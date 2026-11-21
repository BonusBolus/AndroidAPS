package app.aaps.ui.compose.main

/**
 * Whether the loop can give an SMB now, and why (not).
 *
 * @param allowed true when no constraint blocks SMB and the last loop run did not switch it off
 * @param reasons the constraint reasons and the algorithm's own SMB line, in that order
 */
data class SmbStatus(val allowed: Boolean, val reasons: List<String>)

/**
 * Two levels decide if an SMB can be given:
 * - the constraints (SMB preference, open loop, objectives), see `ConstraintsChecker.isSMBModeEnabled`;
 * - the algorithm, which can switch SMB off for one loop run (for example a high temp target, or no
 *   enable-SMB condition true). It writes that as a line starting with "SMB enabled" or "SMB disabled".
 *
 * Display only: this changes nothing about what the loop does.
 *
 * @param algorithmDecision the algorithm's line of the last loop run, null before the first run
 */
internal fun smbStatusOf(constraintAllowed: Boolean, constraintReasons: String, algorithmDecision: String?): SmbStatus {
    val algorithmAllows = algorithmDecision?.startsWith(SMB_DISABLED) != true
    val reasons = constraintReasons.lines().filter { it.isNotBlank() } + listOfNotNull(algorithmDecision)
    return SmbStatus(constraintAllowed && algorithmAllows, reasons)
}

/** The algorithm's SMB decision of one loop run, from its console lines. Null when it wrote none. */
internal fun smbDecisionOf(consoleLines: List<String>?): String? =
    consoleLines?.lastOrNull { it.startsWith(SMB_ENABLED) || it.startsWith(SMB_DISABLED) }

// The texts the determine-basal code writes (OpenAPS SMB and AutoISF). Not translated: algorithm output.
private const val SMB_ENABLED = "SMB enabled"
private const val SMB_DISABLED = "SMB disabled"
