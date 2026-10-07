package tech.inku.callnote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class CallReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SKIP = "tech.inku.callnote.SKIP"
        private const val PREFS = "call_state"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == ACTION_SKIP) {
            Notifier.cancelPrompt(ctx)
            return
        }
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // Android sends this twice; the first copy often has no number.
                val known = number ?: prefs.getString("number", null)
                prefs.edit().putBoolean("incoming", true).putString("number", known).apply()
                Notifier.showPrompt(ctx, known, incoming = true, ringing = true)
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (RecordingService.running) return
                val incoming = prefs.getBoolean("incoming", false)
                Notifier.showPrompt(ctx, prefs.getString("number", null), incoming, ringing = false)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                prefs.edit().clear().apply()
                Notifier.cancelPrompt(ctx)
            }
        }
    }
}
