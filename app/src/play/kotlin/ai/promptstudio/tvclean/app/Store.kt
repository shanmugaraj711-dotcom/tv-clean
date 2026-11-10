package ai.promptstudio.tvclean.app

import ai.promptstudio.tvclean.core.flows.StoreRules
import ai.promptstudio.tvclean.core.flows.UpdateChecker
import ai.promptstudio.tvclean.core.model.ArtifactRef
import ai.promptstudio.tvclean.core.registry.ArtifactFetcher
import ai.promptstudio.tvclean.core.registry.FetchFailure
import ai.promptstudio.tvclean.core.registry.FetchResult
import ai.promptstudio.tvclean.core.registry.HttpFetcher

// The Google Play build (STORES-PLAN 1, decision D3): Google Play updates the app, and Play allows
// no app downloads from outside Play, so there is no update check, no add-on download and no host
// to reach; the TV's software updaters always stay on. The ports are no-ops and the flows hide
// what they would have offered (StoreRules.PLAY).
object Store : StorePorts {
    override val flavour = "play"
    override val hosts: Set<String> = emptySet()
    override val rules = StoreRules.PLAY
    override val updatesMayGoOff = rules.updatesOff
    override val releasePage: String? = null

    // Never asked (Settings has no "Check for updates"); answers "couldn't check" without a request.
    override fun updateChecker(http: HttpFetcher) = UpdateChecker { null }

    // The real fetcher is never built: nothing is downloaded, for any URL.
    override fun addOnFetcher(make: () -> ArtifactFetcher): ArtifactFetcher = NoAddOns

    object NoAddOns : ArtifactFetcher {
        override suspend fun fetch(ref: ArtifactRef, onProgress: (Long, Long) -> Unit): FetchResult =
            FetchResult.Failed(FetchFailure.HOST_NOT_ALLOWED, null)
    }
}
