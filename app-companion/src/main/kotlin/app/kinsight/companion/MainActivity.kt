package app.kinsight.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.kinsight.companion.ui.Hearthlight
import app.kinsight.companion.ui.bodySize
import app.kinsight.companion.ui.buttonTextSize
import app.kinsight.companion.ui.screenPadding
import app.kinsight.companion.ui.sectionGap
import app.kinsight.companion.ui.titleSize
import app.kinsight.core.pairing.HardcodedPairing
import app.kinsight.watchdog.HeartbeatMonitor

/**
 * Caregiver screen: full-screen alert with one big acknowledge action, an
 * all-quiet state, and the offline-monitor banner (Invariant 8). Ember amber
 * appears ONLY while an alert is active — color is semantic.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CompanionScreen() }
    }
}

@Composable
private fun CompanionScreen() {
    val alertCenter = remember { AlertCenter() }
    var state by remember { mutableStateOf<CompanionUiState>(CompanionUiState.AllQuiet) }
    // M1: heartbeats arrive with the relay at M2; until the first one is
    // seen, HeartbeatMonitor treats the monitor as offline — honest default.
    val lastHeartbeatAtMs: Long? = remember { null }

    when (val current = state) {
        is CompanionUiState.AllQuiet -> {
            AllQuietScreen(
                isMonitorOffline = HeartbeatMonitor.isOfflineAlertDue(lastHeartbeatAtMs, System.currentTimeMillis()),
                onRunDrill = {
                    val nowMs = System.currentTimeMillis()
                    state = alertCenter.onEnvelope(current, DemoAlertFeed.stagedFallEnvelope(nowMs), nowMs)
                },
            )
        }

        is CompanionUiState.AlertActive -> {
            AlertScreen(
                onAcknowledge = {
                    state = alertCenter.acknowledge(current, HardcodedPairing.CAREGIVER_ID)
                },
            )
        }

        is CompanionUiState.AlertAcknowledged -> {
            AcknowledgedScreen(onDone = { state = CompanionUiState.AllQuiet })
        }
    }
}

@Composable
private fun AllQuietScreen(
    isMonitorOffline: Boolean,
    onRunDrill: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Hearthlight.surfaceOat) {
        Column(modifier = Modifier.padding(screenPadding), verticalArrangement = Arrangement.Center) {
            Text(
                text = stringResource(R.string.all_quiet_title),
                color = Hearthlight.brandPine,
                fontSize = titleSize,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(sectionGap))
            Text(
                text = stringResource(R.string.all_quiet_body),
                color = Hearthlight.brandPine,
                fontSize = bodySize,
            )
            if (isMonitorOffline) {
                Spacer(Modifier.height(sectionGap))
                Text(
                    text = stringResource(R.string.monitor_offline_banner),
                    color = Hearthlight.alertEmber,
                    fontSize = bodySize,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(sectionGap))
            BigButton(label = stringResource(R.string.run_alert_drill), onClick = onRunDrill)
        }
    }
}

@Composable
private fun AlertScreen(onAcknowledge: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = Hearthlight.alertEmber) {
        Column(modifier = Modifier.padding(screenPadding), verticalArrangement = Arrangement.Center) {
            Text(
                text = stringResource(R.string.alert_title),
                color = Color.White,
                fontSize = titleSize,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(sectionGap))
            Text(
                text = stringResource(R.string.alert_body),
                color = Color.White,
                fontSize = bodySize,
            )
            Spacer(Modifier.height(sectionGap))
            BigButton(label = stringResource(R.string.acknowledge), onClick = onAcknowledge)
        }
    }
}

@Composable
private fun AcknowledgedScreen(onDone: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = Hearthlight.surfaceOat) {
        Column(modifier = Modifier.padding(screenPadding), verticalArrangement = Arrangement.Center) {
            Text(
                text = stringResource(R.string.acknowledged_title),
                color = Hearthlight.brandPine,
                fontSize = titleSize,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(sectionGap))
            Text(
                text = stringResource(R.string.acknowledged_body),
                color = Hearthlight.brandPine,
                fontSize = bodySize,
            )
            Spacer(Modifier.height(sectionGap))
            BigButton(label = stringResource(R.string.all_quiet_title), onClick = onDone)
        }
    }
}

@Composable
private fun BigButton(
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Hearthlight.brandPine),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Hearthlight.minTouchTarget),
    ) {
        Text(text = label, fontSize = buttonTextSize)
    }
}
