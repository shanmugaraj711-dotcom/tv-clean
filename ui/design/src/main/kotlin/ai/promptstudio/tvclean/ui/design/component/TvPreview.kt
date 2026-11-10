package ai.promptstudio.tvclean.ui.design.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import ai.promptstudio.tvclean.core.flows.PreviewBand
import ai.promptstudio.tvclean.core.flows.PreviewButtons
import ai.promptstudio.tvclean.core.flows.PreviewGlow
import ai.promptstudio.tvclean.core.spec.AppColours
import ai.promptstudio.tvclean.core.spec.HighlightChoice
import ai.promptstudio.tvclean.ui.design.R
import ai.promptstudio.tvclean.ui.design.theme.CytTheme
import ai.promptstudio.tvclean.ui.design.theme.Outfit

// What the TV's home screen will look like, for the Look sheet, the style cards and Home's Look
// row. Drawn from the launcher's keys (toTvLook maps the preview the flows read back from the
// bytes the push would send, so it never draws from the editor's state), layer by layer as
// launcher 2.7 draws them, with its numbers on a 960 x 540 dp screen:
// - the background colour, the picture (centre crop), the dim, the sky (Sky.java's three colours,
//   the middle at 55%, and the sun's warm glow 80% down) at its strength;
// - the glow in the focused app's colour (MainActivity.setAmbient): a soft round blob, a wash from
//   the top, or none; on the cinematic shelf (Spotlight) the app's art at cinematic_art,
//   the colour flood across the top (only with ambient_glow), then the two-gradient shade
//   (scrimDrawable) at cinematic_scrim over both;
// - the header: the day's greeting (a festival's own, in its gold), the clock and the date; the
//   buttons as labelled pills (Inputs when the TV has inputs, Wi-Fi, Settings), round icons, or one
//   gear level with the clock; Wi-Fi green when asked;
// - the big name with its bottom 40 dp above the dock (Spotlight: bigger, over the dots, one per
//   dock app);
// - the dock band (DockDecoration): solid #141416 at dock_opacity, glass (white 14% to 5%) or the
//   shelf's own #121014 at 55%, 22 dp at the sides and 14 dp above and below (even padding: the
//   same all round), 22 dp taller with names; empty dock spaces stay band;
// - the tiles where the launcher's grid puts them (for 5 a row: 78 dp margins, 26 dp gaps, the dock
//   at 382 dp), smaller when rows under the dock must fit; the first focused at 1.12 with Lift and
//   zoom (raised, with a shadow), 1.10 otherwise, 1 with movement Off, its ring white, in the app's
//   colour or in the festival's gold; names under every tile when asked; the rows under the dock
//   and the next row peeking at the bottom, from the TV's other apps;
// - with no dock row (All apps), one grid of every app from under the clock, dock first, the name
//   under the focused tile only.
// It is a picture, not the TV: always left to right like the TV (the direction is set around
// BoxWithConstraints, not inside it, so in an RTL layout the offsets are measured from the left
// edge), text at a fixed size, one description for TalkBack (none when drawn over something that
// has its own, like the photo frame). `crop`: drawn over the photo crop, with no background, the
// glow and the shade at full strength and the chrome faint.
// `emptyDockSlots` (0.4.0, fill_dock false): a dock space after the person's dock apps has no tile,
// so the band shows through, as on the TV. With fill_dock true the TV fills it with other apps.
// Home screen 3.0 (0.4.5), each drawn from launcher 3.0 at b06ab21 (cited where it is drawn), all
// with defaults that draw exactly what 2.7 draws: `oneRow` (single_row), `bandAll` (dock_band_all),
// band NONE with a dock (dock_band false), `greetingShown` (show_greeting), `dateBelow`
// (date_below_clock), `brightDate` (header_white), `iconTiles` (uniform_tiles), `highlight`
// (glass_style), and `dockColours` and `otherColours`, each app's colour for its icon card.
@Immutable
data class TvLook(
    val background: Color,
    val picture: ImageBitmap?,
    val dim: Int,
    val sky: Int,
    val skyTop: Color,
    val skyBottom: Color,
    val columns: Int,
    val rowsBelowDock: Int,
    val dockRow: Boolean,
    val dockTitle: Boolean,
    val cinematic: Boolean,
    val accent: Color?,
    val greeting: String,
    val focusScale: Float,
    val dock: List<String>,
    val clock: String,
    val emptyDockSlots: Boolean = false,
    // look2, all with defaults (LookPreview's look2 fields, mapped by toTvLook in TvLookFrom.kt):
    val names: Boolean = false,
    val glow: PreviewGlow = PreviewGlow.RADIAL,
    val glowColour: Color = Color(0xFF8A8A8A),
    val ring: Boolean = false,
    val band: PreviewBand = PreviewBand.SOLID,
    val bandOpacity: Int = 100,
    val evenPadding: Boolean = false,
    val buttons: PreviewButtons = PreviewButtons.LABELLED,
    val roundInputs: Boolean = false,
    val wifiGreen: Boolean = false,
    val inputs: Boolean = false,
    val dots: Boolean = true,
    val scrim: Int = 100,
    val art: Int = 50,
    val lift: Boolean = false,
    val skyMiddle: Color = Color.Unspecified,
    val sunGlow: Float = 0f,
    val crop: Boolean = false,
    val others: List<String> = emptyList(),
    val date: String = "",
    val dayGreeting: String = "",
    // Additive beyond the frozen list: the clock's minute (the day's greeting and AM or PM, from
    // strings_tvpreview.xml, when `dayGreeting` is empty; -1 draws neither) and the sun's place across.
    val minuteOfDay: Int = -1,
    val sunX: Float = 0.5f,
    val oneRow: Boolean = false,
    val bandAll: Boolean = false,
    val greetingShown: Boolean = true,
    val dateBelow: Boolean = false,
    val brightDate: Boolean = false,
    val iconTiles: Boolean = false,
    val highlight: HighlightChoice = HighlightChoice.GLASS,
    val dockColours: List<Color> = emptyList(),
    val otherColours: List<Color> = emptyList(),
)

