package ai.promptstudio.tvclean.ui.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import ai.promptstudio.tvclean.ui.design.theme.CytMotion
import ai.promptstudio.tvclean.ui.design.theme.CytTheme
import ai.promptstudio.tvclean.ui.design.theme.glass

// One choice of a SegmentedChoice: a key, the short label the segment shows ("5") and what
// TalkBack reads for it ("5 apps in a row").
@Immutable
data class SegmentOption(val key: String, val label: String, val contentDescription: String)

// The highlight's test tag (MotionAndBehaviourTest follows it as it slides).
internal const val SEGMENT_HIGHLIGHT_TAG = "tvclean_segment_highlight"

// 0.4.0: a few short choices side by side in one pill track (Apps in a row, 4 to 8; later the
// See-through amount). Equal segments, each at least 48 dp high, in a selectableGroup, each a radio
// button with its own description. One glass highlight sits behind the chosen segment and slides
// from the old choice to the new one on a short spring (about 300 ms, a small overshoot); under the
// system's reduced motion it moves at once. No tick: the highlight and the selected text in the
// primary colour show the choice, and TalkBack says "selected". The track mirrors in RTL (the first
// option at the start edge), as every phone control does. A tap only chooses.
// look2: `enabled` false keeps the value the TV will use selected but greyed, and each segment
// reads `disabledReason` as its state (See-through amount without a see-through band).
@Composable
fun SegmentedChoice(
    options: List<SegmentOption>,
    selectedKey: String?,
    onSelect: (SegmentOption) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledReason: String? = null,
    groupLabel: String? = null,
) {
    val c = CytTheme.colors
    val reduced = CytTheme.reducedMotion
    val selected = options.indexOfFirst { it.key == selectedKey }
    // Each segment's left edge and width in the track, in px (absolute, so the same in RTL).
    val bounds = remember(options.size) { mutableStateListOf<Pair<Float, Float>?>().apply { repeat(options.size) { add(null) } } }
    var height by remember { mutableStateOf(0) }
    val x = remember { Animatable(0f) }
    val width = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    val target = bounds.getOrNull(selected)
    LaunchedEffect(selected, target, reduced) {
        val t = target ?: return@LaunchedEffect
        if (!placed || reduced) {
            x.snapTo(t.first)
            width.snapTo(t.second)
            placed = true
        } else {
            coroutineScope {
                launch { x.animateTo(t.first, CytMotion.segment) }
                launch { width.animateTo(t.second, CytMotion.segment) }
            }
        }
    }
    val density = LocalDensity.current
    val track = CytTheme.shapes.pill
    Box(modifier.fillMaxWidth().background(c.surface1, track).border(1.dp, c.outline, track).padding(4.dp)) {
        if (placed && selected >= 0 && height > 0) {
            Box(
                Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset { IntOffset(x.value.roundToInt(), 0) }
                    .size(with(density) { width.value.toDp() }, with(density) { height.toDp() })
                    .alpha(if (enabled) 1f else 0.5f).glass(track, CytTheme.highlight).testTag(SEGMENT_HIGHLIGHT_TAG),
            )
        }
        Row(Modifier.fillMaxWidth().selectableGroup().then(if (groupLabel != null) Modifier.semantics { contentDescription = groupLabel } else Modifier),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEachIndexed { i, o ->
                val on = i == selected
                val ink by animateColorAsState(
                    when { on && enabled -> c.primary; else -> c.textSecondary }, tween(if (reduced) 0 else 200), label = "segment",
                )
                Box(
                    Modifier.weight(1f).heightIn(min = 48.dp).clip(track)
                        .onPlaced { bounds[i] = it.positionInParent().x to it.size.width.toFloat(); height = it.size.height }
                        .selectable(on, enabled = enabled, role = Role.RadioButton) { onSelect(o) }
                        .semantics {
                            contentDescription = o.contentDescription
                            if (!enabled && disabledReason != null) stateDescription = disabledReason
                        }
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(o.label, Modifier.clearAndSetSemantics {}, color = ink, style = CytTheme.type.titleMedium, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
    }
}
