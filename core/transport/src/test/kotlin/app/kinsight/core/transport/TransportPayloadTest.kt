package app.kinsight.core.transport

import app.kinsight.core.pairing.HardcodedPairing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.jvmErasure

/** Invariant 1: the transport whitelist is sealed and text-only, forever. */
class TransportPayloadTest {
    private val allowedPayloads = setOf("AlertEvent", "ActivitySummary", "Heartbeat")

    private val allowedFieldTypes: Set<KClass<*>> =
        setOf(String::class, Int::class, Long::class, Boolean::class, Double::class, List::class)

    @Test
    fun `the sealed whitelist is exactly the three text DTOs`() {
        // Arrange / Act
        val subclasses = TransportPayload::class.sealedSubclasses.map { it.simpleName }.toSet()

        // Assert — adding a fourth DTO is a deliberate, reviewed act
        assertEquals(allowedPayloads, subclasses)
    }

    @Test
    fun `no payload carries binary or image-capable fields`() {
        // Arrange
        val subclasses = TransportPayload::class.sealedSubclasses

        // Act / Assert — every property is a scalar/text type; no ByteArray,
        // no Bitmap-like type can ever ride a payload.
        subclasses.forEach { payloadClass ->
            payloadClass.memberProperties.forEach { property ->
                val type = property.returnType.jvmErasure
                assertTrue(type in allowedFieldTypes) {
                    "${payloadClass.simpleName}.${property.name} has banned type $type (Invariant 1)"
                }
            }
        }
    }

    @Test
    fun `heartbeat payload stays near 100 bytes as designed`() {
        // Arrange
        val cipher = EnvelopeCipher(HardcodedPairing.current())
        val heartbeat = Heartbeat("monitor-001", 1_718_000_000_000, "NOMINAL", 80)

        // Act
        val envelope = cipher.seal(heartbeat)

        // Assert — DESIGN.md flow 4: heartbeats are ~100 bytes encrypted
        assertTrue(envelope.wireSizeBytes < 256) { "heartbeat is ${envelope.wireSizeBytes} bytes" }
    }
}
