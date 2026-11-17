package cc.esdy.ark;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
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
public class ZimActivity extends Activity {
    private Archive archive; // kept alive while the WebView reads from it
    private WebView web;
    private LinearLayout results;
    private ScrollView resultsScroll;
    private ZimWebClient client;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        new JNIKiwix(this);
        File f = new File(getIntent().getStringExtra("path"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        MainActivity.edgeToEdge(this, root);
        EditText q = new EditText(this);
        q.setHint("Search " + ModuleActivity.label(f));
        q.setSingleLine(true);
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        root.addView(q);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        resultsScroll = new ScrollView(this);
        resultsScroll.addView(results);
        resultsScroll.setVisibility(View.GONE);
        root.addView(resultsScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 3));
        setContentView(root);
        try {
            archive = new Archive(f.getAbsolutePath());
            client = new ZimWebClient(archive);
            client.onPdf = this::openPdf;
            web.setWebViewClient(client);
            web.loadUrl(client.mainUrl());
        } catch (Exception e) {
            TextView t = new TextView(this);
            t.setText("Could not open " + f.getName() + ": " + e.getMessage());
            root.addView(t);
        }
        q.setOnEditorActionListener((v, id, ev) -> { search(q.getText().toString()); return true; });
    }

    private void search(String text) {
        results.removeAllViews();
        if (text.trim().isEmpty() || archive == null) { resultsScroll.setVisibility(View.GONE); return; }
        int n = 0;
        try {
            if (archive.hasFulltextIndex()) {
                Search s = new Searcher(archive).search(new Query(text));
                SearchIterator it = s.getResults(0, 25);
                while (it.hasNext()) { it.next(); addResult(it.getTitle(), it.getPath()); n++; }
            }
            if (n == 0) { // fall back to title suggestions
                SuggestionIterator it = new SuggestionSearcher(archive).suggest(text).getResults(0, 25);
                while (it.hasNext()) { SuggestionItem s = it.next(); addResult(s.getTitle(), s.getPath()); n++; }
            }
        } catch (Exception e) {
            addResult("Search failed: " + e.getMessage(), null);
        }
        if (n == 0) addResult("Nothing found.", null);
        resultsScroll.setVisibility(View.VISIBLE);
    }

    private void addResult(String title, String path) {
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(16);
        t.setPadding(24, 14, 24, 14);
        if (path != null) {
            t.setTextColor(0xff2b4c7e);
            t.setOnClickListener(v -> {
                resultsScroll.setVisibility(View.GONE);
                web.loadUrl("https://" + ZimWebClient.HOST + "/" + Uri.encode(path, "/"));
            });
        }
        results.addView(t);
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

    @Override public void onBackPressed() {
        if (resultsScroll.getVisibility() == View.VISIBLE) resultsScroll.setVisibility(View.GONE);
        else if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
