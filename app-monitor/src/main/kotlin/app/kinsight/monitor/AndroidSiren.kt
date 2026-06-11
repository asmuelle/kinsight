package app.kinsight.monitor

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.kinsight.core.alerts.Siren

/**
 * Invariant 5 — local-first: the siren is a plain alarm-stream tone that
 * needs no network and works in airplane mode. It must NEVER throw into the
 * alert path; any audio failure is logged (no PII) and swallowed so the
 * pipeline continues to the queue.
 */
internal class AndroidSiren : Siren {
    override fun fire() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
            tone.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, SIREN_DURATION_MS)
            Handler(Looper.getMainLooper()).postDelayed(tone::release, RELEASE_AFTER_MS)
        }.onFailure { failure ->
            Log.w(TAG, "Siren could not start; alert path continues", failure)
        }
    }

    private companion object {
        const val TAG = "AndroidSiren"
        const val SIREN_DURATION_MS = 10_000
        const val RELEASE_AFTER_MS = SIREN_DURATION_MS + 1_000L
    }
}
