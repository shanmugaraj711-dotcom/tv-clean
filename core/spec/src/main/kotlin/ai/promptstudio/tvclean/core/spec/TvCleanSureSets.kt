// TV CLEAN 0.2 — brand sure sets for the Indian TV brands the upstream lists do not cover.
//
// The upstream examples (third_party/cleanyourtv, a submodule that is empty in this fork) have
// no profile for Realme, Acer, Nokia, Infinix, Kodak, CloudWalker, Blaupunkt, Vu (by brand name)
// or iFFALCON (by brand name: its one upstream list is filed under the manufacturer "FFalcon").
// Samsung and LG are deliberately NOT here: they run Tizen and webOS, not Android TV, so no ADB
// clean-up applies to them at all.
//
// A TV of one of these brands with no exact profile would otherwise resolve to
// ProfileResolution.None (no clean-up rows at all). These careful sets give it the same safe
// clean-up a brand with one or two lists gets (D26 amendment): ONLY the six packages the owner
// reviewed for the careful rule (SureSetGenerator.CAREFUL_PACKAGES), in default-disable groups,
// with the generic stock launchers — so the home-screen decision is the one a TV with no set gets.
//
// The resolver checks the bundle's derived sets first (BundledProfileSource.resolveNow), so the
// moment a real upstream profile for one of these brands lands, its derived set wins and this
// entry is never used. These entries exist only until then; they are not a second source of truth,
// they are the fallback the derived sets cannot produce while the brand has no profile.

package ai.promptstudio.tvclean.core.spec

import ai.promptstudio.tvclean.core.model.GroupDefault
import ai.promptstudio.tvclean.core.model.PackageEntry
import ai.promptstudio.tvclean.core.model.PackageGroup
import ai.promptstudio.tvclean.core.model.PackageName

object TvCleanSureSets {
    // The six packages the D26 careful rule allows, filed under the group the facts.tsv
    // category puts them in: five tracking, one preloads.
    private fun tracking(vararg names: String) = PackageGroup(
        ProfileCategory.TRACKING.groupId(ask = false),
        ProfileCategory.TRACKING.title(ask = false, brand = ""),
        GroupDefault.DISABLE,
        names.map { PackageEntry(PackageName(it), null) },
    )

    private fun preloads(vararg names: String) = PackageGroup(
        ProfileCategory.PRELOADS.groupId(ask = false),
        ProfileCategory.PRELOADS.title(ask = false, brand = ""),
        GroupDefault.DISABLE,
        names.map { PackageEntry(PackageName(it), null) },
    )

    private fun carefulSet(brand: String): SureSet = SureSet(
        brand = brand,
        // No exact profile backs these sets (that is the point: the brand has none). The empty
        // list says so plainly, instead of naming a profile that does not exist.
        profileIds = emptyList(),
        groups = listOf(
            tracking(
                "com.android.adservices.api",
                "com.android.federatedcompute.services",
                "com.android.ondevicepersonalization.services",
                "com.android.tv.feedbackconsent",
                "com.google.android.feedback",
            ),
            preloads("com.android.printspooler"),
        ),
        stockLaunchers = KnownLaunchers.generic,
    )

    // Indian Android TV brands with no upstream profile (by the brand name a TV reports).
    // Keep the names exactly as the TV's Build.BRAND reports them; the match is case-insensitive.
    val extra: List<SureSet> = listOf(
        "Realme",
        "Acer",
        "Nokia",
        "Infinix",
        "Kodak",
        "CloudWalker",
        "Blaupunkt",
        // Vu and iFFALCON have upstream lists, but filed under the manufacturer (HVCL / FFalcon),
        // so a TV that reports the brand name on the box resolves to None. These entries cover that.
        "Vu",
        "iFFALCON",
    ).map(::carefulSet)

    init {
        // Guard: these sets hold only the six reviewed packages, the same rule the derived
        // careful sets pass (ProfilesTest.carefulSetsForBrandsWithOneOrTwoLists).
        val six = SureSetGenerator.CAREFUL_PACKAGES
        require(extra.flatMap { s -> s.groups.flatMap { g -> g.packages.map { it.name } } }.toSet() == six) {
            "TvCleanSureSets must hold exactly the six careful packages"
        }
    }
}
