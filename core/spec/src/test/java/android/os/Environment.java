package android.os;

// Test stub for launcher 2.7's Config.java: external storage is always mounted here, so Config.load
// reads config.json from the external files dir and keeps its private copy up to date.
public final class Environment {
    private Environment() {}

    public static final String MEDIA_MOUNTED = "mounted";

    public static String getExternalStorageState() { return MEDIA_MOUNTED; }
}
