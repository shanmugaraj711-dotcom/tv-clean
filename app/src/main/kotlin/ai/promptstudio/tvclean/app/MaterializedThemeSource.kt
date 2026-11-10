package ai.promptstudio.tvclean.app

import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import ai.promptstudio.tvclean.core.model.BlobRef
import ai.promptstudio.tvclean.core.spec.BundledThemeSource
import ai.promptstudio.tvclean.core.spec.Picture
import ai.promptstudio.tvclean.core.spec.PictureSource
import ai.promptstudio.tvclean.core.spec.Theme
import ai.promptstudio.tvclean.core.spec.ThemeSource
import ai.promptstudio.tvclean.core.store.BlobStore

// W4's theme wallpapers are `bundled:ai/promptstudio/tvclean/...` refs into the :core:spec resources, but the
// session streams a push from File(blob.path) (W2). This source copies each wallpaper once into the
// BlobStore (put checks size and sha256 before the file becomes visible) and hands out themes whose
// wallpaper refs are plain absolute files. The store's get() is asked again on every call, so a
// copy the system cleared from the cache is made again rather than pointed at.
//
// prepare() runs at start in the background; bundled() is synchronous (ThemeSource is), so a call
// that comes before prepare finished makes the copy itself. A wallpaper that cannot be stored drops
// its theme from the list: a theme is never offered with a ref the session cannot open. The
// pictures (0.4.0, PictureSource) are handled the same way.
class MaterializedThemeSource(
    private val source: ThemeSource,
    private val blobs: BlobStore,
    private val bytesOf: (BlobRef) -> ByteArray? = BundledThemeSource::bytes,
    private val onError: (String, Throwable?) -> Unit = { _, _ -> },
    private val gallery: PictureSource = source as? PictureSource ?: PictureSource.NONE,
) : ThemeSource, PictureSource {

    override fun bundled(): List<Theme> = source.bundled().mapNotNull { theme ->
        val w = theme.wallpaper ?: return@mapNotNull theme
        materialize(theme.id, w)?.let { theme.copy(wallpaper = it) }
    }

    override fun pictures(): List<Picture> = gallery.pictures().mapNotNull { p ->
        materialize("picture ${p.id}", p.wallpaper)?.let { p.copy(wallpaper = it) }
    }

    suspend fun prepare() {
        val refs = source.bundled().mapNotNull { t -> t.wallpaper?.let { t.id to it } } +
            gallery.pictures().map { "picture ${it.id}" to it.wallpaper }
        for ((id, w) in refs) {
            if (isBundled(w) && blobs.get(w.sha256) == null) put(id, w)
        }
    }

    private fun materialize(id: String, ref: BlobRef): BlobRef? {
        if (!isBundled(ref)) return ref
        blobs.get(ref.sha256)?.let { return it }
        return synchronized(this) { blobs.get(ref.sha256) ?: runBlocking { put(id, ref) } }
    }

    private suspend fun put(id: String, ref: BlobRef): BlobRef? {
        val bytes = bytesOf(ref) ?: run { onError("theme $id: wallpaper missing from the resources", null); return null }
        return try {
            blobs.put(ref.sha256, ByteArrayInputStream(bytes), bytes.size.toLong())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            onError("theme $id: wallpaper could not be stored", e)
            null
        }
    }

    private fun isBundled(ref: BlobRef) = ref.path.startsWith(BundledThemeSource.PREFIX)
}
