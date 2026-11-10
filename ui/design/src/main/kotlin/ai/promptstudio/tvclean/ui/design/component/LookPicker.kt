package ai.promptstudio.tvclean.ui.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.promptstudio.tvclean.ui.design.icon.CytIcons
import ai.promptstudio.tvclean.ui.design.theme.CytTheme

// 0.4.0: one picture of the Look sheet's Picture row: a key, the label under it, and the
// thumbnail (the picture itself, or the plain colour the TV shows without one).
// look2: `sub`, a second line under the name ("Until 10 Nov", read by TalkBack too); `action`, a
// tile that opens something instead of choosing ("Choose a photo": a button with a plus, read as
// `actionDescription`, never selected).
@Immutable
data class PictureChoice(
    val key: String,
    val label: String,
    val image: ImageBitmap?,
    val color: Color = Color.Black,
    val sub: String? = null,
    val action: Boolean = false,
    val actionDescription: String? = null,
)

// The Picture row (Look sheet): small 16:9 thumbnails with their names in a row that scrolls
// sideways, one of them chosen (a check and a white rim). A tap only chooses; the sheet's button
// sends the look.
@Composable
fun PicturePicker(options: List<PictureChoice>, selectedKey: String?, onSelect: (PictureChoice) -> Unit, modifier: Modifier = Modifier,
    groupLabel: String? = null) {
    val c = CytTheme.colors
    Row(
        modifier.horizontalScroll(rememberScrollState()).selectableGroup()
            .then(if (groupLabel != null) Modifier.semantics { contentDescription = groupLabel } else Modifier),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (o in options) {
            val selected = !o.action && o.key == selectedKey
            val shape = CytTheme.shapes.small
            val tap = if (o.action) {
                Modifier.clickable(role = Role.Button) { onSelect(o) }.semantics(mergeDescendants = true) { o.actionDescription?.let { contentDescription = it } }
            } else {
                Modifier.selectable(selected, role = Role.RadioButton) { onSelect(o) }.semantics(mergeDescendants = true) {}
            }
            Column(
                Modifier.width(104.dp).clip(shape).then(tap).padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(96.dp, 54.dp).clip(shape).background(if (o.action) c.surface3 else o.color)
                        .border(if (selected) 2.dp else 1.dp, if (selected) c.textPrimary else Color.White.copy(alpha = 0.18f), shape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (o.image != null) Image(o.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    if (o.action) CytIcon(CytIcons.Plus, null, tint = c.textPrimary, size = 22.dp)
                    if (selected) {
                        Box(Modifier.size(24.dp).background(Color.Black.copy(alpha = 0.55f), CytTheme.shapes.pill), contentAlignment = Alignment.Center) {
                            CytIcon(CytIcons.Check, null, tint = Color.White, size = 16.dp)
                        }
                    }
                }
                Text(
                    o.label, Modifier.padding(top = 6.dp), color = if (selected) c.textPrimary else c.textSecondary,
                    style = CytTheme.type.bodySmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (o.sub != null) {
                    Text(o.sub, color = c.textSecondary, style = CytTheme.type.bodySmall, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
    }
}