private const val TV_W = 960f
private const val TV_H = 540f
private val WHITE = Color.White
private val SHADE = Color(0xFF060406)
private val GREEN_WIFI = Color(0xFF34C759)
private val FULL = Size(TV_W, TV_H)
// The cinematic art's stand-in: the app's colour a third of the way up from black, at half of
// cinematic_art (matched to the emulator's shots of a dark banner with a coloured name).
private const val ART_COLOUR = 0.35f
private const val ART_STRENGTH = 0.5f

// Launcher 2.7's layout, in dp on its 960 x 540 screen: activity_main.xml (56 dp side padding, the
// header from 26 dp, the grid 10 dp under it with its own 10 dp padding), item_app.xml (8 dp above
// and below the tile, the name 7 dp under it), AppAdapter (tile width and height, the item heights,
// the room kept for the title and under the last row), DockDecoration (the dock's 6 and 12 dp
// offsets, the band) and MainActivity's grid padding, which pushes the dock towards the bottom.
private const val GRID_LEFT = 66f
private const val GRID_INNER = 828f
private const val GRID_WIDTH = 848f
private const val GRID_TOP = 117f
private const val GRID_H = TV_H - GRID_TOP
private const val FOCUS_FIT = 1.10f
private const val ITEM_EXTRA = 45f
private const val COMPACT_EXTRA = 32f
private const val DOCK_EXTRA = 18f
private const val BOTTOM_ROOM = 30f
private const val TITLE_ROOM = 104f
private const val TOP_ROOM = 12f
private const val COMPACT_BOTTOM = 16f
private const val MIN_HEIGHT_FIT = 0.85f
private const val BAND_PAD = 14f
private const val BAND_SIDE = 22f
private const val NAMES_ROOM = 22f
// single_row without always_show_names sits the row 29 dp lower: the 7 dp gap and the 22 dp name
// item_app.xml keeps under a tile are not used (MainActivity.java:626-628 at b06ab21).
private const val ONE_ROW_DROP = 29f

// The launcher's tile width for `columns` tiles a row when only the width counts (AppAdapter.tileWidthPx):
// the cell less 12 dp, room for the focus scale, between 80 and 180 dp.
fun tvTileWidth(columns: Int): Float {
    val cell = GRID_INNER / columns.coerceIn(1, 8)
    return ((cell - 12f) / FOCUS_FIT).coerceIn(80f, 180f)
}

// The tile's corners (AppAdapter.cornerRadius): 16 dp at the full 180 dp, smaller with the tile.
private fun tileCorner(w: Float) = 16f * min(1f, w / 180f)

// Where everything sits for this look. With rows under the dock the tiles shrink to fit them (at
// most to 85%), and the dock keeps the title's room above it; what still does not fit runs off the
// bottom, as on the TV.
private class TvLayout(look: TvLook) {
    // The grid starts under the header, which the 3.0 header keys make shorter or taller.
    val gridTop = GRID_TOP + headerShift(look)
    private val gridH = TV_H - gridTop
    val n = look.columns.coerceIn(1, 8)
    val cell = GRID_INNER / n
    val dockRow = look.dockRow
    val rows = look.rowsBelowDock.coerceIn(0, 3)
    private val title = look.dockTitle
    val w: Float = run {
        val w0 = tvTileWidth(n)
        if (rows <= 0) {
            w0
        } else {
            val reserved = if (dockRow) DOCK_EXTRA + BOTTOM_ROOM + (if (title) TITLE_ROOM else 0f) else TOP_ROOM + COMPACT_BOTTOM
            val tileH = (gridH - reserved) / (rows + 1) - (if (dockRow) ITEM_EXTRA else COMPACT_EXTRA)
            max(max(80f, w0 * MIN_HEIGHT_FIT), min(w0, tileH * 180f / 101f))
        }
    }
    val h = w * 101f / 180f
    private val itemH = h + if (dockRow) ITEM_EXTRA else COMPACT_EXTRA
    // MainActivity's grid padding: never below 0 (Math.max with the library's clearance, 0 with
    // app_library off), so rows that do not fit at the smallest tile run off the bottom.
    private val padTop: Float = when {
        !dockRow -> TOP_ROOM
        title && rows > 0 -> max(gridH - (itemH + DOCK_EXTRA) - itemH * rows - BOTTOM_ROOM, TITLE_ROOM)
        else -> max(0f, gridH - (itemH + DOCK_EXTRA) - itemH * rows - BOTTOM_ROOM)
    } + if (look.oneRow && !look.names) ONE_ROW_DROP else 0f
    // The dock's tiles; the k-th grid row under the dock (from 1); the r-th row of the plain grid (from 0).
    val dockTop = gridTop + padTop + 6f + 8f
    fun rowTop(k: Int) = gridTop + padTop + itemH + DOCK_EXTRA + (k - 1) * itemH + 8f
    fun gridTop(r: Int) = gridTop + padTop + 4f + r * itemH
    fun x(i: Int) = GRID_LEFT + cell * i + (cell - w) / 2f
    val corner = tileCorner(w)
}

// Left to right like the TV: the direction is set around BoxWithConstraints, not inside it, so in an
// RTL layout the offsets inside are measured from the left edge and the picture is not pushed off.
@Composable
fun TvPreview(look: TvLook, contentDescription: String, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { TvPreviewLtr(look, contentDescription, modifier) }
}

@Composable
private fun TvPreviewLtr(look: TvLook, contentDescription: String, modifier: Modifier) {
    val shape = CytTheme.shapes.medium
    BoxWithConstraints(
        modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shape).background(if (look.crop) Color.Transparent else look.background)
            .then(if (look.crop) Modifier else Modifier.border(1.dp, WHITE.copy(alpha = 0.18f), shape))
            .clearAndSetSemantics { if (contentDescription.isNotEmpty()) { this.contentDescription = contentDescription; role = Role.Image } },
    ) {
        val tv = Tv(maxWidth.value / TV_W)
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
            if (look.picture != null && !look.crop) Image(look.picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (look.dim > 0) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = look.dim.coerceIn(0, 90) / 100f)))
            if (look.sky > 0 && !look.crop) {
                Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = look.sky.coerceIn(0, 100) / 100f; compositingStrategy = CompositingStrategy.Offscreen }) {
                    inTv { sky(look) }
                }
            }
            Canvas(Modifier.fillMaxSize()) { inTv { tint(look) } }
            Header(look, tv)
            if (look.dockRow) Dock(look, tv) else Grid(look, tv)
        }
    }
}

