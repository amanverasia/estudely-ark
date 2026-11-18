package cc.esdy.ark;

import android.app.Activity;
import android.content.Context;

/** Base for every screen: applies the theme chosen in settings (System, Light or Dark) on all Android versions. */
public abstract class ArkActivity extends Activity {
    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(AppSettings.apply(base)); }
}
