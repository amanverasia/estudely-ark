package cc.esdy.ark;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.webkit.WebSettings;
import android.webkit.WebView;

/** The pack's own offline map viewer (MapLibre + PMTiles, place search), served from the phone's storage. */
public class MapActivity extends ArkActivity {
    private WebView web;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LibraryEngine engine = LibraryEngine.get(this);
        String folder = getIntent().getStringExtra("folder");
        ArkUi ui = new ArkUi(this);
        ui.applyWindow(this);
        LinearLayout root = ui.column();
        root.setBackgroundColor(ui.paper);
        MainActivity.edgeToEdge(this, root);
        LinearLayout toolbar = ui.row();
        toolbar.setPadding(ui.dp(8), ui.dp(3), ui.dp(22), ui.dp(3));
        toolbar.addView(ui.iconButton("back", "Back to library", v -> handleBack()));
        ModuleCatalog.Module module = engine.catalog.find(folder);
        String title = module != null ? ui.shortTitle(module) : "Map of India";
        setTitle(title);
        TextView heading = ui.text(title, 16, ui.ink, true);
        heading.setPadding(ui.dp(4), 0, 0, 0);
        toolbar.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(toolbar);
        root.addView(ui.divider());
        web = new WebView(this);
        FrameLayout box = new FrameLayout(this); // padding on a WebView doesn't move its page
        box.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        PackWebClient client = new PackWebClient(engine.packDir());
        client.onHome = this::finish;
        web.setWebViewClient(client);
        web.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override public boolean onConsoleMessage(android.webkit.ConsoleMessage m) {
                android.util.Log.i("EstudelyArkWeb", m.messageLevel() + " " + m.message() + " (" + m.sourceId() + ":" + m.lineNumber() + ")");
                return true;
            }
        });
        web.loadUrl("https://" + PackWebClient.HOST + "/" + folder + "/index.html");
        setContentView(root);
        BackCompat.register(this, this::handleBack);
        if (BuildConfig.DEBUG) web.postDelayed(() -> web.evaluateJavascript(
            "JSON.stringify({err: document.getElementById('err').textContent, w: innerWidth, h: innerHeight, gl: !!document.createElement('canvas').getContext('webgl2'), bar: document.getElementById('bar').getBoundingClientRect().top})",
            v -> android.util.Log.i("EstudelyArkWeb", "state " + v)), 10000);
    }

    /** Android 8-12 path; Android 13+ back gestures go through BackCompat (OnBackInvokedCallback). */
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { handleBack(); }
    private void handleBack() { if (web.canGoBack()) web.goBack(); else finish(); }
}
