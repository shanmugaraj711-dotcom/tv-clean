package ai.promptstudio.tvclean.ui.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import ai.promptstudio.tvclean.core.flows.LookEdit
import ai.promptstudio.tvclean.core.flows.LookRow
import ai.promptstudio.tvclean.core.flows.PreviewGlow
import ai.promptstudio.tvclean.core.spec.BandChoice
import ai.promptstudio.tvclean.core.spec.ButtonsChoice
import ai.promptstudio.tvclean.core.spec.GlowChoice
import ai.promptstudio.tvclean.core.spec.LookKeys
import ai.promptstudio.tvclean.core.spec.Movement
import ai.promptstudio.tvclean.core.spec.StyleId
import ai.promptstudio.tvclean.ui.design.R
import ai.promptstudio.tvclean.ui.design.theme.CytTheme

// The Look page (prototype pageMain), in this order: the caption; the TV-changes line at large
// text; Picture; the photo tint line with "Turn it off"; Style, with the older home screen banner;
// Apps in a row with its helper lines; Names under every app; the More options link with its
// summary; the tiles note. A tap only chooses.
@Composable
internal fun ColumnScope.LookMainPage(
    state: LookEditorState,
    onEdit: (LookEdit) -> Unit,
    onMore: () -> Unit,
    onPick: (() -> Unit)?,
    pictures: Map<String, ImageBitmap>?,
    onUpdateHomeScreen: (() -> Unit)?,
) {
    val c = CytTheme.colors
    val name = styleName(state.style)
    val caption = lookMsg(R.string.tvclean_look_caption, "style" to name, "description" to styleDescription(state.style))
    val bold = remember(caption, name) {
        buildAnnotatedString {
            append(caption)
            val i = caption.indexOf(name)
            if (i >= 0) addStyle(SpanStyle(color = Color.Unspecified, fontWeight = FontWeight.Medium), i, i + name.length + 1)
        }
    }
    Text(bold, Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp), color = c.textSecondary, style = CytTheme.type.bodyMedium)
    // 12.2: at large text, or with a tall footer, the TV-changes line moves from under the button into the list.
    if (state.tvChanged && (LocalTvLineInList.current || LocalDensity.current.fontScale >= TV_LINE_IN_LIST_FONT_SCALE)) {
        LookBody(stringResource(R.string.tvclean_look_tv_note), Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp))
    }

    // Spec 12.1: each strip and the segments are a group named for their section.
    val pictureTitle = stringResource(R.string.tvclean_look_picture_title)
    LookSection(pictureTitle)
    PicturePicker(pictureChoices(state, pictures, onPick != null), state.picture, { ch ->
        if (ch.action) onPick?.invoke() else onEdit(LookEdit.SelectPicture(ch.key))
    }, groupLabel = pictureTitle)
    // A photo under a glow gets one line and a one tap way out (review M3).
    val glowOn = state.preview?.let { it.glow != PreviewGlow.NONE } ?: false
    if (state.photo && glowOn && state.reads27) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp).politeLive(), verticalAlignment = Alignment.CenterVertically) {
            LookBody(stringResource(R.string.tvclean_look_picture_tint), Modifier.weight(1f))
            TertiaryButton(stringResource(R.string.tvclean_look_picture_tint_off), { onEdit(LookEdit.TurnGlowOff) })
        }
    }

    val styleTitle = stringResource(R.string.tvclean_look_style_title)
    LookSection(styleTitle)
    if (!state.reads27) {
        Banner(
            stringResource(R.string.tvclean_look_update_banner), BannerTone.INFO, Modifier.padding(bottom = 12.dp),
            actionLabel = onUpdateHomeScreen?.let { stringResource(R.string.tvclean_look_update_action) }, onAction = { onUpdateHomeScreen?.invoke() },
        )
    }
    StylePicker(styleChoices(state, pictures), state.style.id, { ch -> StyleId.of(ch.key)?.let { onEdit(LookEdit.SelectStyle(it)) } }, groupLabel = styleTitle)

    AppsInARow(state, onEdit)

    // Home screen 3.0: every app in one row, under Apps in a row, with its note while it is on.
    val x = ExtraRows(state)
    if (x.oneRow) {
        ListGroup(Modifier.padding(top = 16.dp)) {
            LookSwitchRow(
                stringResource(R.string.tvclean_look_one_row), x.oneRowOn, { on -> x.edit(onEdit) { copy(oneRow = on) } },
                description = stringResource(R.string.tvclean_look_one_row_desc),
                lines = listOfNotNull(stringResource(R.string.tvclean_look_one_row_note).takeIf { x.oneRowOn }),
                keyName = KEY_NAMES["oneRow"].takeIf { state.keyNames },
            )
        }
    }

    // Names under every app, on the main page (review M4): it belongs to nobody, like apps in a row.
    val names = state.rows?.names ?: LookRow(state.names, state.reads27, if (state.reads27) null else ai.promptstudio.tvclean.core.flows.LookReason.NEEDS_UPDATE)
    ListGroup(Modifier.padding(top = 16.dp)) {
        LookSwitchRow(
            stringResource(R.string.tvclean_look_names), names.value ?: false, { onEdit(LookEdit.SetNames(it)) },
            description = stringResource(R.string.tvclean_look_names_desc), enabled = names.enabled,
            lines = listOfNotNull(names.reason?.let { reasonText(it) }), keyName = KEY_NAMES["names"].takeIf { state.keyNames },
        )
    }
    ListGroup(Modifier.padding(top = 16.dp)) {
        ListRow(stringResource(R.string.tvclean_look_more_title), subtitle = moreSummary(state), onClick = onMore)
    }
    val icons = x.tiles && x.acting.tiles == ai.promptstudio.tvclean.core.spec.TilesChoice.ICONS
    LookBody(stringResource(if (icons) R.string.tvclean_look_tiles_note_icons else R.string.tvclean_look_tiles_note), Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp))
}

