package tech.inku.callnote

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView

class MainActivity : Activity() {
    private val perms = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.POST_NOTIFICATIONS,
    )
    private val sources = listOf("voice_recognition", "mic", "unprocessed", "communication")
    private lateinit var status: TextView
    private lateinit var list: ListView
    private val items = mutableListOf<Pair<String, Uri>>()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        status = TextView(this)
        val grant = Button(this).apply {
            text = "Grant permissions"
            setOnClickListener { requestPermissions(perms, 1) }
        }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, sources)
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            setSelection(sources.indexOf(prefs.getString("source", "voice_recognition")).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    prefs.edit().putString("source", sources[pos]).apply()
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        list = ListView(this)
        list.setOnItemClickListener { _, _, pos, _ ->
            val send = Intent(Intent.ACTION_SEND).setType("audio/mp4")
                .putExtra(Intent.EXTRA_STREAM, items[pos].second)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(send, "Send recording"))
        }
        root.addView(status); root.addView(grant)
        root.addView(TextView(this).apply { text = "Audio source (try another if a recording is silent):" })
        root.addView(spinner)
        root.addView(TextView(this).apply { text = "Recordings (tap to share):" })
        root.addView(list)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val missing = perms.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        status.text = if (missing.isEmpty()) "Ready. You'll be asked when a call comes in."
        else "Missing permissions: " + missing.joinToString { it.substringAfterLast('.') }
        loadRecordings()
    }

    private fun loadRecordings() {
        items.clear()
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME),
            "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?", arrayOf("${RecordingService.REL_PATH}%"),
            "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        )?.use {
            while (it.moveToNext()) {
                items += it.getString(1) to Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it.getLong(0).toString()
                )
            }
        }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items.map { it.first })
    }
}
