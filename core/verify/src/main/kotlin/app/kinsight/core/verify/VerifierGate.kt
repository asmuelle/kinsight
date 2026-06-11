package app.kinsight.core.verify

/** Device facts the gate decides on, captured once at service start. */
data class DeviceCapabilities(
    val totalRamBytes: Long,
) {
    val isVerifierEligible: Boolean get() = totalRamBytes >= VerifierGate.MIN_VERIFIER_RAM_BYTES
}

/**
 * Invariant 4: the verifier loads only on devices reporting >= 4 GB RAM,
 * decided ONCE at service start. Sub-4GB devices get the deterministic path
 * (null verifier); there is no lazy mid-session model load — the loader runs
 * eagerly in the constructor or never.
 */
class VerifierGate(
    capabilities: DeviceCapabilities,
    loader: () -> FallVerifier,
) {
    val verifier: FallVerifier? = if (capabilities.isVerifierEligible) loader() else null

    companion object {
        const val MIN_VERIFIER_RAM_BYTES: Long = 4L * 1024 * 1024 * 1024
    }
}
