package cc.esdy.ark;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

import org.kiwix.libkiwix.JNIKiwix;
import org.kiwix.libzim.Archive;
import org.kiwix.libzim.Query;
import org.kiwix.libzim.Search;
import org.kiwix.libzim.SearchIterator;
import org.kiwix.libzim.Searcher;
import org.kiwix.libzim.SuggestionItem;
import org.kiwix.libzim.SuggestionIterator;
import org.kiwix.libzim.SuggestionSearcher;

/** Reads one ZIM archive offline, with title suggestions and full-text search. */
public class ZimActivity extends ArkActivity {
    private Archive archive; // kept alive while the WebView reads from it
    private WebView web;
    private View contents;
    private boolean contentsOpen;
    private LinearLayout results;
    private ScrollView resultsScroll;
    private ZimWebClient client;
    private ArkUi ui;
    private EditText query;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        new JNIKiwix(this);
        File f = new File(getIntent().getStringExtra("path"));
        ui = new ArkUi(this);
        ui.applyWindow(this);
        String title = ModuleActivity.displayName(f);
        setTitle(title);
        LinearLayout root = ui.column();
        root.setBackgroundColor(ui.paper);
        root.setFocusableInTouchMode(true);
        MainActivity.edgeToEdge(this, root);
        LinearLayout toolbar = ui.row();
        toolbar.setPadding(ui.dp(8), ui.dp(3), ui.dp(22), ui.dp(3));
        toolbar.addView(ui.iconButton("back", "Back", v -> handleBack()));
        TextView heading = ui.text(title, 15, ui.ink, true);
        heading.setMaxLines(2);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        heading.setPadding(ui.dp(4), 0, 0, 0);
        toolbar.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        contents = ui.iconButton("grid", "Contents", v -> setContentsOpen(!contentsOpen));
        contents.setVisibility(View.GONE);
        toolbar.addView(contents);
        root.addView(toolbar);

