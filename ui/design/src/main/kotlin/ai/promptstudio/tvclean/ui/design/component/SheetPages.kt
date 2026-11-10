package ai.promptstudio.tvclean.ui.design.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ai.promptstudio.tvclean.ui.design.icon.CytIcons
import ai.promptstudio.tvclean.ui.design.theme.CytTheme

// look2 pages inside one sheet (the Look sheet's Look, More options and Advanced). The header has
// the title (a heading, and the place focus goes to when a page opens, so TalkBack starts there),
// a Back arrow on levels 2 and up ("Back to Look", "Back to More options") and the page's actions
// at the end (More options and Close, or Advanced). System Back and the predictive back gesture
// reach the sheet as a request to hide it (CytBottomSheet's confirm), and the editor answers it by
// going up one page first (LookEditorNav.back), so Back and the arrow do the same thing.
@Composable
fun SheetPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backLabel: String? = null,
    focusKey: Any? = title,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = CytTheme.colors
    val focus = remember { FocusRequester() }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(focusKey) {
        // The first page keeps the sheet's own focus; a page opened later puts it on its title.
        if (first[0]) first[0] = false else runCatching { focus.requestFocus() }
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) CytIconButton(CytIcons.Back, backLabel ?: "", onBack, Modifier.padding(end = 4.dp))
        Text(
            title,
            Modifier.weight(1f).padding(start = if (onBack == null) 4.dp else 0.dp).focusRequester(focus).focusable().semantics { heading() },
            color = c.textPrimary, style = CytTheme.type.titleLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

// The pages themselves: a short slide in the reading direction going deeper, back the other way,
// a plain switch under reduced motion. `depth` orders the pages (Look 0, More options 1, Advanced 2).
@Composable
fun <T> SheetPages(page: T, depth: (T) -> Int, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    val reduced = CytTheme.reducedMotion
    AnimatedContent(
        page, modifier,
        transitionSpec = {
            if (reduced) {
                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            } else {
                val forward = depth(targetState) > depth(initialState)
                val sign = if (forward) 1 else -1
                (slideInHorizontally(tween(220)) { w -> sign * w / 6 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(160)) { w -> -sign * w / 6 } + fadeOut(tween(160)))
            }
        },
        label = "sheetPages",
    ) { p -> content(p) }
}

// The few icons the Look sheet needs beyond CytIcons, in the same outline style.
internal object SheetIcons {
    val Advanced: ImageVector by lazy { icon("Advanced", "M8 7l-5 5 5 5M16 7l5 5-5 5") }
    val Bigger: ImageVector by lazy { icon("Bigger", "M14 4h6v6M10 20H4v-6M20 4l-7 7M4 20l7-7") }
    val Smaller: ImageVector by lazy { icon("Smaller", "M20 4l-6 6M14 5v5h5M4 20l6-6M10 19v-5H5") }

    private fun icon(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        for (d in paths) {
            b.addPath(
                pathData = PathParser().parsePathString(d).toNodes(), fill = null, stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }
}
