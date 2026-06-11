package app.kinsight.core.transport

import kotlinx.serialization.Serializable

/**
 * Invariant 1 — the ONLY things that may ever leave the donor phone.
 *
 * This interface is sealed so the whitelist is compile-time: transport cannot
 * be handed anything but these three text-only DTOs. None of them has (or may
 * ever gain) a binary/pixel-capable field; RepoInvariantsTest and
 * TransportPayloadTest enforce that in CI forever.
 */
@Serializable
sealed interface TransportPayload {
    val monitorDeviceId: String
}

/** A delivered alert: type + severity + when. No pixels, no health prose. */
@Serializable
data class AlertEvent(
    override val monitorDeviceId: String,
    val alertId: String,
    val eventType: String,
    val severity: String,
    val occurredAtMs: Long,
) : TransportPayload

/** Daily activity roll-up feeding the caregiver digest. */
@Serializable
data class ActivitySummary(
    override val monitorDeviceId: String,
    val dateIso: String,
    val motionMinutes: Int,
    val zonesActive: List<String>,
) : TransportPayload

/** 5-minute liveness ping (~100 bytes; silence is a signal — Invariant 8). */
@Serializable
data class Heartbeat(
    override val monitorDeviceId: String,
    val sentAtMs: Long,
    val thermalState: String,
    val batteryPercent: Int,
) : TransportPayload
