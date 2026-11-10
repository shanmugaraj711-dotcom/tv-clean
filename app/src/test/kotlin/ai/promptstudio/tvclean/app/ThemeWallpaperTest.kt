package ai.promptstudio.tvclean.app

import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import ai.promptstudio.tvclean.core.model.BlobRef
import ai.promptstudio.tvclean.core.model.Sha256
import ai.promptstudio.tvclean.core.registry.DiskBlobStore
import ai.promptstudio.tvclean.core.spec.BundledThemeSource
import ai.promptstudio.tvclean.core.store.BlobStore
import ai.promptstudio.tvclean.testing.fixtures.sha256Of
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// T7 required behaviour 2: BlobRef.path must be an absolute file (W2 opens File(blob.path)), and
// W4's wallpapers are bundled: refs. The graph's ThemeSource copies each wallpaper once into the
// BlobStore and hands out refs to those files.
class ThemeWallpaperTest {
    @get:Rule val tmp = TemporaryFolder()

    private class Counting(private val inner: BlobStore) : BlobStore by inner {
        val puts = AtomicInteger()
        override suspend fun put(sha256: Sha256, source: InputStream, expectedSize: Long): BlobRef { puts.incrementAndGet(); return inner.put(sha256, source, expectedSize) }
    }

    private fun store() = Counting(DiskBlobStore(File(tmp.root, "artifacts"), File(tmp.root, "bundled"), { null }))

    @Test fun everyWallpaperIsAnAbsoluteFileWithItsDigest() {
        val bundled = BundledThemeSource().bundled()
        assertTrue(bundled.any { it.wallpaper?.path?.startsWith(BundledThemeSource.PREFIX) == true }, "W4 still ships bundled: refs")
        val blobs = store()
        val themes = MaterializedThemeSource(BundledThemeSource(), blobs).bundled()
        assertEquals(bundled.map { it.id }, themes.map { it.id })
        for ((before, after) in bundled.zip(themes)) {
            assertEquals(before.doc, after.doc)
            val w = before.wallpaper ?: continue
            val ref = assertNotNull(after.wallpaper)
            val f = File(ref.path)
            assertTrue(f.isAbsolute && f.isFile, ref.path)
            assertEquals(w.sha256, ref.sha256)
            assertEquals(w.size, ref.size)
            assertEquals(w.sha256, sha256Of(f.readBytes()))
        }
    }

    @Test fun eachWallpaperIsCopiedOnceAndAgainOnlyWhenTheCacheLostIt() = runBlocking {
        val blobs = store()
        val source = MaterializedThemeSource(BundledThemeSource(), blobs)
        val wallpapers = BundledThemeSource().bundled().mapNotNull { it.wallpaper }
        // 0.4.0: the Picture row's pictures too; Aurora's is the Aurora look's file, stored once.
        val files = (wallpapers + BundledThemeSource().pictures().map { it.wallpaper }).map { it.sha256 }.distinct()
        source.prepare()
        assertEquals(files.size, blobs.puts.get())
        source.bundled(); source.bundled(); source.pictures()
        assertEquals(files.size, blobs.puts.get(), "no second copy")
        blobs.delete(wallpapers.first().sha256)   // the system cleared the cache
        val again = source.bundled().firstNotNullOf { it.wallpaper?.takeIf { w -> w.sha256 == wallpapers.first().sha256 } }
        assertTrue(File(again.path).isFile)
        assertEquals(files.size + 1, blobs.puts.get())
        val pictures = source.pictures()
        // look2: the gallery of every look's picture (spec 6.2), Diwali first while it is offered.
        assertEquals(listOf("diwali", "aurora", "mist", "glass", "ruska", "curtains", "mountains", "lamp", "neon", "cobalt"), pictures.map { it.id })
        assertTrue(pictures.all { File(it.wallpaper.path).isFile && it.wallpaper.size in 1..300_000 })
    }

    @Test fun aWallpaperThatCannotBeStoredDropsItsTheme() {
        val failing = object : BlobStore by store() {
            override suspend fun put(sha256: Sha256, source: InputStream, expectedSize: Long): BlobRef = throw java.io.IOException("disk full")
        }
        val errors = mutableListOf<String>()
        val themes = MaterializedThemeSource(BundledThemeSource(), failing, onError = { m, _ -> errors += m }).bundled()
        val withWallpaper = BundledThemeSource().bundled().filter { it.wallpaper != null }.map { it.id }
        assertTrue(withWallpaper.isNotEmpty())
        assertTrue(themes.none { it.id in withWallpaper })
        assertTrue(themes.all { it.wallpaper == null })
        assertEquals(withWallpaper.size, errors.size)
    }
}
