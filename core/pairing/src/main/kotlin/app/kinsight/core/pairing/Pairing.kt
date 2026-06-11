package app.kinsight.core.pairing

import java.security.MessageDigest

/**
 * A monitor-caregiver pairing. At M2 the shared secret comes from QR-mediated
 * X25519 exchange and lives in the Android Keystore; the raw bytes here exist
 * for the JVM-testable envelope cipher only.
 */
class PairingKey(
    val monitorDeviceId: String,
    val caregiverId: String,
    val sharedSecret: ByteArray,
) {
    init {
        require(monitorDeviceId.isNotBlank()) { "monitorDeviceId must not be blank" }
        require(caregiverId.isNotBlank()) { "caregiverId must not be blank" }
        require(sharedSecret.size == SECRET_LENGTH_BYTES) {
            "sharedSecret must be $SECRET_LENGTH_BYTES bytes, got ${sharedSecret.size}"
        }
    }

    companion object {
        const val SECRET_LENGTH_BYTES: Int = 32
    }
}

/** Boundary for obtaining the active pairing. */
fun interface PairingSource {
    fun current(): PairingKey
}

/**
 * M1-only single hardcoded pairing (DESIGN.md milestone M1: "single
 * hardcoded pairing"). This is a DEV PLACEHOLDER, not a secret: the value is
 * a digest of a public constant, used so the encrypted-envelope path is real
 * and testable end to end before QR pairing lands at M2. It must never ship
 * past M1.
 */
object HardcodedPairing : PairingSource {
    const val MONITOR_DEVICE_ID: String = "monitor-001"
    const val CAREGIVER_ID: String = "caregiver-001"

    private const val DEV_SEED: String = "kinsight-m1-dev-pairing-placeholder"

    override fun current(): PairingKey =
        PairingKey(
            monitorDeviceId = MONITOR_DEVICE_ID,
            caregiverId = CAREGIVER_ID,
            sharedSecret = MessageDigest.getInstance("SHA-256").digest(DEV_SEED.toByteArray()),
        )
}
