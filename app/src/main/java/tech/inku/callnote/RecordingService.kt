package tech.inku.callnote

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingService : Service() {
    companion object {
        const val ACTION_START = "tech.inku.callnote.START"
        const val EXTRA_NUMBER = "number"
        const val EXTRA_NAME = "name"
        const val EXTRA_INCOMING = "incoming"
        const val REL_PATH = "Recordings/CallNote"
        @Volatile var running = false
    }

    private var recorder: MediaRecorder? = null
    private var pfd: ParcelFileDescriptor? = null
    private var uri: Uri? = null
    private var number: String? = null
    private var name: String? = null
    private var incoming = false
    private var startedAt = 0L
    private var callback: TelephonyCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START || running) return START_NOT_STICKY
        number = intent.getStringExtra(EXTRA_NUMBER)
        name = intent.getStringExtra(EXTRA_NAME)
        incoming = intent.getBooleanExtra(EXTRA_INCOMING, false)

        Notifier.ensureChannels(this)
        Notifier.cancelPrompt(this)
        val n = Notification.Builder(this, Notifier.CH_REC)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Recording call")
            .setContentText(name ?: number ?: "unknown number")
            .setOngoing(true)
            .build()
        startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)

        if (!startRecorder()) { stopSelf(); return START_NOT_STICKY }
        running = true
        watchForCallEnd()
        return START_NOT_STICKY
    }

    private fun audioSource(): Int = when (
        getSharedPreferences("settings", MODE_PRIVATE).getString("source", "voice_recognition")
    ) {
        "mic" -> MediaRecorder.AudioSource.MIC
        "unprocessed" -> MediaRecorder.AudioSource.UNPROCESSED
        "communication" -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
        else -> MediaRecorder.AudioSource.VOICE_RECOGNITION
    }

    private fun startRecorder(): Boolean = try {
        startedAt = System.currentTimeMillis()
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(startedAt))
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "${stamp}_recording.m4a")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, REL_PATH)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        pfd = contentResolver.openFileDescriptor(uri!!, "w")
        recorder = MediaRecorder(this).apply {
            setAudioSource(audioSource())
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(96_000)
            setAudioSamplingRate(44_100)
            setOutputFile(pfd!!.fileDescriptor)
            prepare()
            start()
        }
        true
    } catch (e: Exception) {
        uri?.let { contentResolver.delete(it, null, null) }
        false
    }

    private fun watchForCallEnd() {
        val tm = getSystemService(TelephonyManager::class.java)
        val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                if (state == TelephonyManager.CALL_STATE_IDLE) finish()
            }
        }
        callback = cb
        tm.registerTelephonyCallback(mainExecutor, cb)
    }

    private fun finish() {
        callback?.let { getSystemService(TelephonyManager::class.java).unregisterTelephonyCallback(it) }
        callback = null
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release(); recorder = null
        pfd?.close(); pfd = null
        running = false
        // The call-log row is written a moment after the call ends.
        Handler(Looper.getMainLooper()).postDelayed({ finalizeFile(); stopSelf() }, 1500)
    }

    private fun finalizeFile() {
        val u = uri ?: return
        if (number == null || name == null) {
            val (n, cached) = Notifier.lastCallLogEntry(this)
            if (number == null) number = n
            if (name == null) name = cached ?: number?.let { Notifier.lookupName(this, it) }
        }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date(startedAt))
        val safe = listOfNotNull(number, name).joinToString("_")
            .replace(Regex("[^A-Za-z0-9+_-]"), "")
        val display = listOf(stamp, if (incoming) "in" else "out", safe)
            .filter { it.isNotEmpty() }.joinToString("_") + ".m4a"
        val v = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, display)
            put(MediaStore.Audio.Media.TITLE, "Call ${name ?: ""} ${number ?: ""}".trim())
            put(MediaStore.Audio.Media.IS_PENDING, 0)
        }
        contentResolver.update(u, v, null, null)

        val share = Intent(Intent.ACTION_SEND).setType("audio/mp4")
            .putExtra(Intent.EXTRA_STREAM, u)
            .putExtra(Intent.EXTRA_TEXT, "Call ${name ?: ""} ${number ?: ""}".trim())
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val pi = PendingIntent.getActivity(
            this, 20, Intent.createChooser(share, "Send recording"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, Notifier.CH_PROMPT)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Recording saved")
            .setContentText(display)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(3, n)
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }
}
