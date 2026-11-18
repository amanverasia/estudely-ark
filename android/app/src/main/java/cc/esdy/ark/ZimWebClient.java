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
    /** Called when a page has finished loading (the reader updates its toolbar). */
    public Runnable onPageFinished;
    /** Dark theme: allow WebView to darken pages that declare "color-scheme: light only" (DevDocs does). */
    public boolean darkPages;
    static final String ALLOW_DARKENING = "<style>:root{color-scheme:light!important}</style>";

    /**
     * DevDocs archives lay out a 256 px sidebar with a fixed 240 px margin and their own scrolling box: on a
     * phone that leaves the text about 120 px wide. On narrow screens, hide the sidebar (the reader's Contents
     * button shows it full screen), give the text the full width and let the page scroll normally.
     * Scoped to DevDocs pages (their ._app wrapper), so other archives are unchanged.
     */
    static final String PHONE_LAYOUT = "<style>@media (max-width: 760px){"
        + "html.ark-devdocs,html.ark-devdocs body{height:auto!important;overflow:visible!important}"
        + "html.ark-devdocs ._app,html.ark-devdocs ._container{height:auto!important;margin-left:0!important;width:auto!important}"
        + "html.ark-devdocs ._sidebar{display:none!important}"
        + "html.ark-devdocs.ark-nav ._sidebar{display:block!important;position:fixed!important;top:0!important;left:0!important;"
        + "right:0!important;bottom:0!important;width:auto!important;height:auto!important;overflow:auto!important;z-index:2147483000!important;background:#fff!important}"
        + "html.ark-devdocs.ark-nav ._sidebar ._list,html.ark-devdocs.ark-nav ._sidebar ._list-item{width:auto!important;max-width:none!important}"
        + "html.ark-devdocs ._content{height:auto!important;overflow:visible!important;margin-left:0!important;padding:0 14px 24px!important;"
        // DevDocs sets text at 0.875rem; on a phone use 1rem (the reader's text size setting still scales it).
        + "font-size:1rem!important;line-height:1.6!important}"
        + "html.ark-devdocs ._page{width:auto!important}"
        + "html.ark-devdocs ._attribution-p{box-sizing:border-box!important;max-width:100%;overflow-wrap:anywhere}"
        + "html.ark-devdocs ._page table{display:block;overflow-x:auto;max-width:100%}"
        // Code keeps its line breaks and indentation and scrolls sideways; wrapping it word by word made it unreadable.
        + "html.ark-devdocs ._page pre{white-space:pre!important;overflow-x:auto!important;overflow-wrap:normal!important;max-width:100%}"
        // Tables holding code (C/C++ declaration tables) stack their cells, so the code gets the full width and the
        // "(1)" / "(since C11)" labels sit under it. Older WebViews without :has() keep the scrolling table above.
        + "html.ark-devdocs ._page table:has(pre),html.ark-devdocs ._page table:has(pre)>tbody{display:block!important;overflow:visible!important}"
        + "html.ark-devdocs ._page table:has(pre) tr{display:flex!important;flex-wrap:wrap;align-items:baseline;column-gap:8px}"
        + "html.ark-devdocs ._page table:has(pre) :is(td,th){display:block!important;border:0!important}"
        + "html.ark-devdocs ._page table:has(pre) td:has(>pre){flex:1 1 100%;min-width:0}"
        // Contents rows big enough to tap.
        // Padding, not flex: the item count floats right and flex would move it in front of the title.
        + "html.ark-devdocs.ark-nav ._sidebar ._list-item{font-size:1rem!important;line-height:1.25!important;"
        + "padding-top:12px!important;padding-bottom:12px!important;height:auto!important}"
        // DevDocs pins the folder arrow to the row's top; centre it (its 32 px box) in the taller row.
        + "html.ark-devdocs.ark-nav ._sidebar ._list-arrow{top:calc(50% - 16px)!important}"
        + "html.ark-devdocs ._page table:has(pre) .ark-empty{display:none!important}"
        + "}</style><script>(function(){function f(){if(!document.querySelector('._app,devdocs-navbar'))return;"
        + "document.documentElement.classList.add('ark-devdocs');"
        // Stacked declaration tables show their empty filler cells as grey boxes; mark them so the CSS hides them.
        + "document.querySelectorAll('._page td,._page th').forEach(function(c){if(!c.textContent.trim()&&!c.querySelector('img,svg,pre'))c.classList.add('ark-empty')})}"
        + "if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',f);else f();"
        + "window.arkHasContents=function(){return document.documentElement.classList.contains('ark-devdocs')};"
        + "window.arkToggleContents=function(open){document.documentElement.classList.toggle('ark-nav',open);"
        // Opening Contents shows the current page's entry, not the top of a long list.
        + "var a=open&&document.querySelector('._sidebar .active');if(a)a.scrollIntoView({block:'center'})};})();</script>";

    /** Adds PHONE_LAYOUT to an HTML page, right after <head> (or at the start if there is none). */
    static byte[] withPhoneLayout(byte[] html, String extra) {
        String page = new String(html, java.nio.charset.StandardCharsets.UTF_8);
        String add = PHONE_LAYOUT + extra;
        // After <head> so it comes before the archive's own styles; "light only" in a later stylesheet would win
        // over a plain rule, hence !important in ALLOW_DARKENING.
        int at = page.indexOf("<head>");
        page = at < 0 ? add + page : page.substring(0, at + 6) + add + page.substring(at + 6);
        return page.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

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
            if ("text/html".equals(type)) data = withPhoneLayout(data, darkPages ? ALLOW_DARKENING : "");
            WebResourceResponse r = new WebResourceResponse(type, charset, new ByteArrayInputStream(data));
            Map<String, String> h = new HashMap<>();
            h.put("Cache-Control", "no-store");
            r.setResponseHeaders(h);
            return r;
        } catch (Exception e) {
            return notFound();
        }
    }

    @Override public void onPageFinished(WebView view, String url) {
        if (onPageFinished != null) onPageFinished.run();
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
