package ai.promptstudio.tvclean.core.planner

import ai.promptstudio.tvclean.core.spec.BundledProfileSource
import ai.promptstudio.tvclean.core.spec.DebloatTxtParser
import ai.promptstudio.tvclean.testing.fixtures.Fixtures
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

// WP4 item 2 (C6): the "Better back on" table cannot drift from upstream. Every entry is a kept
// (commented-out) line of that folder's debloat.txt at the pinned commit (WP0's Scan.kept), and
// so it is not in the shipped profile; a list that switches it off again, or drops the folder,
// fails here and the entry is reviewed.
class PutBackAdviceDriftTest {
    @Test fun everyEntryIsAKeptLineOfItsPinnedList() {
        val shipped = BundledProfileSource.bundled.profiles.associateBy { it.id }
        val problems = mutableListOf<String>()
        for (e in PutBackAdvice.entries) {
            val file = File(Fixtures.upstreamDir, "examples/${e.profileId}/debloat.txt")
            if (!file.isFile) { problems += "${e.profileId}: no upstream folder"; continue }
            val kept = DebloatTxtParser.kept(file.readText(), e.profileId)
            if (e.pkg !in kept) problems += "${e.profileId} ${e.pkg.value}: not a kept line (kept: ${kept.keys.map { it.value }})"
            val profile = shipped[e.profileId]
            if (profile == null) problems += "${e.profileId}: not a shipped profile"
            else if (profile.groups.any { g -> g.packages.any { it.name == e.pkg } }) problems += "${e.profileId} ${e.pkg.value}: still in a profile group"
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }
}