// TV dp to this drawing's dp, and to sp for text (the font scale is 1 inside the drawing). `em`
// letter spacing as Android's android:letterSpacing; no line height is the font's own, like a
// TextView without font padding.
@Immutable
private class Tv(val s: Float) {
    fun u(v: Float): Dp = (v * s).dp
    fun sp(v: Float) = (v * s).sp
    fun text(size: Float, weight: FontWeight = FontWeight.Normal, color: Color = WHITE, em: Float = 0f) = TextStyle(
        color = color, fontFamily = Outfit, fontWeight = weight, fontSize = sp(size), letterSpacing = sp(size * em),
    )
}

// Places the content with its bottom edge at (x, bottom) in TV dp.
private fun Modifier.bottomAt(tv: Tv, x: Float, bottom: Float) = layout { m, c ->
    val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
    layout(p.width, p.height) { p.place((x * tv.s * density).roundToInt(), (bottom * tv.s * density).roundToInt() - p.height) }
}

// Places the content with its bottom right corner at (right, bottom) in TV dp.
private fun Modifier.bottomEndAt(tv: Tv, right: Float, bottom: Float) = layout { m, c ->
    val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
    layout(p.width, p.height) { p.place((right * tv.s * density).roundToInt() - p.width, (bottom * tv.s * density).roundToInt() - p.height) }
}

// Draws in TV dp: 960 x 540 whatever the size on the phone.
private inline fun DrawScope.inTv(crossinline block: DrawScope.() -> Unit) {
    val k = size.width / TV_W
    scale(k, k, pivot = Offset.Zero) { block() }
}

// SkyView: the three colours top to bottom, the middle at 55%, then the sun's warm glow.
private fun DrawScope.sky(look: TvLook) {
    val mid = if (look.skyMiddle == Color.Unspecified) lerp(look.skyTop, look.skyBottom, 0.5f) else look.skyMiddle
    drawRect(Brush.verticalGradient(0f to look.skyTop, 0.55f to mid, 1f to look.skyBottom, startY = 0f, endY = TV_H), size = FULL)
    if (look.sunGlow > 0f) {
        val warm = lerp(look.skyBottom, Color(0xFFFFD9A0), 0.65f)
        drawRect(
            Brush.radialGradient(listOf(warm.copy(alpha = look.sunGlow.coerceIn(0f, 1f)), warm.copy(alpha = 0f)),
                center = Offset(look.sunX * TV_W, TV_H * 0.80f), radius = TV_W * 0.55f),
            size = FULL,
        )
    }
}

// The layers that tint the picture, in the focused app's colour: the glow, or on the cinematic
// shelf the art, the colour flood and the shade, in the launcher's order (activity_main.xml: heroArt,
// ambient, then heroScrim over both). The photo crop draws them too, at full strength.
private fun DrawScope.tint(look: TvLook) {
    val c = look.glowColour
    if (look.cinematic) {
        // heroArt: the app's banner softened to a few pixels and stretched over the screen at
        // cinematic_art. The phone has no banner, and most banners are dark round their name, so
        // the art is a dark wash in the app's colour: it mostly darkens, as on the TV.
        if (!look.crop && look.art > 0) {
            drawRect(lerp(Color.Black, c, ART_COLOUR).copy(alpha = look.art.coerceIn(0, 100) / 100f * ART_STRENGTH), size = FULL)
        }
        if (look.glow != PreviewGlow.NONE) {
            drawRect(Brush.radialGradient(0f to c.copy(alpha = 0xA6 / 255f), 0.5f to c.copy(alpha = 0x47 / 255f), 1f to c.copy(alpha = 0f),
                center = Offset(TV_W * 0.80f, TV_H * 0.06f), radius = TV_W * 0.78f), size = FULL)
        }
        val a = look.scrim.coerceIn(0, 100) / 100f
        if (a > 0f) {
            drawRect(Brush.horizontalGradient(0f to SHADE.copy(alpha = 0xD1 / 255f * a), 0.42f to SHADE.copy(alpha = 0x59 / 255f * a), 0.70f to SHADE.copy(alpha = 0f),
                startX = 0f, endX = TV_W), size = FULL)
            drawRect(Brush.verticalGradient(0f to SHADE.copy(alpha = 0f), 0.28f to SHADE.copy(alpha = 0f), 0.58f to SHADE.copy(alpha = 0xDB / 255f * a),
                0.76f to SHADE.copy(alpha = a), startY = 0f, endY = TV_H), size = FULL)
        }
        return
    }
    when (look.glow) {
        PreviewGlow.NONE -> {}
        PreviewGlow.HORIZON -> {
            drawRect(Brush.verticalGradient(0f to c.copy(alpha = 0x66 / 255f), 0.4f to c.copy(alpha = 0x24 / 255f), 1f to c.copy(alpha = 0f),
                startY = 0f, endY = TV_H * 0.75f), size = FULL)
            drawRect(Brush.radialGradient(listOf(c.copy(alpha = 0x40 / 255f), c.copy(alpha = 0f)), center = Offset(TV_W * 0.5f, -TV_H * 0.25f), radius = TV_W * 0.55f), size = FULL)
        }
        PreviewGlow.RADIAL -> {
            drawRect(Brush.radialGradient(0f to c.copy(alpha = 0x5C / 255f), 0.45f to c.copy(alpha = 0x26 / 255f), 1f to c.copy(alpha = 0f),
                center = Offset(TV_W * 0.32f, TV_H * 0.42f), radius = TV_W * 0.62f), size = FULL)
        }
    }
}

