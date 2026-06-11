package app.kinsight.monitor

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import app.kinsight.core.alerts.AlertPipeline
import app.kinsight.core.alerts.Connectivity
import app.kinsight.core.alerts.OutboundAlertQueue
import app.kinsight.core.capture.LumaMotionGate
import app.kinsight.core.classify.HeuristicFallClassifier
import app.kinsight.core.events.InMemoryEventRepository
import app.kinsight.core.pairing.HardcodedPairing
import app.kinsight.core.pose.PoseLandmarker
import app.kinsight.core.pose.PoseWindow
import app.kinsight.core.transport.EnvelopeCipher
import app.kinsight.core.transport.RelayClient
import app.kinsight.core.transport.SendResult
import app.kinsight.core.verify.DeviceCapabilities
import app.kinsight.core.verify.FallVerifier
import app.kinsight.core.verify.VerificationCoordinator
import app.kinsight.core.verify.VerifierGate
import app.kinsight.core.verify.VerifierVerdict
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Foreground camera service (FOREGROUND_SERVICE_TYPE_CAMERA): runs the
 * capture -> infer -> store -> surface pipeline for as long as the donor
 * phone is monitoring. M1 wiring notes:
 * - Pose detection is a stub until the MediaPipe `.task` asset is fetched at
 *   setup (TOOLS.md); the surrounding pipeline is fully wired and tested.
 * - The verifier is a fail-open stub behind the real RAM gate (Invariant 4);
 *   Gemma 3n arrives at M2.
 * - There is no network path in this app (Invariant 1): sealed alert
 *   envelopes queue until the transport relay client lands at M2.
 */
class MonitorService : LifecycleService() {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var pipeline: MonitorPipeline

    override fun onCreate() {
        super.onCreate()
        pipeline = buildPipeline()
        startInForeground()
        bindCamera()
    }

    override fun onDestroy() {
        analysisExecutor.shutdown()
        super.onDestroy()
    }

    private fun buildPipeline(): MonitorPipeline {
        val pairingKey = HardcodedPairing.current()
        val verifierGate =
            VerifierGate(DeviceCapabilities(totalRamBytes = totalRamBytes())) {
                // M2 loads Gemma 3n here; the M1 stub always fails open.
                FallVerifier { VerifierVerdict.UNAVAILABLE }
            }
        return MonitorPipeline(
            motionGate = LumaMotionGate(),
            poseLandmarker = pendingPoseLandmarker(),
            poseWindow = PoseWindow(),
            classifier = HeuristicFallClassifier(),
            verification = VerificationCoordinator(verifierGate.verifier),
            alertPipeline =
                AlertPipeline(
                    repository = InMemoryEventRepository(),
                    siren = AndroidSiren(),
                    cipher = EnvelopeCipher(pairingKey),
                    queue = OutboundAlertQueue(),
                    relay = RelayClient { SendResult.Failed("relay client lands at M2; envelope stays queued") },
                    connectivity = Connectivity { false },
                ),
            monitorDeviceId = pairingKey.monitorDeviceId,
        )
    }

    /**
     * Pose Landmarker boundary: returns no pose until the LiteRT model asset
     * is provisioned on-device (M1 device-lab work, TOOLS.md). Returning null
     * keeps the window empty — the classifier simply never sees a person.
     */
    private fun pendingPoseLandmarker(): PoseLandmarker = PoseLandmarker { null }

    private fun totalRamBytes(): Long {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return info.totalMem
    }

    private fun startInForeground() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notification =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_body))
                .setOngoing(true)
                .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
        )
    }

    private fun bindCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            runCatching { bindAnalysis(providerFuture.get()) }
                .onFailure { failure ->
                    // No PII in logs (Invariant 7); the watchdog restarts us.
                    Log.w(TAG, "Camera bind failed; stopping for watchdog restart", failure)
                    stopSelf()
                }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindAnalysis(provider: ProcessCameraProvider) {
        val analysis =
            ImageAnalysis
                .Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
        analysis.setAnalyzer(analysisExecutor, LumaImageAnalyzer { frame -> pipeline.onFrame(frame) })
        provider.unbindAll()
        provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, analysis)
    }

    private companion object {
        const val TAG = "MonitorService"
        const val CHANNEL_ID = "kinsight.monitoring"
        const val NOTIFICATION_ID = 1001
    }
}