// From font scale 1.5 the TV-changes line sits in the list instead of under the button.
const val TV_LINE_IN_LIST_FONT_SCALE = 1.5f

// A section label (an overline that is a heading, so TalkBack can jump between groups).
@Composable
internal fun LookSection(text: String, modifier: Modifier = Modifier) {
    Overline(text, modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp).semantics { heading() })
}

// The Picture strip: Choose a photo (with a picker), a new photo, the earlier look's photo, the
// photo kept for this TV, No picture, then the gallery (Diwali with its "Until" line).
@Composable
private fun pictureChoices(state: LookEditorState, given: Map<String, ImageBitmap>?, choose: Boolean): List<PictureChoice> {
    val pick = PictureChoice(PICK, stringResource(R.string.tvclean_look_picture_choose), null, action = true,
        actionDescription = stringResource(R.string.tvclean_look_picture_choose_cd)).takeIf { choose }
    val new = state.newPath?.let { PictureChoice(LookEditorState.NEW, stringResource(R.string.tvclean_look_picture_new), lookPicture(it, given)) }
    val earlier = state.earlierPath?.let { PictureChoice(LookEditorState.EARLIER, stringResource(R.string.tvclean_look_picture_earlier), lookPicture(it, given)) }
    val mine = state.minePath?.let { PictureChoice(LookEditorState.MINE, stringResource(R.string.tvclean_look_picture_mine), lookPicture(it, given)) }
    val none = PictureChoice(LookEditorState.NONE, stringResource(R.string.tvclean_look_picture_none), null, Color.Black)
    val locale = LocalConfiguration.current.locales[0]
    val gallery = state.pictures.map { p ->
        val until = p.until?.let { d -> lookMsg(R.string.tvclean_look_picture_until, "date" to d.format(DateTimeFormatter.ofPattern("d MMM", locale))) }
        PictureChoice(p.id, galleryPictureName(p.id, p.name), lookPicture(p.path, given), Color(p.backgroundArgb), sub = until)
    }
    return listOfNotNull(pick, new, earlier, mine, none) + gallery
}

private const val PICK = "pick"

// The five style cards: this TV with this picture in each style (the flows draw them), dimmed with
// "Needs the home screen update" where this TV's home screen cannot show them.
@Composable
private fun styleChoices(state: LookEditorState, given: Map<String, ImageBitmap>?): List<StyleChoice> {
    val needs = stringResource(R.string.tvclean_look_needs_update)
    return StyleId.entries.map { s ->
        val card = state.styleCards.firstOrNull { it.style == s }
        val enabled = (card?.enabled ?: true) && s in state.styles
        StyleChoice(
            s.id, styleName(s), styleDescription(s), card?.preview?.let { it.toTvLook(lookPicture(it.picturePath, given, sample = 8)) },
            enabled = enabled, note = needs.takeUnless { enabled },
        )
    }
}