// The header (activity_main.xml): the greeting from 26 dp, the clock under it with AM or PM, a
// divider and the date bottom aligned, the buttons bottom aligned 6 dp above the clock's bottom.
@Composable
private fun Header(look: TvLook, tv: Tv) {
    val accent = look.accent
    val festive = accent != null && look.greeting.isNotEmpty()
    val greeting = when {
        festive -> look.greeting
        look.dayGreeting.isNotEmpty() -> look.dayGreeting
        look.minuteOfDay >= 0 -> stringResource(dayPart(look.minuteOfDay))
        else -> ""
    }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (look.crop) 0.6f else 1f }) {
        // show_greeting false: the greeting is GONE (MainActivity.java:467 at b06ab21), so the clock moves up.
        if (greeting.isNotEmpty() && look.greetingShown) {
            // A festival's greeting is its accent a quarter of the way to white (liftTowardsWhite).
            val colour = if (accent != null && festive) lerp(accent, WHITE, 0.25f) else WHITE.copy(alpha = 0x8C / 255f)
            Text(greeting.uppercase(), Modifier.offset(tv.u(60f), tv.u(26f)), maxLines = 1, style = tv.text(12f, FontWeight.Medium, colour, em = 0.22f))
        }
        val clockBottom = clockBottom(look)
        // header_white (MainActivity.java:468-472 at b06ab21): the date and the line before it in full white.
        val dateColour = if (look.brightDate) WHITE else WHITE.copy(alpha = 0xA6 / 255f)
        Row(Modifier.bottomAt(tv, 56f, clockBottom), verticalAlignment = Alignment.Bottom) {
            Text(look.clock, maxLines = 1, style = tv.text(52f, FontWeight.Light, em = -0.04f))
            if (look.minuteOfDay >= 0) {
                Text(stringResource(if (look.minuteOfDay < 720) R.string.tvclean_tv_am else R.string.tvclean_tv_pm), Modifier.padding(start = tv.u(6f), bottom = tv.u(8f)),
                    maxLines = 1, style = tv.text(15f, FontWeight.Medium, WHITE.copy(alpha = 0xB3 / 255f), em = 0.08f))
            }
            if (look.date.isNotEmpty() && !look.dateBelow) {
                Box(Modifier.padding(start = tv.u(16f), end = tv.u(16f), bottom = tv.u(8f)).width(tv.u(1f)).height(tv.u(26f))
                    .background(if (look.brightDate) WHITE else WHITE.copy(alpha = 0x33 / 255f)))
                Text(look.date, Modifier.padding(bottom = tv.u(9f)), maxLines = 1, style = tv.text(14f, color = dateColour, em = 0.14f))
            }
        }
        // date_below_clock (MainActivity.moveDateBelowClock, MainActivity.java:2296-2312 at b06ab21): the
        // date's column moves under the clock row with a 4 dp top margin, at the clock's left, and the
        // line before it goes; same size and colour.
        if (look.date.isNotEmpty() && look.dateBelow) {
            Text(look.date, Modifier.offset(tv.u(56f), tv.u(clockBottom + 4f + (DATE_LINE - 14f * OUTFIT_LINE) / 2f)), maxLines = 1,
                style = tv.text(14f, color = dateColour, em = 0.14f))
        }
        Buttons(look, tv)
    }
}

// The clock row's bottom: 26 dp, the greeting's line and the 52 sp clock.
private const val CLOCK_BOTTOM = 108f

// Home screen 3.0's header (activity_main.xml:160-323 at b06ab21). The greeting's line is what
// CLOCK_BOTTOM keeps above the clock (52 sp of Outfit at its 1.26 line, includeFontPadding false);
// the date's line (14 sp, with the TextView's font padding) is that line at 14 sp. Hiding the greeting
// lifts the clock, the buttons (topButtons aligns to leftBlock's bottom, 6 dp up) and the grid by its
// line; the date under the clock adds 4 dp and its line to leftBlock, so they all move down. The
// dock keeps its place: MainActivity pushes it down from the grid's bottom.
private const val OUTFIT_LINE = 1.26f
private const val GREETING_LINE = CLOCK_BOTTOM - 26f - 52f * OUTFIT_LINE
private const val DATE_LINE = GREETING_LINE * 14f / 12f

private fun clockBottom(look: TvLook) = CLOCK_BOTTOM - if (look.greetingShown) 0f else GREETING_LINE

// leftBlock's bottom less the 2.7 one: how far the buttons and the grid move.
private fun headerShift(look: TvLook) = clockBottom(look) + (if (look.dateBelow) 4f + DATE_LINE else 0f) - CLOCK_BOTTOM

// The launcher's day parts for the greeting (MainActivity.updateGreeting, the default hours).
private fun dayPart(minute: Int): Int = when (minute / 60) {
    in 5..11 -> R.string.tvclean_tv_good_morning
    in 12..16 -> R.string.tvclean_tv_good_afternoon
    in 17..21 -> R.string.tvclean_tv_good_evening
    else -> R.string.tvclean_tv_good_night
}

// Labelled pills, round icons, or one round menu button level with the clock (TopButton,
// TopButtonIcon: 42 dp high, 10 dp apart, the 22 dp icon 9 dp before the 15 sp name).
@Composable
private fun Buttons(look: TvLook, tv: Tv) {
    val menu = look.buttons == PreviewButtons.MENU
    val round = look.buttons == PreviewButtons.ROUND
    val icon = look.accent ?: WHITE
    // A festival on (Festival.java, applyFestiveSkin): a bell for Inputs, a diya for the network and
    // a marigold for Settings, in their own colours.
    val festive = look.accent != null && look.greeting.isNotEmpty()
    val bottom = if (menu) 86f + clockBottom(look) - CLOCK_BOTTOM else CLOCK_BOTTOM + headerShift(look) - 6f
    Row(Modifier.bottomEndAt(tv, TV_W - 56f, bottom), horizontalArrangement = Arrangement.spacedBy(tv.u(10f))) {
        if (menu) {
            Pill(TvIcons.gear, null, icon, tv, if (festive) FestiveIcon.MARIGOLD else null)
        } else {
            if (look.inputs) Pill(TvIcons.input, if (look.roundInputs) null else stringResource(R.string.tvclean_tv_inputs), icon, tv, if (festive) FestiveIcon.BELL else null)
            Pill(TvIcons.wifi, if (round) null else stringResource(R.string.tvclean_tv_wifi), if (look.wifiGreen) GREEN_WIFI else icon, tv, if (festive) FestiveIcon.DIYA else null)
            Pill(TvIcons.gear, if (round) null else stringResource(R.string.tvclean_tv_settings), icon, tv, if (festive) FestiveIcon.MARIGOLD else null)
        }
    }
}

