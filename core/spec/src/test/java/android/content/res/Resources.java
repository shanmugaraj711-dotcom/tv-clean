package android.content.res;

import java.util.Map;

// Test stub for upstream Config.java (1bbf0c9, v2.4.0 and 2.7): resource lookups answered from a map of id to value,
// filled from the launcher's res/values/config.xml by the test. getString with format arguments is
// String.format, as Android's; launcher 2.7's RaceWeekend calls it, Config.load never does.
public class Resources {
    private final Map<Integer, Object> values;

    public Resources(Map<Integer, Object> values) { this.values = values; }

    private Object get(int id) {
        Object v = values.get(id);
        if (v == null) throw new NotFoundException("resource 0x" + Integer.toHexString(id));
        return v;
    }

    public String[] getStringArray(int id) { return ((String[]) get(id)).clone(); }
    public String getString(int id) { return (String) get(id); }
    public String getString(int id, Object... formatArgs) { return String.format(getString(id), formatArgs); }
    public int getInteger(int id) { return (Integer) get(id); }
    public boolean getBoolean(int id) { return (Boolean) get(id); }
    public int[] getIntArray(int id) { return ((int[]) get(id)).clone(); }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String name) { super(name); }
    }
}
