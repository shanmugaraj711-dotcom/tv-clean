package ai.promptstudio.tvclean.ui.design.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.max
import ai.promptstudio.tvclean.ui.design.R
import ai.promptstudio.tvclean.ui.design.theme.CytTheme

// look2 (spec 4 and 12.2): the TV preview pinned above the Look sheet's list. On the main page it
// is 184 dp tall at rest and shrinks to 96 dp as the list scrolls (`collapse`, 0 to 1). It never
// takes more than 30% of the window, and with the pinned footer under 40%; from font scale 1.5 it
// starts at the compact size, so the list keeps room. On More options and Advanced it stays compact,
// with a corner button that shows it full size and back.
object PinnedPreviewSize {
    val FULL = 184.dp
    val COMPACT = 96.dp
    const val WINDOW_SHARE = 0.30f
    const val WITH_FOOTER_SHARE = 0.40f
    const val COMPACT_FONT_SCALE = 1.5f

    // The preview's height for a `width` wide area in a `window` tall window, with a pinned footer
    // `footer` tall; `collapse` 0 is at rest, 1 scrolled; `mainPage` false is More options or
    // Advanced (compact unless `big`).
    fun height(width: Dp, window: Dp, footer: Dp, fontScale: Float, collapse: Float, mainPage: Boolean, big: Boolean): Dp {
        val natural = min(width * 9f / 16f, FULL)
        var full = min(natural, window * WINDOW_SHARE)
        val compact = min(COMPACT, full)
        if (fontScale >= COMPACT_FONT_SCALE) full = compact
        if (full + footer > window * WITH_FOOTER_SHARE) full = max(compact, window * WITH_FOOTER_SHARE - footer)
        return when {
            !mainPage -> if (big) natural else compact
            else -> full + (compact - full) * collapse.coerceIn(0f, 1f)
        }
    }
}

@Composable
fun PinnedPreview(
    look: TvLook?,
    description: String,
    modifier: Modifier = Modifier,
    collapse: Float = 0f,
    mainPage: Boolean = true,
    big: Boolean = false,
    footer: Dp = 0.dp,
    onToggleBig: (() -> Unit)? = null,
    toggleLabel: String = "",
) {
    val window = LocalConfiguration.current.screenHeightDp.dp
    val fontScale = LocalDensity.current.fontScale
    val reduced = CytTheme.reducedMotion
    BoxWithConstraints(modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        val target = PinnedPreviewSize.height(maxWidth, window, footer, fontScale, collapse, mainPage, big)
        // A scroll moves it at once (it follows the finger); the size button eases.
        val h by animateDpAsState(target, if (reduced || mainPage) snap() else tween(200), label = "pinnedPreview")
        val w = min(h * 16f / 9f, maxWidth)
        Box(Modifier.width(w)) {
            if (look != null) {
                TvPreview(look, description)
            } else {
                // W13: the preview is not drawn yet (the bytes are still being made).
                Row(
                    Modifier.fillMaxWidth().size(w, w * 9f / 16f).clip(CytTheme.shapes.medium).background(Color.Black).politeLive(),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spinner(color = Color.White)
                    Text(stringResource(R.string.tvclean_look_preview_drawing), Modifier.padding(start = 10.dp), color = Color.White,
                        style = CytTheme.type.bodySmall)
                }
            }
            if (!mainPage && onToggleBig != null) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(48.dp).clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onToggleBig).semantics { contentDescription = toggleLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(32.dp).background(Color.Black.copy(alpha = 0.62f), CircleShape), contentAlignment = Alignment.Center) {
                        CytIcon(if (big) SheetIcons.Smaller else SheetIcons.Bigger, null, tint = Color.White, size = 18.dp)
                    }
                }
            }
        }
    }
}
