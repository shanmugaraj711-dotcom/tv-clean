package android.content;

import android.content.res.Resources;
import java.io.File;

// Test stub for upstream Config.java: the Context calls it makes. The preferences stand in for the
// launcher's private launcher_settings file; null gives a fresh empty store on each call. `filesDir`
// is the app's private files dir, where launcher 2.7 keeps a copy of the last config.json it read;
// it must not be the external files dir, or the copy would overwrite the pushed file.
public class Context {
    public static final int MODE_PRIVATE = 0;

    private final File externalFilesDir;
    private final File filesDir;
    private final Resources resources;
    private final SharedPreferences preferences;

    public Context(File externalFilesDir, Resources resources, SharedPreferences preferences) {
        this(externalFilesDir, null, resources, preferences);
    }

    public Context(File externalFilesDir, File filesDir, Resources resources, SharedPreferences preferences) {
        this.externalFilesDir = externalFilesDir;
        this.filesDir = filesDir;
        this.resources = resources;
        this.preferences = preferences;
    }

    public File getExternalFilesDir(String type) { return externalFilesDir; }

    public File getFilesDir() {
        if (filesDir == null) throw new IllegalStateException("no private files dir in this test context");
        return filesDir;
    }

    public Resources getResources() { return resources; }

    public SharedPreferences getSharedPreferences(String name, int mode) {
        return preferences != null ? preferences : new MemoryPreferences();
    }
}
