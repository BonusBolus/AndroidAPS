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
 * @param describeHighTempTarget text for the high temp target line, from its raw mg/dL value. The algorithm
 *   writes that value unrounded and always in mg/dL. Any other line is shown as it is.
 */
internal fun smbStatusOf(
    constraintAllowed: Boolean,
    constraintReasons: String,
    algorithmDecision: String?,
    describeHighTempTarget: (mgdl: Double) -> String
): SmbStatus {
    val algorithmAllows = algorithmDecision?.startsWith(SMB_DISABLED) != true
    val decisionText = algorithmDecision?.let { line -> highTempTargetMgdlOf(line)?.let(describeHighTempTarget) ?: line }
    val reasons = constraintReasons.lines().filter { it.isNotBlank() } + listOfNotNull(decisionText)
    return SmbStatus(constraintAllowed && algorithmAllows, reasons)
}

/** The mg/dL value of the algorithm's high temp target line, or null for any other line or a value that does not parse. */
internal fun highTempTargetMgdlOf(line: String): Double? =
    HIGH_TEMP_TARGET.matchEntire(line)?.groupValues?.get(1)?.toDoubleOrNull()

/** The algorithm's SMB decision of one loop run, from its console lines. Null when it wrote none. */
internal fun smbDecisionOf(consoleLines: List<String>?): String? =
    consoleLines?.lastOrNull { it.startsWith(SMB_ENABLED) || it.startsWith(SMB_DISABLED) }

// The texts the determine-basal code writes (OpenAPS SMB and AutoISF). Not translated: algorithm output.
private const val SMB_ENABLED = "SMB enabled"
private const val SMB_DISABLED = "SMB disabled"
private val HIGH_TEMP_TARGET = Regex("""^SMB disabled due to high temptarget of (\S+)$""")
