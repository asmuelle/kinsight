package app.kinsight.monitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import app.kinsight.monitor.ui.Hearthlight
import app.kinsight.monitor.ui.bodySize
import app.kinsight.monitor.ui.headlineSize
import app.kinsight.monitor.ui.screenPadding
import app.kinsight.monitor.ui.sectionGap

/** Donor-phone home screen: one job — start monitoring, state the promise. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MonitorScreen(onStartMonitoring = ::startMonitorService)
        }
    }

    private fun startMonitorService() {
        ContextCompat.startForegroundService(this, Intent(this, MonitorService::class.java))
    }
}

@Composable
private fun MonitorScreen(onStartMonitoring: () -> Unit) {
    val context = LocalContext.current
    var isMonitoring by remember { mutableStateOf(false) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                onStartMonitoring()
                isMonitoring = true
            }
        }

    Surface(modifier = Modifier.fillMaxSize(), color = Hearthlight.surfaceOat) {
        Column(
            modifier = Modifier.padding(screenPadding),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.headline),
                color = Hearthlight.brandPine,
                fontSize = headlineSize,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(sectionGap))
            Text(
                text = stringResource(R.string.privacy_promise),
                color = Hearthlight.brandPine,
                fontSize = bodySize,
            )
            Spacer(Modifier.height(sectionGap))
            Text(
                text = stringResource(R.string.scope_statement),
                color = Hearthlight.brandPine,
                fontSize = bodySize,
            )
            Spacer(Modifier.height(sectionGap))
            StartMonitoringButton(isMonitoring = isMonitoring) {
                val hasCamera =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                if (hasCamera) {
                    onStartMonitoring()
                    isMonitoring = true
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        }
    }
}

@Composable
private fun StartMonitoringButton(
    isMonitoring: Boolean,
    onClick: () -> Unit,
) {
    if (isMonitoring) {
        Text(
            text = stringResource(R.string.monitoring_active),
            color = Hearthlight.brandPine,
            fontSize = bodySize,
            fontWeight = FontWeight.Medium,
        )
        return
    }
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Hearthlight.brandPine),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Hearthlight.minTouchTarget),
    ) {
        Text(text = stringResource(R.string.start_monitoring), fontSize = bodySize)
    }
}
