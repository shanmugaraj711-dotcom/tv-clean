package ai.promptstudio.tvclean.core.spec

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The curated half of a profile: what a person reading the upstream header decided (match
// strings, platform, stock launcher, quirks, keep lists, tested info) and the category of every
// upstream group title. Bundled as profile-meta.json next to this class; DebloatTxtParser falls
// back to header keywords for a folder that has no entry (a newer upstream example).
@Serializable
internal data class ProfileMetaFile(val schema: Int, val upstreamCommit: String? = null, val profiles: Map<String, FolderMeta>)

@Serializable
internal data class FolderMeta(
    val match: MatchMeta,
    val platform: String,
    val stockLaunchers: List<String> = emptyList(),
    val liveTv: String? = null,
    val setupWraith: String? = null,
    // The list names the setup wizard, but the home screen switch on this TV keeps it on (it is
    // never-disable here): a list's later notes may switch it off at a cost the curated takeover
    // does not take, as casting on xiaomi-mitv-aesp0.
    val setupWraithStaysOn: Boolean = false,
    val quirks: List<String> = emptyList(),
    val neverDisable: List<String> = emptyList(),
    val suggestedDock: List<String> = emptyList(),
    val tested: TestedMeta? = null,
    val groups: Map<String, CategoryMeta> = emptyMap(),
    val packages: Map<String, CategoryMeta> = emptyMap(),
    // Active package lines of the upstream list that this profile leaves out, as if they were
    // commented out, each with the reason (package to reason). The converter fails when one is not an
    // active line of the list, so an entry cannot outlive the line it is about.
    val leftOut: Map<String, String> = emptyMap(),
)

@Serializable
internal data class MatchMeta(
    val models: List<String> = emptyList(), val devices: List<String> = emptyList(), val brand: String? = null,
    val sdkMin: Int? = null, val sdkMax: Int? = null, val fingerprintPrefix: String? = null,
)

@Serializable
internal data class TestedMeta(val by: String? = null, val firmware: String? = null, val daysLivedWith: Int? = null, val rebootTested: Boolean = false,
    // Test build: a candidate list (core/spec/candidates/candidate-meta.json), never set in profile-meta.json.
    val candidate: Boolean = false)

@Serializable
internal data class CategoryMeta(val category: String, val ask: Boolean = false)

internal object ProfileMeta {
    // NOTE (2026-11-10): this is a slash-separated classpath resource path, not a Kotlin
    // package name. It must match the file under src/main/resources (ai/promptstudio/...).
    const val RESOURCE = "/ai/promptstudio/tvclean/core/spec/profile-meta.json"
    private val json = Json { ignoreUnknownKeys = false }

    fun decode(text: String): ProfileMetaFile = json.decodeFromString(ProfileMetaFile.serializer(), text).also {
        require(it.schema == 1) { "profile-meta schema ${it.schema} is not 1" }
    }

    val bundled: ProfileMetaFile by lazy {
        val text = ProfileMeta::class.java.getResourceAsStream(RESOURCE)?.use { it.readBytes().decodeToString() }
        if (text == null) ProfileMetaFile(1, null, emptyMap()) else decode(text)
    }
}