private enum class FestiveIcon { DIYA, BELL, MARIGOLD }

// pill_bg: white 18% to 8% top to bottom, a 22% edge, 21 dp corners.
@Composable
private fun Pill(vector: ImageVector, label: String?, tint: Color, tv: Tv, festive: FestiveIcon? = null) {
    val shape = RoundedCornerShape(tv.u(21f))
    Row(
        Modifier.height(tv.u(42f)).then(if (label == null) Modifier.width(tv.u(42f)) else Modifier)
            .background(Brush.verticalGradient(listOf(WHITE.copy(alpha = 0x2E / 255f), WHITE.copy(alpha = 0x14 / 255f))), shape)
            .border(tv.u(1f), WHITE.copy(alpha = 0x38 / 255f), shape)
            .padding(start = if (label == null) 0.dp else tv.u(16f), end = if (label == null) 0.dp else tv.u(20f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (label == null) Arrangement.Center else Arrangement.spacedBy(tv.u(9f)),
    ) {
        if (festive != null) {
            Canvas(Modifier.size(tv.u(22f))) {
                val k = size.width / 24f
                scale(k, k, pivot = Offset.Zero) {
                    when (festive) {
                        FestiveIcon.DIYA -> diya()
                        FestiveIcon.BELL -> bell()
                        FestiveIcon.MARIGOLD -> marigold()
                    }
                }
            }
        } else {
            Image(vector, null, Modifier.size(tv.u(22f)), colorFilter = ColorFilter.tint(tint))
        }
        if (label != null) Text(label, maxLines = 1, style = tv.text(15f, FontWeight.Medium, em = 0.01f))
    }
}

@Composable
private fun Dock(look: TvLook, tv: Tv) {
    val g = TvLayout(look)
    // fill_dock false: a dock space past the dock list stays empty; true: the TV fills it from the
    // other apps. The grid under the dock goes on from there: the rest of the dock list, then the others.
    val all = look.dock + look.others
    val docked: List<String?> = List(g.n) { if (look.emptyDockSlots) look.dock.getOrNull(it) else all.getOrNull(it) }
    val grid = if (look.emptyDockSlots) look.dock.drop(g.n) + look.others else all.drop(g.n)
    // Each app's colour (icon tiles), in the same order.
    val allColours = look.dockColours + look.otherColours
    val dockedColours: List<Color?> = List(g.n) { if (look.emptyDockSlots) look.dockColours.getOrNull(it) else allColours.getOrNull(it) }
    val gridColours = if (look.emptyDockSlots) look.dockColours.drop(g.n) + look.otherColours else allColours.drop(g.n)
    val chrome = if (look.crop) 0.7f else 1f

    // The big name (hero): 48 sp light, its bottom 26 dp above the band; on the cinematic shelf 68 sp,
    // over the meta line's room (kept even when empty) and the dots, whose bottom is 40 dp above the dock.
    if ((look.dockTitle || look.cinematic) && look.dock.isNotEmpty()) {
        val nameBottom = g.dockTop - 40f - if (look.cinematic) 54f else 0f
        Text(look.dock.first(), Modifier.bottomAt(tv, 68f, nameBottom).graphicsLayer { alpha = chrome }, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = tv.text(if (look.cinematic) 68f else 48f, FontWeight.Light, em = if (look.cinematic) -0.025f else -0.02f))
    }
    // One dot per dock app in the row, the focused one long and white (updateHeroDots), only from two.
    val dots = min(g.n, look.dock.size)
    if (look.cinematic && look.dots && dots > 1) {
        Row(Modifier.offset(tv.u(72f), tv.u(g.dockTop - 46f)).graphicsLayer { alpha = chrome }, horizontalArrangement = Arrangement.spacedBy(tv.u(6f))) {
            for (i in 0 until dots) {
                Box(Modifier.size(tv.u(if (i == 0) 20f else 6f), tv.u(6f)).background(if (i == 0) WHITE else WHITE.copy(alpha = 0x4D / 255f), RoundedCornerShape(tv.u(3f))))
            }
        }
    }
    // dock_band false (DockDecoration.java:90 at b06ab21): no band and no edge; the tiles stay put.
    if (look.band != PreviewBand.NONE) Band(look, g, tv, more = grid.isNotEmpty())
    val ring = ringColour(look)
    for (i in 0 until g.n) docked[i]?.let { Tile(it, i == 0, look, ring, g.x(i), g.dockTop, g, tv, label = look.names, compact = false, colour = dockedColours[i]) }
    // single_row (MainActivity.java:522-523 and AppAdapter.java:420 at b06ab21): one sideways row, a
    // cell of the dock's width each, the dock apps first and then the others. The wrapper clips at the
    // grid's edge (activity_main.xml:374-379), and the next cell's tile starts past it, so at rest the
    // row shows the dock's own tiles and nothing under them (Down is swallowed, MainActivity.java:2242-2246).
    if (look.oneRow) return
    // The rows under the dock and the next row peeking at the bottom edge.
    for (k in 1..g.rows + 1) {
        val y = g.rowTop(k)
        if (y >= TV_H) break
        for (i in 0 until g.n) grid.getOrNull((k - 1) * g.n + i)?.let {
            Tile(it, false, look, ring, g.x(i), y, g, tv, label = look.names, compact = false, colour = gridColours.getOrNull((k - 1) * g.n + i))
        }
    }
}

// dock_row false (All apps): one grid from just under the header, the name under the focused tile
// (under every tile with names on).
@Composable
private fun Grid(look: TvLook, tv: Tv) {
    val g = TvLayout(look)
    val all = look.dock + look.others
    val colours = look.dockColours + look.otherColours
    val ring = ringColour(look)
    var r = 0
    while (g.gridTop(r) < TV_H) {
        for (i in 0 until g.n) {
            val focused = r == 0 && i == 0
            all.getOrNull(r * g.n + i)?.let {
                Tile(it, focused, look, ring, g.x(i), g.gridTop(r), g, tv, label = look.names || focused, compact = true, colour = colours.getOrNull(r * g.n + i))
            }
        }
        r++
    }
}

// White; the app's colour with app_glow or the cinematic dock's coloured focus; a festival's gold.
private fun ringColour(look: TvLook): Color = look.accent ?: if (look.ring) look.glowColour else WHITE

// DockDecoration: around the dock's tiles, 22 dp at the sides (less when the grid's edge is nearer)
// and 14 dp above and below with 26 dp corners, or the same padding all round with corners
// concentric with the tiles'; 22 dp taller with names. Solid #141416 at dock_opacity, the light
// glass (white 14% to 5%) or the cinematic shelf's own #121014 at 55%.
// Home screen 3.0 (DockDecoration.java:90-140 at b06ab21): in one row the sides keep their full 22 dp
// (even padding: only the room above limits it) and the band stops 1 dp inside the grid's edges
// with its rounded end; Band behind every app takes in every laid out cell, and the grid lays out
// the next cell in its end padding (clipToPadding false), so with more apps than the dock's the band
// runs to the grid's right edge.
@Composable
private fun Band(look: TvLook, g: TvLayout, tv: Tv, more: Boolean = false) {
    val left = g.x(0)
    val right = g.x(g.n - 1) + g.w
    val inGridLeft = left - 56f
    val inGridRight = GRID_WIDTH - (right - 56f)
    val (padX, padY, corner) = if (look.evenPadding) {
        val room = (if (look.oneRow) g.dockTop - g.gridTop else min(g.dockTop - g.gridTop, min(inGridLeft, inGridRight))) - 1f
        val even = max(0f, min(g.cell - g.w, room))
        Triple(even, even, g.corner + even)
    } else {
        Triple(if (look.oneRow) BAND_SIDE else max(0f, min(BAND_SIDE, min(inGridLeft, inGridRight) - 1f)), BAND_PAD, 26f)
    }
    if (look.oneRow) {
        val l = max(left - padX, 57f)
        val r = if (look.bandAll && more) 56f + GRID_WIDTH - 1f else min(right + padX, 56f + GRID_WIDTH - 1f)
        BandBox(look, g, tv, l, r - l, padY, corner)
        return
    }
    BandBox(look, g, tv, left - padX, right - left + 2 * padX, padY, corner)
}

// The band itself: from `x`, `width` wide, around the dock's tiles.
@Composable
private fun BandBox(look: TvLook, g: TvLayout, tv: Tv, x: Float, width: Float, padY: Float, corner: Float) {
    val shape = RoundedCornerShape(tv.u(corner))
    val glass = look.band == PreviewBand.GLASS
    val fill: Brush = when {
        look.crop -> SolidColor(Color.Transparent)
        look.cinematic -> SolidColor(Color(0xFF121014).copy(alpha = 0x8C / 255f))
        glass -> Brush.verticalGradient(listOf(WHITE.copy(alpha = 0x24 / 255f), WHITE.copy(alpha = 0x0D / 255f)))
        else -> SolidColor(Color(0xFF141416).copy(alpha = look.bandOpacity.coerceIn(0, 100) / 100f))
    }
    val edge = when {
        look.crop -> WHITE.copy(alpha = 0.5f)
        glass -> WHITE.copy(alpha = 0x38 / 255f)
        look.cinematic -> WHITE.copy(alpha = 0x26 / 255f)
        else -> WHITE.copy(alpha = 0x1F / 255f)
    }
    val h = g.h + 2 * padY + if (look.names) NAMES_ROOM else 0f
    Box(
        Modifier.offset(tv.u(x), tv.u(g.dockTop - padY)).size(tv.u(width), tv.u(h))
            .background(fill, shape).border(tv.u(1f).coerceAtLeast(0.5.dp), edge, shape),
    )
}

// A plain card with the app's name (never a logo). Focused: the launcher's glass highlight, scaled
// by the movement, with its ring and soft glow, raised 4 dp with a shadow with Lift and zoom. The
// name under it: 14 sp, 7 dp under the tile (4 dp in the plain grid), a little lower when focused,
// at 75% when every tile has its name.
// Home screen 3.0: with icon tiles (uniform_tiles, AppAdapter.java at b06ab21) the card is the
// app's colour top to bottom (backdrop, AppAdapter.java:714-725) with the icon centred at 62% of the
// tile's height (ICON_SHARE, AppAdapter.java:421-427, 669) and no name inside it (AppAdapter.java:491);
// the name under it keeps its own rule (AppAdapter.java:463-466): none in the dock or a single row
// unless every app has its name (the big name names the app you are on), under the focused tile
// in the grid. The phone has no icons, so the icon is a light rounded square with the app's first
// letter. The highlight
// (glass_style, GlassView.java at b06ab21): glass as 2.7 draws it; rim only a 2 dp edge, no glow,
// panel or shine (GlassView.java:66-68, 104-106); halo the same edge with the glow at half its
// strength (GlassView.java:92-95). The edge is white at 80%, or the ring's colour two thirds of the
// way to white at 90% (GlassView.java:48-60). `colour`: the app's colour, for its icon card.
@Composable
private fun Tile(name: String, focused: Boolean, look: TvLook, ring: Color, x: Float, y: Float, g: TvLayout, tv: Tv, label: Boolean, compact: Boolean,
    colour: Color? = null) {
    val w = g.w
    val h = g.h
    val shape = RoundedCornerShape(tv.u(g.corner))
    val scale = if (focused) look.focusScale else 1f
    val raise = if (focused && look.lift) 4f else 0f
    val icons = look.iconTiles && !look.crop
    val card = if (icons) iconCard(colour) else null
    val plain: Modifier = if (card != null) Modifier.background(Brush.verticalGradient(listOf(card.first, card.second)), shape) else Modifier.background(Color(0xFF1C1C1E), shape)
    val glow = when (look.highlight) { HighlightChoice.GLASS -> 1f; HighlightChoice.HALO -> 0.5f; HighlightChoice.RIM -> 0f }
    Box(
        Modifier.offset(tv.u(x), tv.u(y - raise)).size(tv.u(w), tv.u(h))
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focused && !look.crop) Modifier.drawBehind { focusGlow(ring, g.corner * tv.s * density, tv.s * density, look.lift, glow) } else Modifier)
            .then(
                when {
                    look.crop -> Modifier.border(tv.u(if (focused) 3f else 1f), WHITE.copy(alpha = if (focused) 0.9f else 0.4f), shape)
                    focused && look.highlight != HighlightChoice.GLASS -> plain.border(tv.u(2f), rimColour(ring), shape)
                    focused && card != null -> plain.background(Brush.verticalGradient(listOf(WHITE.copy(alpha = 0x3D / 255f), WHITE.copy(alpha = 0x12 / 255f))), shape)
                        .border(tv.u(2.5f), ring.copy(alpha = 0.95f), shape)
                    focused -> Modifier.background(Brush.verticalGradient(0f to Color(0xFF606066), 0.55f to Color(0xFF2A2A2E), 1f to Color(0xFF1C1C1F)), shape)
                        .border(tv.u(2.5f), ring.copy(alpha = 0.95f), shape)
                    else -> plain
                },
            )
            .then(if (icons) Modifier else Modifier.padding(horizontal = tv.u(6f))),
        contentAlignment = Alignment.Center,
    ) {
        if (card != null) {
            val icon = (h * ICON_SHARE).roundToInt().toFloat()
            Box(Modifier.size(tv.u(icon)).background(WHITE.copy(alpha = 0.92f), RoundedCornerShape(tv.u(icon * 0.22f))), contentAlignment = Alignment.Center) {
                Text(name.take(1).uppercase(), maxLines = 1, style = tv.text(icon * 0.5f, FontWeight.Medium, card.first))
            }
        } else if (!look.crop) {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                style = tv.text(15f, color = WHITE.copy(alpha = if (focused) 1f else 0.8f)))
        }
    }
    if (label) {
        // item_app.xml's name: a 22 dp line tileH + 7 dp down (4 dp in the plain grid), moved up 6 dp
        // at rest and down 4 dp on the focused tile.
        val width = min(180f, g.cell)
        val top = y + h + (if (compact) 4f else 7f) + if (focused) 4f else -6f
        Box(
            Modifier.offset(tv.u(x + w / 2f - width / 2f), tv.u(top)).size(tv.u(width), tv.u(22f))
                .graphicsLayer { alpha = (if (focused) 1f else 0.75f) * (if (look.crop) 0.6f else 1f) },
            contentAlignment = Alignment.Center,
        ) {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                style = tv.text(14f, FontWeight.Medium, WHITE.copy(alpha = 0xF2 / 255f), em = 0.02f))
        }
    }
}

