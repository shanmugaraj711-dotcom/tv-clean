package ai.promptstudio.tvclean.core.spec

import ai.promptstudio.tvclean.core.model.PackageName

// The colour the phone's preview gives an app's glow and ring. The TV takes it from the app's own
// banner (AppAdapter.ambientColorAt), which the phone does not have, so the preview uses this small
// table of well known TV apps, by package, in each app's own brand colour, and a neutral grey for
// any other app. A colour here is always opaque.
object AppColours {
    const val NEUTRAL = 0xFF9AA0A6.toInt()

    private val BY_PACKAGE: Map<String, Int> = listOf(
        "com.netflix.ninja" to 0xE50914,
        "com.netflix.mediaclient" to 0xE50914,
        "com.google.android.youtube.tv" to 0xFF1F1F,
        "com.google.android.youtube.tvkids" to 0xFF3D3D,
        "com.google.android.youtube.tvmusic" to 0xFF0033,
        "com.liskovsoft.smarttubetv.beta" to 0xE53935,
        "com.amazon.amazonvideo.livingroom" to 0x00A8E1,
        "com.amazon.avod.thirdpartyclient" to 0x00A8E1,
        "in.startv.hotstar" to 0x2B5CFF,
        "in.startv.hotstartvonly" to 0x2B5CFF,
        "com.jio.media.stb.ondemand" to 0xD9008D,
        "com.jio.media.ondemand" to 0xD9008D,
        "com.sonyliv" to 0xF5A623,
        "com.graymatrix.did" to 0x8230C6,
        "com.spotify.tv.android" to 0x1DB954,
        "com.jio.media.jiobeats" to 0x2BC5B4,
        "com.gaana" to 0xE72C30,
        "com.plexapp.android" to 0xE5A00D,
        "org.videolan.vlc" to 0xFF8800,
        "org.xbmc.kodi" to 0x17B2E7,
        "org.jellyfin.androidtv" to 0xAA5CC3,
        "com.disney.disneyplus" to 0x113CCF,
        "tv.twitch.android.app" to 0x9146FF,
        "com.mxtech.videoplayer.television" to 0x0A7CFF,
        "com.mxtech.videoplayer.ad" to 0x0A7CFF,
        "tv.accedo.airtel.wynk" to 0xE40000,
        "com.suntv.sunnxt" to 0xF36F21,
    ).associate { (pkg, rgb) -> pkg to (0xFF000000.toInt() or rgb) }

    // The app's colour, or NEUTRAL for an app the table does not know (or no app at all).
    fun of(pkg: PackageName?): Int = of(pkg?.value)

    fun of(pkg: String?): Int = pkg?.let { BY_PACKAGE[it] } ?: NEUTRAL

    // The packages the table knows, for tests.
    val known: Set<String> get() = BY_PACKAGE.keys
}
