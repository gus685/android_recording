package tech.inku.callnote

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Intentionally empty. Android keeps microphone capture alive during a call for
 * apps that run an enabled accessibility service; ordinary apps get silence.
 */
class CallAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