// Apps in a row: 4 to 8 as one segmented choice with one sliding highlight, "Bigger" under the 4
// end and "More apps" under the 8 end, then the helper lines (a polite live region).
@Composable
private fun ColumnScope.AppsInARow(state: LookEditorState, onEdit: (LookEdit) -> Unit) {
    val title = stringResource(R.string.tvclean_look_columns_title)
    LookSection(title)
    val first = state.minColumns
    val last = state.maxColumns
    val options = (first..last).map { n ->
        val cd = when (n) { first -> R.string.tvclean_look_columns_cd_big; last -> R.string.tvclean_look_columns_cd_many; else -> R.string.tvclean_look_columns_cd }
        SegmentOption(n.toString(), n.toString(), lookMsg(cd, "count" to n))
    }
    SegmentedChoice(options, state.columns.toString(), { onEdit(LookEdit.SelectColumns(it.key.toInt())) }, groupLabel = title)
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp).clearAndSetSemantics {}, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.tvclean_look_columns_end_big), color = CytTheme.colors.textSecondary, style = CytTheme.type.bodySmall)
        Text(stringResource(R.string.tvclean_look_columns_end_many), color = CytTheme.colors.textSecondary, style = CytTheme.type.bodySmall)
    }
    Column(Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp).politeLive(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (line in columnsHelp(state)) LookBody(line)
    }
}

// Spec 11.3: the first line always (the 4 line at 4), the 8 line at 8, then at most one of: All
// apps, the empty dock, empty spaces, waiting apps, Spotlight's row one press down, the row under
// the dock, the 7 line. Counted on the dock as the TV shows it, the waiting apps too.
@Composable
private fun columnsHelp(state: LookEditorState): List<String> {
    val c = state.columns
    val n = state.apps
    val lines = mutableListOf(stringResource(if (c == state.minColumns) R.string.tvclean_look_columns_biggest else R.string.tvclean_look_columns_help))
    if (c == state.maxColumns) lines += stringResource(R.string.tvclean_look_columns_eight)
    val below = (n - state.waiting).coerceAtMost(c * 3) - c
    // With every app in one row there are no rows under the dock and nothing waits (its own note says so).
    val oneRow = ExtraRows(state).oneRowOn
    when {
        state.style == StyleId.ALL_APPS -> lines += lookMsg(R.string.tvclean_look_columns_all_apps, "count" to c)
        n == 0 -> lines += stringResource(R.string.tvclean_look_columns_empty_dock)
        n < c -> lines += lookMsg(R.string.tvclean_look_columns_empty, "apps" to n, "free" to c - n)
        oneRow -> if (c == 7) lines += stringResource(R.string.tvclean_look_columns_seven)
        state.waiting > 0 -> lines += lookMsg(R.string.tvclean_look_columns_waiting, "count" to state.waiting)
        state.style == StyleId.SPOTLIGHT && below > 0 -> lines += lookMsg(R.string.tvclean_look_columns_spotlight_below, "count" to below)
        below > 0 -> lines += lookMsg(R.string.tvclean_look_columns_below, "count" to below)
        c == 7 -> lines += stringResource(R.string.tvclean_look_columns_seven)
    }
    return lines
}

