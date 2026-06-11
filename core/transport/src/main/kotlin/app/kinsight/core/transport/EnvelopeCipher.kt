package app.kinsight.core.transport

import app.kinsight.core.pairing.PairingKey
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Thrown when a payload would exceed the FCM/Invariant-1 wire budget. */
class OversizedPayloadException(
    actualBytes: Int,
) : IllegalArgumentException(
        "Envelope of $actualBytes bytes exceeds the ${EnvelopeCipher.MAX_WIRE_BYTES}-byte budget (Invariant 1)",
    )

/** Ciphertext-only wire form: the relay never sees anything else. */
class SealedEnvelope(
    val nonce: ByteArray,
    val ciphertext: ByteArray,
) {
    val wireSizeBytes: Int get() = nonce.size + ciphertext.size
}

/**
 * Invariant 7 — E2E-encrypted metadata: every payload is AES-256-GCM sealed
 * under the pairing secret before it touches any transport. The relay (and
 * FCM) carry ciphertext only.
 */
class EnvelopeCipher(
    private val pairingKey: PairingKey,
) {
    private val json = Json { classDiscriminator = TYPE_DISCRIMINATOR }
    private val random = SecureRandom()

    fun seal(payload: TransportPayload): SealedEnvelope {
        val plaintext = json.encodeToString(TransportPayload.serializer(), payload).toByteArray()
        val nonce = ByteArray(NONCE_LENGTH_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, nonce))
        val envelope = SealedEnvelope(nonce = nonce, ciphertext = cipher.doFinal(plaintext))
        if (envelope.wireSizeBytes > MAX_WIRE_BYTES) throw OversizedPayloadException(envelope.wireSizeBytes)
        return envelope
    }

    fun open(envelope: SealedEnvelope): TransportPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, envelope.nonce))
        val plaintext = cipher.doFinal(envelope.ciphertext)
        return json.decodeFromString(TransportPayload.serializer(), String(plaintext))
    }

    private fun secretKey() = SecretKeySpec(pairingKey.sharedSecret, KEY_ALGORITHM)

    companion object {
        /** FCM's payload ceiling doubles as our Invariant 1 budget. */
        const val MAX_WIRE_BYTES: Int = 4096

        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"
        private const val NONCE_LENGTH_BYTES = 12
        private const val TAG_LENGTH_BITS = 128
        private const val TYPE_DISCRIMINATOR = "kind"
    }
}
