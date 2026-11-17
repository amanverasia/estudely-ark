package cc.esdy.ark;

import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;

/**
 * Serves files from the pack folder to a WebView at https://pack.ark.local/<path>, with HTTP Range support
 * (the map viewer reads its tiles and fonts with Range requests and refuses a server without 206).
 * Every other host is refused, so pages can't reach the internet.
 *
 * WebView applies a request's Range header itself to whatever shouldInterceptRequest returns, sizing the
 * resource from InputStream.available() (an int, so useless for the 2.6 GB map file), and it cut our own 206
 * bodies a second time. So HTML pages get a small fetch() wrapper that moves the Range header into the
 * "ark-range" query parameter; WebView then passes our 206 through untouched.
 */
public final class PackWebClient extends WebViewClient {
    public static final String HOST = "pack.ark.local";
    private final File root;

    public PackWebClient(File root) { this.root = root; }

    static final String RANGE_SHIM = "<script>(function(){var f=window.fetch;window.fetch=function(u,o){try{"
        + "var h=new Headers(o&&o.headers),r=h.get('Range');"
        + "if(r&&typeof u==='string'){h.delete('Range');var x=new URL(u,location.href);x.searchParams.set('ark-range',r);"
        + "u=x.href;o=Object.assign({},o,{headers:h});}}catch(e){}return f.call(this,u,o);};})();</script>"
        // The app has its own toolbar with a back button, so drop the page's link back to the desktop START-HERE
        // page (and the " · " after it). The shared page keeps that link for the desktop pack.
        + "<script>document.addEventListener('DOMContentLoaded',function(){"
        + "document.querySelectorAll('a[href$=\"START-HERE.html\"]').forEach(function(a){"
        + "var n=a.nextSibling;if(n&&n.nodeType===3&&n.textContent.trim().charAt(0)==='\u00b7')n.remove();a.remove();});});</script>";

    static String mime(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".html")) return "text/html";
        if (n.endsWith(".js")) return "application/javascript";
        if (n.endsWith(".css")) return "text/css";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".tsv") || n.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }

    @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
        WebResourceResponse r = serve(req);
        if (BuildConfig.DEBUG) android.util.Log.i("EstudelyArkWeb", r.getStatusCode() + " " + req.getUrl() + " " + req.getRequestHeaders().get("Range"));
        return r;
    }

    private WebResourceResponse serve(WebResourceRequest req) {
        if (!HOST.equals(req.getUrl().getHost())) return error(403, "Offline");
        String path = req.getUrl().getPath();
        if (path == null) return error(404, "Not found");
        File f = new File(root, path);
        try {
            if (!f.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator) || !f.isFile()) return error(404, "Not found");
            String type = mime(f.getName());
            String charset = type.startsWith("text/") || type.endsWith("javascript") || type.endsWith("json") ? "utf-8" : null;
            Map<String, String> h = new HashMap<>();
            h.put("Accept-Ranges", "bytes");
            h.put("Access-Control-Allow-Origin", "*");
            long len = f.length();
            String range = null;
            for (Map.Entry<String, String> e : req.getRequestHeaders().entrySet()) if (e.getKey().equalsIgnoreCase("Range")) range = e.getValue();
            String q = req.getUrl().getQueryParameter("ark-range");
            if (q != null) range = q;
            if (type.equals("text/html")) {
                String html = new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                int at = html.indexOf("<head>");
                html = at < 0 ? RANGE_SHIM + html : html.substring(0, at + 6) + RANGE_SHIM + html.substring(at + 6);
                return new WebResourceResponse(type, charset, 200, "OK", h, new ByteArrayInputStream(html.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            }
            if (range != null && range.startsWith("bytes=")) {
                String[] p = range.substring(6).split("-", 2);
                long start, end;
                if (p[0].isEmpty()) { start = Math.max(0, len - Long.parseLong(p[1])); end = len - 1; }
                else { start = Long.parseLong(p[0]); end = p.length > 1 && !p[1].isEmpty() ? Math.min(Long.parseLong(p[1]), len - 1) : len - 1; }
                if (start > end || start >= len) return error(416, "Range Not Satisfiable");
                byte[] buf = new byte[(int) (end - start + 1)];
                try (RandomAccessFile raf = new RandomAccessFile(f, "r")) { raf.seek(start); raf.readFully(buf); }
                h.put("Content-Range", "bytes " + start + "-" + end + "/" + len);
                return new WebResourceResponse(type, charset, 206, "Partial Content", h, new ByteArrayInputStream(buf));
            }
            InputStream in = new FileInputStream(f);
            return new WebResourceResponse(type, charset, 200, "OK", h, in);
        } catch (Exception e) {
            return error(500, "Error");
        }
    }

    /** Called when a page links back to the desktop pack's START-HERE page, which the app replaces. */
    public Runnable onHome;

    @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
        String path = req.getUrl().getPath();
        if (onHome != null && path != null && path.endsWith("/START-HERE.html")) { onHome.run(); return true; }
        return !HOST.equals(req.getUrl().getHost());
    }

    private static WebResourceResponse error(int code, String reason) {
        return new WebResourceResponse("text/plain", "utf-8", code, reason, null, new ByteArrayInputStream(new byte[0]));
    }
}
