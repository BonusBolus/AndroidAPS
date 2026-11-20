package app.aaps.ui.compose.overview.chips

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.overview.IsfStep
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.ui.UiStringIds
import app.aaps.ui.compose.main.TempTargetChipState
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.ui.R as UiR

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TempTargetChipTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsTargetText_active() {
        compose.setContent {
            MaterialTheme {
                TempTargetChip(
                    targetText = "5.5 - 5.5 (30 min)",
                    state = TempTargetChipState.Active,
                    progress = 0.5f,
                    reason = TT.Reason.EATING_SOON,
                    onClick = {}
                )
            }
        }

        compose.onNodeWithText("5.5 - 5.5 (30 min)").assertIsDisplayed()
    }

    @Test
    fun adjustedTargetShowsProfileAndAlgorithmTargetWhenTheyDiffer() {
        compose.setContent {
            MaterialTheme {
                TempTargetChip(
                    targetText = "6.1",
                    profileTargetText = "5.5",
                    tempTargetStep = IsfStep.UP,
                    state = TempTargetChipState.Adjusted,
                    progress = 0f,
                    reason = null,
                    onClick = {}
                )
            }
        }

        compose.onNodeWithText("5.5").assertIsDisplayed()
        compose.onNodeWithText("6.1").assertIsDisplayed()
    }

    @Test
    fun adjustedTargetShowsOneValueWhenItIsTheSame() {
        compose.setContent {
            MaterialTheme {
                TempTargetChip(
                    targetText = "5.5",
                    profileTargetText = "5.5",
                    state = TempTargetChipState.Adjusted,
                    progress = 0f,
                    reason = null,
                    onClick = {}
                )
            }
        }

        // One number, not "5.5 -> 5.5": the step is only shown when the algorithm really moved the target.
        compose.onAllNodesWithText("5.5").assertCountEquals(1)
    }

    @Test
    fun showsTargetText_none_withNullReason() {
        compose.setContent {
            MaterialTheme {
                TempTargetChip(
                    targetText = "5.0 - 7.0",
                    state = TempTargetChipState.None,
                    progress = 0f,
                    reason = null,
                    onClick = {}
                )
            }
        }

        compose.onNodeWithText("5.0 - 7.0").assertIsDisplayed()
    }

    @Test
    fun click_firesCallback() {
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                TempTargetChip(
                    targetText = "5.0 - 7.0",
                    state = TempTargetChipState.None,
                    progress = 0f,
                    reason = null,
                    onClick = { clicks++ },
                    modifier = Modifier.testTag("ttChip")
                )
            }
        }

        compose.onNodeWithTag("ttChip").performClick()

        assertThat(clicks).isEqualTo(1)
    }

    /** With no temp target the chip shows the profile target; it must not read as a running one. */
    @Test
    fun noTempTargetIsSaidAsSuch() {
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
        val expected = RuntimeEnvironment.getApplication().getString(UiR.string.a11y_tt_none)
        compose.setContent {
            MaterialTheme {
                TempTargetChip(
                    targetText = "5.0 - 7.0",
                    state = TempTargetChipState.None,
                    progress = 0f,
                    reason = null,
                    onClick = {},
                    modifier = Modifier.testTag("ttChip")
                )
            }
        }

        compose.onNodeWithTag("ttChip").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected))
    }
}