// The highlight's soft glow around the tile (stronger in a colour than in white) and, with Lift and
// zoom, the shadow under the raised tile. `corner` and `unit` are in pixels (unit: one TV dp).
private fun DrawScope.focusGlow(ring: Color, corner: Float, unit: Float, lift: Boolean, glow: Float = 1f) {
    if (lift) {
        for (i in 1..6) {
            val grow = i * 2.2f * unit
            drawRoundRect(Color.Black.copy(alpha = 0.10f), topLeft = Offset(-grow, 16f * unit - grow), size = Size(size.width + 2 * grow, size.height + 2 * grow),
                cornerRadius = CornerRadius(corner + grow))
        }
    }
    if (glow <= 0f) return
    val strength = (if (ring == WHITE) 0.28f else 0.6f) * glow
    val steps = 6
    for (i in 1..steps) {
        val grow = i * 3f * unit
        drawRoundRect(ring.copy(alpha = strength * (1f - i / (steps + 1f)) / 2f), topLeft = Offset(-grow, -grow), size = Size(size.width + 2 * grow, size.height + 2 * grow),
            cornerRadius = CornerRadius(corner + grow), style = Stroke(width = max(1f, 3f * unit)))
    }
}

// The icon's share of the tile's height (AppAdapter.ICON_SHARE at b06ab21).
private const val ICON_SHARE = 0.62f