// The More options link's summary: the movement always first ("Quick movement, picked for this
// TV", "Lively movement with lift and zoom"), then what differs from the style's and the picture's
// own, in page order: "{movement}, plus glow, dock and buttons" when nothing does, else the first
// one and how many more.
@Composable
internal fun moreSummary(state: LookEditorState): String {
    val word = stringResource(
        when (state.movement) {
            Movement.LIVELY -> R.string.tvclean_look_word_lively
            Movement.SMOOTH -> R.string.tvclean_look_word_smooth
            Movement.QUICK -> R.string.tvclean_look_word_quick
            Movement.OFF -> R.string.tvclean_look_word_off
        },
    )
    val liftOn = state.lift && (state.movement == Movement.LIVELY || state.movement == Movement.SMOOTH)
    var first = if (liftOn) lookMsg(R.string.tvclean_look_more_lift, "movement" to word) else word
    if (state.movementPicked) first = lookMsg(R.string.tvclean_look_more_picked, "movement" to first)
    val items = changedWords(state)
    val locale = LocalConfiguration.current.locales[0]
    fun lower(s: String) = s.replaceFirstChar { it.lowercase(locale) }
    return when (items.size) {
        0 -> lookMsg(R.string.tvclean_look_more_summary_default, "movement" to first)
        1 -> lookMsg(R.string.tvclean_look_more_summary_two, "movement" to first, "second" to lower(items[0]))
        else -> lookMsg(R.string.tvclean_look_more_summary_more, "movement" to first, "second" to lower(items[0]), "count" to items.size - 1)
    }
}

// The rows of More options the person changed from the style's and the picture's own, in their
// words ("Glow from the top", "Picture darkened 40%").
@Composable
private fun changedWords(state: LookEditorState): List<String> {
    val r = state.rows ?: return emptyList()
    val d = state.details
    val own = LookKeys.own(state.style)
    val out = mutableListOf<String>()
    fun <T> changed(row: LookRow<T>, mine: T?, ownValue: T?): Boolean = row.visible && row.enabled && mine != null && mine != ownValue
    if (changed(r.bigName, d.bigName, own.bigName)) out += bigNameWord(r.bigName.value == true)
    if (changed(r.spotColour, d.spotColour, own.spotColour)) out += stringResource(if (r.spotColour.value == true) R.string.tvclean_look_word_spot_on else R.string.tvclean_look_word_spot_off)
    if (changed(r.glow, d.glow, own.glow)) r.glow.value?.let { out += glowWord(it) }
    if (changed(r.ring, d.ring, own.ring)) out += stringResource(if (r.ring.value == true) R.string.tvclean_look_word_ring_on else R.string.tvclean_look_word_ring_off)
    if (changed(r.band, d.band, own.band)) r.band.value?.let { out += bandWord(it) }
    if (changed(r.buttons, d.buttons, own.buttons)) r.buttons.value?.let { out += buttonsWord(it) }
    if (changed(r.greenWifi, d.greenWifi, own.greenWifi)) out += stringResource(if (r.greenWifi.value == true) R.string.tvclean_look_word_wifi_on else R.string.tvclean_look_word_wifi_off)
    // Darken and Sky only when they differ from the picture's own, as "Use {style}'s own settings" counts them.
    if (r.dimChanged) out += lookMsg(R.string.tvclean_look_word_dim, "dim" to (r.dim.value ?: d.dim ?: 0))
    if (r.skyChanged) out += stringResource(if (r.sky.value == true) R.string.tvclean_look_word_sky_on else R.string.tvclean_look_word_sky_off)
    // Home screen 3.0's rows, after the 2.7 ones.
    out += extraWords(state)
    return out
}

@Composable
private fun bigNameWord(on: Boolean) = stringResource(if (on) R.string.tvclean_look_word_big_name_on else R.string.tvclean_look_word_big_name_off)

@Composable
private fun glowWord(g: GlowChoice) = stringResource(
    when (g) { GlowChoice.SOFT -> R.string.tvclean_look_word_glow_soft; GlowChoice.FROM_TOP -> R.string.tvclean_look_word_glow_top; GlowChoice.NONE -> R.string.tvclean_look_word_glow_none },
)

@Composable
private fun bandWord(b: BandChoice) = stringResource(
    when (b) { BandChoice.SOLID -> R.string.tvclean_look_word_band_solid; BandChoice.SEE_THROUGH -> R.string.tvclean_look_word_band_see; BandChoice.GLASS -> R.string.tvclean_look_word_band_glass },
)

@Composable
private fun buttonsWord(b: ButtonsChoice) = stringResource(
    when (b) {
        ButtonsChoice.WITH_NAMES -> R.string.tvclean_look_word_buttons_names
        ButtonsChoice.ROUND -> R.string.tvclean_look_word_buttons_round
        ButtonsChoice.MENU -> R.string.tvclean_look_word_buttons_menu
    },
)
