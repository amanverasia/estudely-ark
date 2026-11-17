package cc.esdy.ark;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;

/** The pack's own offline map viewer (MapLibre + PMTiles, place search), served from the phone's storage. */
public class MapActivity extends Activity {
    private WebView web;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LibraryEngine engine = LibraryEngine.get(this);
        String folder = getIntent().getStringExtra("folder");
        web = new WebView(this);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this); // padding on a WebView doesn't move its page
        box.addView(web);
        MainActivity.edgeToEdge(this, box);
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
        setContentView(box);
        if (BuildConfig.DEBUG) web.postDelayed(() -> web.evaluateJavascript(
            "JSON.stringify({err: document.getElementById('err').textContent, w: innerWidth, h: innerHeight, gl: !!document.createElement('canvas').getContext('webgl2'), bar: document.getElementById('bar').getBoundingClientRect().top})",
            v -> android.util.Log.i("EstudelyArkWeb", "state " + v)), 10000);
    }

    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