        LinearLayout searchBar = ui.row();
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(ui.dp(13), 0, ui.dp(1), 0);
        searchBar.setBackground(ui.bg(ui.card, 13, ui.line));
        searchBar.addView(ui.icon("search", ui.muted, 19));
        query = new EditText(this);
        query.setHint("Search this archive");
        query.setContentDescription("Search " + title);
        query.setSingleLine(true);
        query.setTextSize(14);
        query.setTextColor(ui.ink);
        query.setHintTextColor(ui.muted);
        query.setBackground(null);
        query.setMinHeight(ui.dp(48));
        query.setPadding(ui.dp(10), ui.dp(10), ui.dp(4), ui.dp(10));
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBar.addView(query, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        searchBar.addView(ui.iconButton("next", "Search archive", v -> search(query.getText().toString())));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.setMargins(ui.dp(20), ui.dp(5), ui.dp(20), ui.dp(14));
        root.addView(searchBar, searchParams);

        results = ui.column();
        results.setPadding(ui.dp(20), ui.dp(6), ui.dp(20), ui.dp(12));
        results.setBackgroundColor(ui.card);
        resultsScroll = new ScrollView(this);
        resultsScroll.setBackgroundColor(ui.card);
        resultsScroll.addView(results);
        resultsScroll.setVisibility(View.GONE);
        root.addView(resultsScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        ws.setTextZoom(AppSettings.textZoom(this)); // reading text size from settings
        if (ui.dark) darken(ws); // dark theme: let WebView darken the archive's own (light) pages
        ws.setDomStorageEnabled(true); // some archives are web apps that need localStorage (freeCodeCamp was blank without it)
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 3));
        setContentView(root);
        BackCompat.register(this, this::handleBack);
        try {
            archive = new Archive(f.getAbsolutePath());
            client = new ZimWebClient(archive);
            client.onPdf = this::openPdf;
            client.darkPages = ui.dark;
            client.onPageFinished = () -> {
                contentsOpen = false;
                web.evaluateJavascript("window.arkHasContents&&window.arkHasContents()",
                        v -> contents.setVisibility("true".equals(v) ? View.VISIBLE : View.GONE));
            };
            web.setWebViewClient(client);
            web.loadUrl(client.mainUrl());
        } catch (Exception e) {
            TextView error = ui.text("Could not open " + f.getName() + ": " + e.getMessage(), 14, ui.ink, false);
            error.setPadding(ui.dp(22), ui.dp(15), ui.dp(22), ui.dp(18));
            root.addView(error);
        }
        query.setOnEditorActionListener((v, id, ev) -> { search(query.getText().toString()); return true; });
        root.requestFocus();
    }

    private void search(String text) {
        results.removeAllViews();
        if (text.trim().isEmpty() || archive == null) { resultsScroll.setVisibility(View.GONE); return; }
        query.clearFocus();
        InputMethodManager keyboard = getSystemService(InputMethodManager.class);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(query.getWindowToken(), 0);
        int n = 0;
        // libzim's Java binding can report hasNext() on an empty result set and then throw while reading the
        // entry ("Cannot get entry for end iterator"); zimgit archives, which have no title index, do this.
        // Treat that as the end of the list, log it, and keep the search usable.
        try {
            if (archive.hasFulltextIndex()) {
                Search s = new Searcher(archive).search(new Query(text));
                SearchIterator it = s.getResults(0, 25);
                while (it.hasNext()) {
                    try {
                        it.next();
                        addResult(it.getTitle(), it.getPath());
                        n++;
                    } catch (RuntimeException end) {
                        android.util.Log.w("EstudelyArk", "full-text results ended early", end);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.w("EstudelyArk", "full-text search failed", e);
        }
        if (n == 0) { // fall back to title suggestions
            try {
                SuggestionIterator it = new SuggestionSearcher(archive).suggest(text).getResults(0, 25);
                while (it.hasNext()) {
                    try {
                        SuggestionItem s = it.next();
                        addResult(s.getTitle(), s.getPath());
                        n++;
                    } catch (RuntimeException end) {
                        android.util.Log.w("EstudelyArk", "suggestions ended early", end);
                        break;
                    }
                }
            } catch (Exception e) {
                android.util.Log.w("EstudelyArk", "suggestion search failed", e);
            }
        }
        if (n == 0) addResult("Nothing found. Try another word.", null);
        resultsScroll.setVisibility(View.VISIBLE);
    }

    private void addResult(String title, String path) {
        if (results.getChildCount() > 0) results.addView(ui.divider());
        TextView result = ui.text(title, 16, path != null ? ui.blue : ui.muted, false);
        result.setMinHeight(ui.dp(52));
        result.setGravity(Gravity.CENTER_VERTICAL);
        result.setPadding(ui.dp(4), ui.dp(14), ui.dp(4), ui.dp(14));
        if (path != null) ui.clickable(result, ui.card, 8, v -> {
            resultsScroll.setVisibility(View.GONE);
            // A result can itself be a PDF (some archives index their attachments). The WebView can't show one,
            // so hand it to the PDF viewer as a tapped link inside a page would be.
            if (isPdf(path)) openPdf(path);
            else web.loadUrl("https://" + ZimWebClient.HOST + "/" + Uri.encode(path, "/"));
        });
        results.addView(result);
    }

    private boolean isPdf(String path) {
        try {
            return archive != null && archive.hasEntryByPath(path)
                    && archive.getEntryByPath(path).getItem(true).getMimetype().startsWith("application/pdf");
        } catch (Exception e) { return false; }
    }

    /** Copies a PDF out of the archive to the cache and opens it in the PDF viewer. */
    private void openPdf(String path) {
        try {
            byte[] data = archive.getEntryByPath(path).getItem(true).getData().getData();
            File dir = new File(getCacheDir(), "zim-pdf");
            dir.mkdirs();
            File out = new File(dir, path.replaceAll("[^A-Za-z0-9._-]", "_"));
            java.nio.file.Files.write(out.toPath(), data);
            startActivity(new android.content.Intent(this, PdfActivity.class).putExtra("path", out.getAbsolutePath()));
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "Could not open the PDF: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    /** Android 8-12 path; Android 13+ back gestures go through BackCompat (OnBackInvokedCallback). */
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { handleBack(); }
    @SuppressWarnings("deprecation")
    private static void darken(WebSettings ws) {
        if (android.os.Build.VERSION.SDK_INT >= 33) ws.setAlgorithmicDarkeningAllowed(true);
        else if (android.os.Build.VERSION.SDK_INT >= 29) ws.setForceDark(WebSettings.FORCE_DARK_ON);
    }

    private void setContentsOpen(boolean open) {
        contentsOpen = open;
        web.evaluateJavascript("window.arkToggleContents&&window.arkToggleContents(" + open + ")", null);
        contents.setContentDescription(open ? "Close contents" : "Contents");
    }

    private void handleBack() {
        if (contentsOpen) { setContentsOpen(false); return; }
        if (resultsScroll.getVisibility() == View.VISIBLE) resultsScroll.setVisibility(View.GONE);
        else if (web.canGoBack()) web.goBack();
        else finish();
    }
}
