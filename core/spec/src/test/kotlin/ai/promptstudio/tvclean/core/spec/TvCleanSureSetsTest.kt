// TV CLEAN 0.2 — the brand sure sets for the Indian TV brands the upstream lists do not cover.
//
// These tests run against an EMPTY bundle on purpose: the upstream examples (the submodule) and
// the generated profiles.json are not in this fork, so the bundle cannot be built here. An empty
// bundle proves the new path exactly — with no derived set anywhere, only a TV Clean set can
// resolve a TV, and a brand we do not list resolves to None.
package ai.promptstudio.tvclean.core.spec

import kotlinx.coroutines.runBlocking
import org.junit.Test
import ai.promptstudio.tvclean.core.model.DeviceFacts
import ai.promptstudio.tvclean.core.model.GroupDefault
import ai.promptstudio.tvclean.core.model.PackageGroup
import ai.promptstudio.tvclean.core.model.PackageName
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TvCleanSureSetsTest {
    private val emptyBundle = ProfileBundle(null, emptyList(), emptyList())
    private val source = BundledProfileSource(emptyBundle)

    private fun facts(brand: String) = DeviceFacts(
        brand = brand, manufacturer = brand, model = "Unknown Model", device = "unknown-device",
        serialLast4 = "0000", sdk = 30, release = "11", fingerprint = "$brand/unknown/unknown:x/X/1:user/release-keys",
        abis = listOf("armeabi-v7a"), ramMb = 2048, hasGms = true, adbSecure = true,
        adbFeatures = setOf("shell_v2"), installRefusal = false, locale = "en",
    )

    // Every brand we list resolves to its careful set, and the set holds only the six reviewed
    // packages, switched off by default, with the generic stock launchers.
    @Test fun indianBrandsWithoutAProfileResolveToACarefulSet() = runBlocking<Unit> {
        val six = SureSetGenerator.CAREFUL_PACKAGES
        for (brand in TvCleanSureSets.extra.map { it.brand }) {
            val r = source.resolve(facts(brand))
            assertIs<ProfileResolution.BrandSureSet>(r, "a $brand TV should resolve to a sure set")
            assertEquals(brand, r.brand)
            val pkgs = r.sureSet.flatMap { g -> g.packages.map { it.name } }.toSet()
            assertEquals(six, pkgs, "a $brand sure set holds exactly the six careful packages")
            assertTrue(r.sureSet.all { it.default == GroupDefault.DISABLE }, "a $brand sure set is default-off")
            assertEquals(KnownLaunchers.generic, r.stockLaunchers)
        }
        // The six land in the two groups their facts.tsv category puts them in.
        val realme = source.resolve(facts("Realme")) as ProfileResolution.BrandSureSet
        assertEquals(
            mapOf("tracking" to 5, "preloads" to 1),
            realme.sureSet.associate { it.id to it.packages.size },
        )
    }

    // A brand we do not list still resolves to None — these sets add coverage, they do not claim
    // every unknown TV.
    @Test fun anUnlistedBrandStillResolvesToNone() = runBlocking<Unit> {
        assertIs<ProfileResolution.None>(source.resolve(facts("SomeBrandNobodyListed")))
    }

    // The bundle's derived set wins over a TV Clean set when both match a TV, so a real profile
    // landing later takes over from the fallback automatically.
    @Test fun aDerivedSetWinsOverATvCleanSet() = runBlocking<Unit> {
        val derived = SureSet(
            brand = "Realme",
            profileIds = listOf("realme-smart-tv"),
            groups = listOf(
                PackageGroup("tracking", "Tracking and analytics", GroupDefault.DISABLE,
                    listOf(ai.promptstudio.tvclean.core.model.PackageEntry(PackageName("com.example.derived"), null))),
            ),
            stockLaunchers = KnownLaunchers.generic,
        )
        val withDerived = BundledProfileSource(ProfileBundle(null, emptyList(), listOf(derived)))
        val r = withDerived.resolve(facts("Realme"))
        assertIs<ProfileResolution.BrandSureSet>(r)
        assertEquals(
            setOf(PackageName("com.example.derived")),
            r.sureSet.flatMap { g -> g.packages.map { it.name } }.toSet(),
            "the derived set's packages win, not the TV Clean fallback",
        )
    }

    // The brand match is case-insensitive, on the brand or the manufacturer (the same rule the
    // derived sets use): a TV that reports "realme" (Build.BRAND) or "Realme" (manufacturer)
    // both get the set.
    @Test fun brandMatchIsCaseInsensitiveAndCoversManufacturer() = runBlocking<Unit> {
        val lower = source.resolve(facts("realme"))
        assertIs<ProfileResolution.BrandSureSet>(lower)
        val byMaker = source.resolve(facts("Realme").copy(brand = "SomeOdm", manufacturer = "Realme"))
        assertIs<ProfileResolution.BrandSureSet>(byMaker)
    }
}
