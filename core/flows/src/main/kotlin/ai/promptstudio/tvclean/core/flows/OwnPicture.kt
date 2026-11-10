package ai.promptstudio.tvclean.core.flows

import ai.promptstudio.tvclean.core.model.BlobRef
import ai.promptstudio.tvclean.core.model.TvId
import ai.promptstudio.tvclean.core.store.BlobStore
import ai.promptstudio.tvclean.core.store.DocumentStore

// 0.4.0: the person's own photo for one TV. The phone makes it (crop to 16:9, 1920 x 1080, a JPEG of
// at most MAX_BYTES, re-encoded so no metadata such as the place or the camera is left) and keeps it
// in the app's private files at tvs/<id>/picture.jpg: not backed up, gone with "Forget this TV",
// never in a TV report or a restore point. Each push puts it into the BlobStore (a cache the system
// may clear) and sends it as the launcher's wallpaper.jpg, the same push a look's own picture takes.
// A newly made photo is only staged (stage: the BlobStore, for the preview and the push) and kept
// (keep) once "Put it on the TV" has put it there, so the kept copy, and "Restore mine", is always
// the photo the TV last got from this phone.
internal object OwnPicture {
    const val MAX_BYTES = 280_000
    const val DEFAULT_DIM = 30

    fun path(id: TvId) = "tvs/${id.value}/picture.jpg"

    // A new photo as a file the preview and the push can read; nothing is kept for the TV yet.
    // Refused unless it is a JPEG within the size the push allows.
    suspend fun stage(d: ManageDeps, jpeg: ByteArray): BlobRef = stage(d.blobs, jpeg)

    suspend fun stage(blobs: BlobStore, jpeg: ByteArray): BlobRef {
        check(jpeg)
        return blobs.putBytes(jpeg)
    }

    // Keeps the photo for the TV, after the TV got it.
    suspend fun keep(d: ManageDeps, id: TvId, jpeg: ByteArray) = keep(d.documents, id, jpeg)

    suspend fun keep(documents: DocumentStore, id: TvId, jpeg: ByteArray) {
        check(jpeg)
        documents.write(path(id), jpeg)
    }

    private fun check(jpeg: ByteArray) =
        require(jpeg.size in 4..MAX_BYTES && jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte()) { "not a JPEG of at most $MAX_BYTES bytes" }

    // The kept photo as a file the push can send, or null when this TV has none.
    suspend fun ref(d: ManageDeps, id: TvId): BlobRef? = ref(d.documents, d.blobs, id)

    suspend fun ref(documents: DocumentStore, blobs: BlobStore, id: TvId): BlobRef? {
        val bytes = documents.read(path(id)) { it } ?: return null
        return blobs.get(sha256Of(bytes)) ?: blobs.putBytes(bytes)
    }

    // A HomeChoice.picture to what the push sends: the own photo with its dim, or a gallery picture.
    suspend fun lookPicture(d: ManageDeps, id: TvId, picture: String?, dim: Int?): LookPicture? =
        if (picture == LookPicture.MINE_ID) ref(d, id)?.let { mine(it, dim) }
        else LookPicture.of(picture, d.pictures)

    // The kept photo with its dim (the default when none was kept), as every push sends it.
    fun mine(ref: BlobRef, dim: Int?): LookPicture.Of = LookPicture.Of(ref, (dim ?: DEFAULT_DIM).coerceIn(0, 90))
}
