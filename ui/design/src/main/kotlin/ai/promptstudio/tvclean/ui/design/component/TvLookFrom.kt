package ai.promptstudio.tvclean.ui.design.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import java.time.format.DateTimeFormatter
import java.util.Locale
import ai.promptstudio.tvclean.core.flows.LookPreview
import ai.promptstudio.tvclean.core.flows.Sky
import ai.promptstudio.tvclean.core.spec.Movement

// The one mapping from the bytes' preview (LookPreview) to the drawing (TvLook). `picture`: the
// wallpaper as an image on this phone, when the TV shows one. `crop`: drawn over the photo crop,
// so the glow and Spotlight's shade are at full strength and the chrome is faint (spec 9).
// The launcher's numbers: focus scale 1.12 with Lift and zoom, 1.10 otherwise, none with Off; the
// festival's accent replaces the app's colour for the ring and the pills; the sky is Sky.at at the
// clock's minute; the clock is 12 hour like the TV's, the date in the phone's language.
fun LookPreview.toTvLook(picture: ImageBitmap?, crop: Boolean = false): TvLook {
    val p = Sky.at(minuteOfDay)
    val hour = (minuteOfDay / 60).coerceIn(0, 23)
    val h12 = (hour + 11) % 12 + 1
    return TvLook(
        background = Color(backgroundArgb), picture = picture, dim = dim, sky = sky,
        skyTop = Color(p.top), skyBottom = Color(p.bottom), skyMiddle = Color(p.mid),
        sunGlow = p.sun / 255f, sunX = p.sunX,
        columns = columns, rowsBelowDock = rowsBelowDock, dockRow = dockRow, dockTitle = dockTitle, cinematic = cinematic,
        accent = if (festive) Color(accentArgb) else null, greeting = greeting,
        focusScale = when {
            movement == Movement.OFF -> 1f
            lift -> 1.12f
            else -> 1.10f
        },
        dock = dock, clock = "$h12:${(minuteOfDay % 60).coerceIn(0, 59).toString().padStart(2, '0')}",
        emptyDockSlots = !fillDock,
        names = names, glow = glow, glowColour = Color(focusArgb), ring = ring, band = band, bandOpacity = bandOpacity,
        evenPadding = evenPadding, buttons = buttons, roundInputs = roundInputs, wifiGreen = wifiGreen, inputs = inputs,
        dots = dots, scrim = scrim, art = art, lift = lift, crop = crop, others = others,
        date = today.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())).uppercase(Locale.getDefault()),
        minuteOfDay = minuteOfDay,
        oneRow = oneRow, bandAll = bandAll, greetingShown = greetingShown, dateBelow = dateBelow, brightDate = brightDate,
        iconTiles = iconTiles, highlight = highlight, dockColours = dockArgb.map { Color(it) }, otherColours = othersArgb.map { Color(it) },
    )
}
