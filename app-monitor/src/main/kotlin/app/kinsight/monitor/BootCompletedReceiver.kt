package app.kinsight.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Watchdog leg of DESIGN.md flow 4: donor phones reboot after power loss and
 * only the elder is at home to "fix" it — so monitoring must come back by
 * itself. (On API 31+ the camera FGS start is deferred until the app is next
 * visible; the companion's offline alert covers that gap — Invariant 8.)
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
    }
}
