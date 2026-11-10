package ai.promptstudio.tvclean.ui.design.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.roundToInt
import ai.promptstudio.tvclean.ui.design.theme.CytTheme

// look2 (lifted from the photo crop): a percent slider in the brand, in whole steps of `step`
// between `range`'s ends. TalkBack reads `label` and "30 percent" (`valueText`), and a swipe moves
// one step. `onValue` follows the thumb; `onDone` gets the value when the finger lifts, so a
// caller that does real work (a new preview from the bytes) can wait for it.
@Composable
fun CytSlider(
    value: Int,
    onValue: (Int) -> Unit,
    range: IntRange,
    step: Int,
    label: String,
    valueText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDone: (() -> Unit)? = null,
) {
    val c = CytTheme.colors
    val steps = ((range.last - range.first) / step - 1).coerceAtLeast(0)
    Slider(
        value = value.coerceIn(range).toFloat(),
        onValueChange = { v -> onValue((((v - range.first) / step).roundToInt() * step + range.first).coerceIn(range)) },
        modifier = modifier.fillMaxWidth().semantics { contentDescription = label; stateDescription = valueText },
        enabled = enabled,
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = steps,
        onValueChangeFinished = onDone,
        colors = SliderDefaults.colors(
            thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.surface3,
            activeTickColor = c.bg.copy(alpha = 0.5f), inactiveTickColor = c.textTertiary.copy(alpha = 0.5f),
            disabledThumbColor = c.textTertiary, disabledActiveTrackColor = c.textTertiary.copy(alpha = 0.6f),
            disabledInactiveTrackColor = c.surface3,
        ),
    )
}
