package cc.esdy.ark;

import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import org.kiwix.libzim.Archive;
import org.kiwix.libzim.Entry;
import org.kiwix.libzim.Item;

/**
 * Serves one ZIM archive to a WebView from https://zim.ark.local/<path>.
 * Every other host gets an empty 403, so pages inside an archive can never reach the internet.
 */
public final class ZimWebClient extends WebViewClient {
    public static final String HOST = "zim.ark.local";
    private final Archive archive;
    /** Called with the archive path of a PDF the user tapped (a WebView can't show PDFs). */
    public java.util.function.Consumer<String> onPdf;

    public ZimWebClient(Archive archive) { this.archive = archive; }

    public String mainUrl() throws Exception {
        Entry main = archive.getMainEntry();
        // The main entry is usually a redirect to the real front page.
        Item item = main.getItem(true);
        return "https://" + HOST + "/" + Uri.encode(item.getPath(), "/");
    }

    @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
        Uri u = req.getUrl();
        if (!"https".equals(u.getScheme()) || !HOST.equals(u.getHost())) return blocked();
        String path = u.getPath(); // already percent-decoded; query and fragment are not part of it
        if (path == null || path.length() < 2) return notFound();
        path = path.substring(1);
        try {
            if (!archive.hasEntryByPath(path)) return notFound();
            Item item = archive.getEntryByPath(path).getItem(true);
            String mime = item.getMimetype();
            String type = mime, charset = null;
            int semi = mime.indexOf(';');
            if (semi >= 0) type = mime.substring(0, semi).trim();
            if (type.startsWith("text/") || type.endsWith("javascript") || type.endsWith("json")) charset = "utf-8";
            byte[] data = item.getData().getData();
            WebResourceResponse r = new WebResourceResponse(type, charset, new ByteArrayInputStream(data));
            Map<String, String> h = new HashMap<>();
            h.put("Cache-Control", "no-store");
            r.setResponseHeaders(h);
            return r;
        } catch (Exception e) {
            return notFound();
        }
    }

    @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
        // Stay inside the archive; ignore links to anywhere else.
        if (!HOST.equals(req.getUrl().getHost())) return true;
        String path = req.getUrl().getPath();
        if (onPdf != null && path != null && path.length() > 1) {
            try {
                String p = path.substring(1);
                if (archive.hasEntryByPath(p) && archive.getEntryByPath(p).getItem(true).getMimetype().startsWith("application/pdf")) {
                    onPdf.accept(p);
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    private static WebResourceResponse blocked() {
        return new WebResourceResponse("text/plain", "utf-8", 403, "Offline", null, new ByteArrayInputStream(new byte[0]));
    }

    private static WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not found", null, new ByteArrayInputStream(new byte[0]));
    }
}
