package ai.promptstudio.tvclean.launcher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

// The launcher's ThemeProvider (RFC-THEME-1): the way the phone writes the look into this app's
// own files. The provider opens config.json, wallpaper.jpg and theme_revision under this app's
// external files folder, so a file the provider writes is the launcher's own and the launcher can
// always open it — the shell's write lock (issues #1279, #1649, #1290) never blocks the look.
//
// It is a file provider only: query/insert/update/delete are not supported. The manifest guards it
// with WRITE_SECURE_SETTINGS, so only the shell and the system can reach it, never an ordinary app
// on the TV. The file name is checked against a fixed list, so no path can escape the folder
// (no "/", no "..").
public final class ThemeProvider extends ContentProvider {

    // The files the phone writes; the first two are what this app sends (ThemeProviderLines.FILES).
    static final Set<String> FILES = new HashSet<>(Arrays.asList("config.json", "wallpaper.jpg", "theme_revision"));

    @Override public boolean onCreate() { return true; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || !FILES.contains(name)) {
            throw new FileNotFoundException("the launcher's theme provider takes only " + FILES);
        }
        File dir = getContext().getExternalFilesDir(null);
        if (dir == null) throw new FileNotFoundException("no external files folder");
        File file = new File(dir, name);
        int access = ParcelFileDescriptor.parseMode(mode);
        return ParcelFileDescriptor.open(file, access);
    }

    @Override public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        if ("config.json".equals(name)) return "application/json";
        if ("wallpaper.jpg".equals(name)) return "image/jpeg";
        return "application/octet-stream";
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        throw new UnsupportedOperationException("the theme provider is files only");
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("the theme provider is files only");
    }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("the theme provider is files only");
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("the theme provider is files only");
    }
}