// The rim of rim and halo (GlassView.java:48-60 at b06ab21): white at 0xCC, or a colour two thirds
// of the way to white at 0xE6.
private fun rimColour(ring: Color): Color =
    if (ring == WHITE) WHITE.copy(alpha = 0xCC / 255f) else lerp(ring, WHITE, 2f / 3f).copy(alpha = 0xE6 / 255f)

// An icon card's two colours, top and bottom (AppAdapter.backdrop, AppAdapter.java:714-725 at
// b06ab21): the app's colour with a yellow turned amber (hue 42 to 70 with some colour goes to 36),
// its value at most 0.62, the bottom at 60% of that. An app with no colour of its own (the phone's
// neutral grey, or none) is the launcher's grey #6E6E73.
private fun iconCard(colour: Color?): Pair<Color, Color> {
    val c = colour?.takeUnless { it == Color(AppColours.NEUTRAL) } ?: Color(0xFF6E6E73)
    val r = c.red
    val gr = c.green
    val b = c.blue
    val mx = max(r, max(gr, b))
    val mn = min(r, min(gr, b))
    val d = mx - mn
    var hue = when {
        d == 0f -> 0f
        mx == r -> 60f * (((gr - b) / d) % 6f)
        mx == gr -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - gr) / d + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val sat = if (mx == 0f) 0f else d / mx
    if (hue in 42f..70f && sat > 0.3f) hue = 36f
    val v = min(mx, 0.62f)
    return Color.hsv(hue, sat, v) to Color.hsv(hue, sat, v * 0.6f)
}

// The header's icons: launcher 2.7's own vectors (ic_input, ic_wifi, ic_settings), 24 x 24, white, tinted.
private object TvIcons {
    private fun icon(name: String, d: String): ImageVector =
        ImageVector.Builder(name, 22.dp, 22.dp, 24f, 24f).addPath(PathParser().parsePathString(d).toNodes(), fill = SolidColor(WHITE)).build()

    val wifi = icon(
        "wifi",
        "M1,9l2,2c4.97,-4.97 13.03,-4.97 18,0l2,-2C16.93,2.93 7.08,2.93 1,9zM9,17l3,3 3,-3c-1.65,-1.66 -4.34,-1.66 -6,0zM5,13l2,2c2.76,-2.76 7.24,-2.76 " +
            "10,0l2,-2C15.14,9.14 8.87,9.14 5,13z",
    )
    val gear = icon(
        "gear",
        "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32" +
            "c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84" +
            "c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87" +
            "C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61" +
            "l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84" +
            "c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32" +
            "c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z",
    )
    val input = icon(
        "input",
        "M18,7V4c0,-1.1 -0.9,-2 -2,-2H8C6.9,2 6,2.9 6,4v3H5v6l3,6v3h8v-3l3,-6V7H18zM8,4h8v3h-2V5h-1v2h-2V5h-1v2H8V4z",
    )
}

