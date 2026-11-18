package cc.esdy.ark;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/** Reader and appearance settings, kept with the library's other choices. */
final class AppSettings {
    static final String SYSTEM = "system", LIGHT = "light", DARK = "dark";
    static final int[] TEXT_ZOOMS = {85, 100, 125, 150};
    static final String[] TEXT_ZOOM_NAMES = {"Small", "Normal", "Large", "Larger"};

    private AppSettings() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("library", Context.MODE_PRIVATE);
    }

    static String theme(Context c) { return prefs(c).getString("theme", SYSTEM); }
    static void setTheme(Context c, String theme) { prefs(c).edit().putString("theme", theme).apply(); }

    static int textZoom(Context c) { return prefs(c).getInt("textZoom", 100); }
    static void setTextZoom(Context c, int zoom) { prefs(c).edit().putInt("textZoom", zoom).apply(); }

    /** The context an activity should use: the system's light/dark choice unless the user picked one. */
    static Context apply(Context base) {
        String theme = theme(base);
        if (SYSTEM.equals(theme)) return base;
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (DARK.equals(theme) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return base.createConfigurationContext(config);
    }
}
