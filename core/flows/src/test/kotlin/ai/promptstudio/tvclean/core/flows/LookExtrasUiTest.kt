package ai.promptstudio.tvclean.core.flows

import ai.promptstudio.tvclean.core.model.PackageName
import ai.promptstudio.tvclean.core.spec.BandChoice
import ai.promptstudio.tvclean.core.spec.BundledThemeSource
import ai.promptstudio.tvclean.core.spec.ButtonsChoice
import ai.promptstudio.tvclean.core.spec.HighlightChoice
import ai.promptstudio.tvclean.core.spec.LookExtras
import ai.promptstudio.tvclean.core.spec.Movement
import ai.promptstudio.tvclean.core.spec.StyleId
import ai.promptstudio.tvclean.core.spec.TilesChoice
import ai.promptstudio.tvclean.testing.fixtures.FakeClock
import ai.promptstudio.tvclean.testing.fixtures.Samples
import kotlin.test.Test
import kotlin.test.assertEquals

// 0.4.5 (plan WP6): what the Look UI reads of the 3.0 switches. actingExtras is LookRules.extras of
// the draft (the rows show it and the summary counts it); offeredExtras is what would act with every
// switch on, so the UI hides or greys a row whose key cannot act in this look. Below 3.0, nothing.
class LookExtrasUiTest {
    private val looks = BundledThemeSource()
    private val drafts = LookDrafts(looks, looks, ConfigEncoder.Codec, FakeClock(Samples.NOW_MS))
    private val apps = List(4) { AppChoice(PackageName("com.example.app$it"), "App $it") }
    private val all = LookExtras(true, true, true, true, true, true, true, TilesChoice.ICONS, HighlightChoice.RIM)

    private fun open(style: StyleId, extras: LookExtras, tv: LookTv = LookTv(9L, 2048, true), details: LookDetails = LookDetails()) =
        drafts.open(HomeChoice(emptyList(), null, picture = "mist", columns = 5, style = style.id, movement = Movement.LIVELY.id, lift = false,
            names = false, details = details, extras = extras), LookContext(apps, tv = tv))

    private fun rules(d: LookDraft, extras: LookExtras, tv: LookTv = LookTv(9L, 2048, true)): LookExtras {
        val p = LookParts(d.style, d.picture ?: LookPicture.NONE_ID, d.columns, details = d.details, extras = extras)
        return LookRules.extras(p, LookRules.rows(p, drafts.pictureOf(p.picture, LookContext(apps, tv = tv)), apps.size, tv), tv)
    }

    @Test fun actingAndOfferedAreTheRulesOnEveryStyle() {
        for (style in StyleId.entries) for (details in listOf(LookDetails(), LookDetails(greenWifi = true), LookDetails(greenWifi = true, buttons = ButtonsChoice.MENU))) {
            val d = open(style, all, details = details)
            assertEquals(rules(d, all), d.actingExtras, "$style $details acting")
            assertEquals(rules(d, all), d.offeredExtras, "$style $details offered")
            val none = open(style, LookExtras.DEFAULT, details = details)
            assertEquals(LookExtras.DEFAULT, none.actingExtras, "$style nothing on")
            assertEquals(d.offeredExtras, none.offeredExtras, "$style offered does not depend on the choice")
        }
    }

    @Test fun whereEachSwitchActs() {
        assertEquals(false, open(StyleId.ALL_APPS, all).offeredExtras.oneRow)
        assertEquals(true, open(StyleId.SPOTLIGHT, all).offeredExtras.oneRow)
        assertEquals(false, open(StyleId.SPOTLIGHT, all).offeredExtras.noBand)
        assertEquals(true, open(StyleId.CLASSIC, all).offeredExtras.noBand)
        assertEquals(false, open(StyleId.CLASSIC, all).offeredExtras.wifiWarnings)
        assertEquals(true, open(StyleId.CLASSIC, all, details = LookDetails(greenWifi = true)).offeredExtras.wifiWarnings)
    }

    @Test fun belowThirtyNothingActsOrIsOffered() {
        val d = open(StyleId.CLASSIC, all, tv = LookTv(8L, 2048, true))
        assertEquals(LookExtras.DEFAULT, d.actingExtras)
        assertEquals(LookExtras.DEFAULT, d.offeredExtras)
    }

    // Choosing a band while None is on is one edit: the band, and the band back on.
    @Test fun aBandAfterNoneIsOneEdit() {
        val tv = LookTv(9L, 2048, true)
        val d = open(StyleId.CLASSIC, LookExtras(noBand = true))
        assertEquals(true, d.actingExtras.noBand)
        val e = drafts.edit(d, LookEdit.SetBandBack(BandChoice.GLASS), LookContext(apps, tv = tv))
        assertEquals(false, e.extras.noBand)
        assertEquals(BandChoice.GLASS, e.details.band)
        // Spotlight keeps its own band: nothing changes.
        val s = open(StyleId.SPOTLIGHT, LookExtras(noBand = true))
        assertEquals(s, drafts.edit(s, LookEdit.SetBandBack(BandChoice.GLASS), LookContext(apps, tv = tv)))
    }
}
