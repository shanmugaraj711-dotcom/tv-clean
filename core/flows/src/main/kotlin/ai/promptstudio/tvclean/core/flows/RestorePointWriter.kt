package ai.promptstudio.tvclean.core.flows

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import ai.promptstudio.tvclean.core.backup.TvRestoreFiles
import ai.promptstudio.tvclean.core.model.BlobRef
import ai.promptstudio.tvclean.core.model.Profile
import ai.promptstudio.tvclean.core.model.Sha256
import ai.promptstudio.tvclean.core.model.Tv
import ai.promptstudio.tvclean.core.planner.PlanRequest
import ai.promptstudio.tvclean.core.planner.PlanResult
import ai.promptstudio.tvclean.core.session.TvSession
import ai.promptstudio.tvclean.core.store.BlobStore

fun sha256Of(bytes: ByteArray): Sha256 =
    Sha256(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })

suspend fun BlobStore.putBytes(bytes: ByteArray): BlobRef = put(sha256Of(bytes), ByteArrayInputStream(bytes), bytes.size.toLong())

// D44: after every plan, restore-point.json and restore.txt (W6's bytes) are pushed to
// /sdcard/tvclean/ by a WriteRestorePoint plan (R0: 11). Best effort: a failure here never
// turns a finished plan into a stop; the phone's own restore point is the one that matters.
class RestorePointWriter(
    private val files: TvRestoreFiles,
    private val blobs: BlobStore,
    private val ledger: RunLedger,
    private val runner: PlanRunner,
) {
    suspend fun write(tv: Tv, session: TvSession, profile: Profile?): Boolean = attempt {
        val changes = ledger.changes(tv.id)
        val json = blobs.putBytes(files.restorePointJson(tv, changes))
        val txt = blobs.putBytes(files.restoreTxt(tv, changes, profile))
        val snapshot = runner.snapshotFor(tv, session)
        val plan = (runner.plan(tv, PlanRequest.WriteRestorePoint(json, txt), snapshot) as? PlanResult.Ok)?.plan ?: return@attempt false
        runner.run(tv, plan, session) is RunOutcome.Finished
    }.getOrDefault(false)
}
