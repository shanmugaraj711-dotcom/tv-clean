package ai.promptstudio.tvclean.core.spec

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// The codec's launcher 3.0 rules (plan WP5 steps 9 and 10): the keys that act only with another key
// on, glass_style, and the app_keys grammar of RemoteKeys at b06ab21.
class Launcher30CodecTest {
    private fun warnings(json: String): List<String> =
        assertIs<DecodeResult.Ok>(LauncherConfigCodec.decode(json.encodeToByteArray()), json).warnings

    @Test fun aKeyWithoutTheKeyItNeedsIsWarnedAbout() {
        for ((json, want) in listOf(
            """{"dock_row": false, "single_row": true}""" to "single_row does nothing without dock_row: the launcher keeps its grid",
            """{"dock_band_all": true}""" to "dock_band_all does nothing without single_row: the band stays behind the dock apps",
            """{"show_library_pill": false}""" to "show_library_pill does nothing without app_library: there is no App library to show",
            """{"library_ok_opens": true, "app_library": false}""" to "library_ok_opens does nothing without app_library: there is no App library to open apps from",
            """{"wifi_status_warnings": true}""" to "wifi_status_warnings does nothing without wifi_status_color: the Wi-Fi icon stays white",
            """{"lunar_utc_offset": 7}""" to "lunar_utc_offset does nothing without lunar_date: the TV shows no lunar date",
            """{"lunar_utc_offset": 0, "lunar_date": false}""" to "lunar_utc_offset does nothing without lunar_date: the TV shows no lunar date",
            """{"lunar_utc_offset": 14}""" to "lunar_utc_offset does nothing without lunar_date: the TV shows no lunar date",
            """{"glass_style": "frost"}""" to "glass_style is not glass, rim or halo; the launcher uses glass",
            """{"glass_style": 2}""" to "glass_style is not a string; the launcher uses glass",
        )) {
            assertEquals(listOf(want), warnings(json), json)
        }
        assertEquals(listOf("single_row does nothing without dock_row: the launcher keeps its grid",
            "dock_band_all does nothing without single_row: the band stays behind the dock apps"),
            warnings("""{"dock_row": false, "single_row": true, "dock_band_all": true}"""))
        assertEquals(listOf("consume_volume_keys does nothing with key_filter false: the key helpers do not see the volume keys"),
            warnings("""{"key_filter": false, "consume_volume_keys": true}"""))
        assertEquals(listOf("home_long_press_home does nothing with key_filter false: the key helper does not see the Home key"),
            warnings("""{"key_filter": false, "home_long_press_home": true}"""))
    }

    // A value at the launcher's own (lunar_utc_offset -1, the locale's own offset, or one Config.integer
    // gives -1 for: out of -1 to 14, or not a number) never warns.
    @Test fun aKeyWithWhatItNeedsOrAtItsDefaultIsNot() {
        for (json in listOf(
            """{"single_row": true}""", """{"single_row": true, "dock_band_all": true}""", """{"dock_row": false, "single_row": false}""",
            """{"dock_band_all": false}""", """{"show_library_pill": true}""", """{"show_library_pill": false, "app_library": true}""",
            """{"library_ok_opens": true, "app_library": true}""", """{"wifi_status_warnings": true, "wifi_status_color": true}""",
            """{"wifi_status_warnings": false}""", """{"consume_volume_keys": true}""", """{"home_long_press_home": true}""",
            """{"lunar_date": true, "lunar_utc_offset": 7}""", """{"lunar_utc_offset": -1}""", """{"lunar_utc_offset": -1.0}""",
            """{"lunar_utc_offset": 15}""", """{"lunar_utc_offset": "7"}""", """{"glass_style": "rim"}""", """{"glass_style": " Halo "}""",
            """{"glass_style": "glass"}""", """{"glass_style": "GLASS"}""", """{"dock_band": false, "show_greeting": false, "date_below_clock": true,
                "header_white": true, "uniform_tiles": true}""",
        )) {
            assertEquals(emptyList(), warnings(json), json)
        }
    }

    @Test fun appKeysGrammar() {
        val good = listOf(
            "190:com.google.android.youtube.tv", "KEYCODE_BUTTON_3:com.google.android.youtube.tv", "KEYCODE_TCL_NETFLIX:com.netflix.ninja",
            "scan:490:com.wbd.hbomax", "hold:KEYCODE_BUTTON_8:org.xbmc.kodi", "hold:scan:490:com.wbd.hbomax", "front:191:com.netflix.ninja",
            "front:scan:485:com.google.android.youtube.tv", "4001:input:com.tcl.tvinput/.TvPassThroughService/HW15", "scan:365:inputs",
            "178:inputs", "183:back", "184:home", "185:recents", "164:mute", "223:sleep", "186:screensaver", "176:settings", "187:wifi",
            "172:guide:com.seena.app", "191:com.netflix.ninja/.MainActivity", "191:com.example.addon/com.example.addon.QuickBar",
            " 190 : com.google.android.youtube.tv ",
        )
        for (e in good) assertEquals(emptyList(), warnings("""{"app_keys": ["$e"]}"""), e)
        assertEquals(ConfigCodecImpl.AppKey("hold:", null, 490, "com.wbd.hbomax"), ConfigCodecImpl.appKeyEntry("hold:scan:490:com.wbd.hbomax"))
        assertEquals(ConfigCodecImpl.AppKey(null, 4001, null, "input:com.tcl.tvinput/.TvPassThroughService/HW15"),
            ConfigCodecImpl.appKeyEntry("4001:input:com.tcl.tvinput/.TvPassThroughService/HW15"))
        assertEquals(-1, ConfigCodecImpl.appKeyEntry("KEYCODE_BUTTON_3:x.y")?.keycode, "Android resolves the other names")
        for (skipped in listOf("youtube", "190", "190:", "0:com.a.b", "scan:0:com.a.b", "scan:x:com.a.b", "scan:490:", "hold:", "KEYCODE_x:com.a.b",
            "hold:front:190:com.a.b", "1234567:com.a.b")) {
            assertNull(ConfigCodecImpl.appKeyEntry(skipped), skipped)
            assertEquals(listOf("app_keys[0] is not keycode:target (190:com.google.android.youtube.tv) or scan:code:target; the launcher skips it"),
                warnings("""{"app_keys": ["$skipped"]}"""), skipped)
        }
        for (bad in listOf("190:youtube", "190:input:", "190:guide:", "190:guide:seena", "190:com.a.b/", "190:/.Main", "190:com.a.b/.", "190:Back")) {
            val k = assertNotNull(ConfigCodecImpl.appKeyEntry(bad), bad)
            assertEquals(listOf("app_keys[0] names ${k.target}, which is not an app, an input or a remote action the launcher knows; that button would do nothing"),
                warnings("""{"app_keys": ["$bad"]}"""), bad)
        }
        // The navigation key warning stays, for every way of naming the button; a scancode is not a keycode.
        for (e in listOf("4:com.a.b", "KEYCODE_DPAD_CENTER:com.a.b", "hold:23:com.a.b", "front:KEYCODE_BACK:home")) {
            assertEquals(1, warnings("""{"app_keys": ["$e"]}""").count { it.startsWith("app_keys takes remote buttons every app needs") }, e)
        }
        assertEquals(emptyList(), warnings("""{"app_keys": ["scan:4:com.a.b"]}"""))
    }
}
