package tech.inku.callnote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.CallLog
import android.provider.ContactsContract
import android.net.Uri

object Notifier {
    const val CH_PROMPT = "prompt"
    const val CH_REC = "recording"
    const val ID_PROMPT = 1

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROMPT, "Record this call?", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REC, "Recording", NotificationManager.IMPORTANCE_LOW)
        )
    }

    /** Heads-up prompt: shown while ringing and (as a reminder) during the call. */
    fun showPrompt(ctx: Context, number: String?, incoming: Boolean, ringing: Boolean) {
        ensureChannels(ctx)
        val name = number?.let { lookupName(ctx, it) }
        val who = name ?: number ?: "unknown number"
        val title = if (ringing) "Call from $who" else if (incoming) "In call with $who" else "In call"
        val start = Intent(ctx, RecordingService::class.java)
            .setAction(RecordingService.ACTION_START)
            .putExtra(RecordingService.EXTRA_NUMBER, number)
            .putExtra(RecordingService.EXTRA_NAME, name)
            .putExtra(RecordingService.EXTRA_INCOMING, incoming)
        val startPi = PendingIntent.getForegroundService(
            ctx, 10, start, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val skip = Intent(ctx, CallReceiver::class.java).setAction(CallReceiver.ACTION_SKIP)
        val skipPi = PendingIntent.getBroadcast(
            ctx, 11, skip, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, CH_PROMPT)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText("Record this call?")
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(!ringing)
            .addAction(Notification.Action.Builder(0, "Record", startPi).build())
            .addAction(Notification.Action.Builder(0, "No", skipPi).build())
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_PROMPT, n)
    }

    fun cancelPrompt(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancel(ID_PROMPT)
    }

    fun lookupName(ctx: Context, number: String): String? = try {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)
        )
        ctx.contentResolver.query(
            uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (e: Exception) { null }

    /** Most recent call log entry (number, cached name) — used for outgoing calls. */
    fun lastCallLogEntry(ctx: Context): Pair<String?, String?> = try {
        ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME),
            null, null, "${CallLog.Calls.DATE} DESC LIMIT 1"
        )?.use { if (it.moveToFirst()) it.getString(0) to it.getString(1) else null to null }
            ?: (null to null)
    } catch (e: Exception) { null to null }
}
