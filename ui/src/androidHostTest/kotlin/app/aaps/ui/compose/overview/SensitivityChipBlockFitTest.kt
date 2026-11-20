package app.aaps.ui.compose.overview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.overview.IsfStep
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.ui.compose.overview.chips.AutosensChip
import app.aaps.ui.compose.overview.chips.SensitivityChip
import app.aaps.ui.compose.overview.chips.SensitivityUiState
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Checks that the autosens and ISF chips fit in the overview row beside the BG circle.
 *
 * The widths are the room the row gets on a phone: about 261 dp at the default display size of a
 * 411 dp wide phone, and about 210 dp at one step larger display size (width minus 16 dp padding,
 * the 126 dp BG circle and the 8 dp gap). The font scales are the default and 130 %.
 *
 * The autosens value and both ISF values must never be cut: the second ISF is the one the loop works
 * with. The wizard ISF and the second autosens value may be cut when there is no room, so they are
 * only reported.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h4000dp")
class SensitivityChipBlockFitTest {

    @get:Rule
    val compose = createComposeRule()

    private class Case(val name: String, val state: SensitivityUiState, val required: List<String>, val optional: List<String>)

    private val cases = listOf(
        Case(
            "mmol normal",
            SensitivityUiState(asText = "97", isfFrom = "5.5", isfTo = "8.1", isfStep = IsfStep.UP, wizardIsf = "6.4", ratio = 0.97, hasData = true),
            required = listOf("97", "5.5", "8.1"), optional = listOf("6.4")
        ),
        Case(
            "mg/dL normal",
            SensitivityUiState(asText = "97", isfFrom = "100", isfTo = "145", isfStep = IsfStep.UP, wizardIsf = "115", ratio = 0.97, hasData = true),
            required = listOf("97", "100", "145"), optional = listOf("115")
        ),
        Case(
            "mmol worst",
            SensitivityUiState(
                asText = "105", algorithmAutosens = "112", isfFrom = "10.2", isfTo = "12.5", isfStep = IsfStep.UP, wizardIsf = "11.8", ratio = 1.05,
                hasData = true
            ),
            required = listOf("105", "10.2", "12.5"), optional = listOf("112", "11.8")
        ),
        Case(
            "mg/dL worst",
            SensitivityUiState(
                asText = "105", algorithmAutosens = "112", isfFrom = "180", isfTo = "225", isfStep = IsfStep.UP, wizardIsf = "199", ratio = 1.05,
                hasData = true
            ),
            required = listOf("105", "180", "225"), optional = listOf("112", "199")
        )
    )
    private val widthsDp = listOf(261, 210)
    private val fontScales = listOf(1.0f, 1.3f)

    @Test
    fun autosensAndIsfValuesFitBesideTheBgCircle() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    cases.forEachIndexed { c, case ->
                        fontScales.forEach { scale ->
                            val density = LocalDensity.current
                            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                                // What the row needs: both chips at their own width, with the gap between them.
                                Row(Modifier.testTag("need-$c-$scale")) {
                                    AutosensChip(state = case.state, onClick = {})
                                    Box(Modifier.width(AapsSpacing.small))
                                    SensitivityChip(state = case.state, onClick = {})
                                }
                                widthsDp.forEach { width ->
                                    Box(Modifier.width(width.dp).testTag("fit-$c-$scale-$width")) {
                                        SensitivityChipBlock(state = case.state, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val report = StringBuilder()
        val problems = mutableListOf<String>()
        cases.forEachIndexed { c, case ->
            fontScales.forEach { scale ->
                val density = compose.density.density
                val needDp = compose.onNode(hasTestTag("need-$c-$scale"), useUnmergedTree = true).fetchSemanticsNode().size.width / density
                widthsDp.forEach { width ->
                    val cutRequired = case.required.filter { isCut(it, "fit-$c-$scale-$width", "need-$c-$scale") }
                    val cutOptional = case.optional.filter { isCut(it, "fit-$c-$scale-$width", "need-$c-$scale") }
                    report.append(
                        "%-12s font %.1f  width %3d dp  needs %5.1f dp  cut: %s%n".format(
                            case.name, scale, width, needDp,
                            (cutRequired + cutOptional).ifEmpty { listOf("nothing") }.joinToString()
                        )
                    )
                    if (cutRequired.isNotEmpty()) problems += "${case.name}, font $scale, $width dp: $cutRequired"
                }
            }
        }
        println(report)
        if (problems.isNotEmpty()) fail("Required values are cut:\n${problems.joinToString("\n")}\n\n$report")
    }

    /**
     * True when the text is not shown in full in [inTag]: it is narrower there than in [needTag], where it has all
     * the room it wants, or it ends with an ellipsis.
     */
    private fun isCut(text: String, inTag: String, needTag: String): Boolean {
        val node = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(inTag)), useUnmergedTree = true).fetchSemanticsNode()
        val full = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(needTag)), useUnmergedTree = true).fetchSemanticsNode()
        if (node.size.width < full.size.width) return true
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        val layout = results.firstOrNull() ?: return false
        return (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
    }
}