// The festival's pill icons, still and simplified from the launcher's own (DiyaDrawable,
// BellDrawable, MarigoldDrawable), in their 24 x 24 box: a clay diya with its flame at the spout, a
// gold bell, a marigold of orange petals.
private val CLAY_EDGE = Color(0xFF3D1408)

private fun DrawScope.diya() {
    drawCircle(Brush.radialGradient(listOf(Color(0x99FFC45A), Color(0x00FF8A1A)), center = Offset(18.5f, 9f), radius = 6f), radius = 6f, center = Offset(18.5f, 9f))
    drawRoundRect(Color(0xFF8E3E1E), topLeft = Offset(7.2f, 20f), size = Size(5.2f, 2.4f), cornerRadius = CornerRadius(1f))
    val body = Path().apply {
        moveTo(1.6f, 14.2f)
        cubicTo(1.8f, 18.6f, 5.4f, 21.0f, 9.6f, 21.0f)
        cubicTo(13.2f, 21.0f, 15.6f, 19.4f, 16.8f, 16.6f)
        cubicTo(17.6f, 15.0f, 18.6f, 13.6f, 20.2f, 12.4f)
        quadraticTo(20.5f, 12.0f, 19.9f, 11.9f)
        cubicTo(18.2f, 12.1f, 16.8f, 12.6f, 15.4f, 13.0f)
        cubicTo(12.0f, 11.6f, 5.0f, 11.8f, 1.6f, 14.2f)
        close()
    }
    drawPath(body, Brush.radialGradient(0f to Color(0xFFE58C57), 0.35f to Color(0xFFC4612F), 0.7f to Color(0xFF9A4220), 1f to Color(0xFF6E2C14),
        center = Offset(7.5f, 14.5f), radius = 13f))
    drawPath(body, CLAY_EDGE, style = Stroke(0.7f))
    val opening = Path().apply {
        moveTo(2.6f, 14.2f)
        cubicTo(5.6f, 12.6f, 11.8f, 12.5f, 15.2f, 13.4f)
        cubicTo(16.6f, 13.0f, 17.8f, 12.7f, 19.2f, 12.4f)
        cubicTo(17.6f, 13.4f, 16.6f, 14.0f, 15.4f, 14.6f)
        cubicTo(11.6f, 16.4f, 5.0f, 16.2f, 2.6f, 14.2f)
        close()
    }
    drawPath(opening, Color(0xFF4A1E0E))
    for (i in 0 until 5) drawCircle(Color(0xFFFFE0A3), radius = 0.55f, center = Offset(5.0f + i * 2.4f, 17.6f + if (i in 1..3) 0.9f else 0f))
    // The flame: a teardrop standing on the wick.
    val flame = Path().apply {
        moveTo(18.5f, 12.1f)
        cubicTo(16.3f, 11.6f, 16.6f, 8.0f, 18.5f, 3.9f)
        cubicTo(20.4f, 8.0f, 20.7f, 11.6f, 18.5f, 12.1f)
        close()
    }
    drawPath(flame, Brush.verticalGradient(0f to Color(0xFFD9430B), 0.3f to Color(0xFFFF7A14), 0.65f to Color(0xFFFFA62B), 1f to Color(0xFFF08A24), startY = 3.9f, endY = 12.1f))
    drawOval(Color(0xFFFFE9A6), topLeft = Offset(17.9f, 8.4f), size = Size(1.2f, 3f))
}

private fun DrawScope.bell() {
    val gold = Brush.horizontalGradient(0f to Color(0xFFB07A14), 0.45f to Color(0xFFFFD45A), 1f to Color(0xFF9A6410), startX = 4f, endX = 20f)
    drawRoundRect(Color(0xFF8A5A10), topLeft = Offset(10.6f, 2f), size = Size(2.8f, 3f), cornerRadius = CornerRadius(1.2f))
    val shell = Path().apply {
        moveTo(12f, 4.2f)
        cubicTo(7.6f, 4.2f, 6.6f, 8.6f, 6.4f, 12.6f)
        cubicTo(6.2f, 15.6f, 5.2f, 17.0f, 3.8f, 18.2f)
        lineTo(20.2f, 18.2f)
        cubicTo(18.8f, 17.0f, 17.8f, 15.6f, 17.6f, 12.6f)
        cubicTo(17.4f, 8.6f, 16.4f, 4.2f, 12f, 4.2f)
        close()
    }
    drawPath(shell, gold)
    drawPath(shell, Color(0xFF5A3A06), style = Stroke(0.7f))
    drawRoundRect(Color(0xFFE0A52C), topLeft = Offset(3.4f, 17.6f), size = Size(17.2f, 1.8f), cornerRadius = CornerRadius(0.9f))
    drawCircle(Color(0xFF7A4C08), radius = 1.7f, center = Offset(12f, 20.8f))
}

private fun DrawScope.marigold() {
    val c = Offset(12f, 12f)
    fun ring(count: Int, r: Float, len: Float, wide: Float, fill: Color, turn: Float) {
        for (i in 0 until count) {
            val a = (i + turn) * 2.0 * Math.PI / count
            val x = c.x + (r * kotlin.math.cos(a)).toFloat()
            val y = c.y + (r * kotlin.math.sin(a)).toFloat()
            drawCircle(fill, radius = (len + wide) / 2f, center = Offset(x, y))
        }
    }
    ring(12, 7.4f, 4.4f, 3.6f, Color(0xFFE5600A), 0f)
    ring(10, 5.0f, 3.8f, 3.2f, Color(0xFFF79A16), 0.5f)
    ring(8, 2.8f, 3.2f, 2.6f, Color(0xFFFFC63E), 0.25f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFE0821A), Color(0xFF7A3004)), center = c, radius = 2.4f), radius = 2.4f, center = c)
}
