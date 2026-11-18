package cc.esdy.ark;

import android.app.Activity;
import android.os.Build;

/**
 * Back handling that also works with Android 13+ back gestures. Apps targeting Android 16 no longer get
 * onBackPressed() for gestures, so screens with their own back logic (reader history, search results, tabs)
 * register it here; older Android versions keep calling onBackPressed(), which should call the same logic.
 */
final class BackCompat {
    private BackCompat() {}

    static void register(Activity activity, Runnable onBack) {
        if (Build.VERSION.SDK_INT >= 33)
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, onBack::run);
    }
}
