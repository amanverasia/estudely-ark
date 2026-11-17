package cc.esdy.ark;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.kiwix.libkiwix.JNIKiwix;

/** Library screen: pick modules, download them, open the ones that are ready. */
public class MainActivity extends Activity {
    private LibraryEngine engine;
    private LinearLayout list;
    private TextView summary, log;
    private Button download;
    private final List<Row> rows = new ArrayList<>();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final java.util.function.Consumer<String> logListener = s -> ui.post(() -> {
        if (!BuildConfig.DEBUG) { log.setText(s); return; } // release: just the latest message
        log.append(s + "\n");
        if (log.getLineCount() > 200) log.setText(log.getText().subSequence(log.getText().length() / 2, log.getText().length()));
    });

    static class Row { ModuleCatalog.Module m; CheckBox box; TextView status; android.widget.ProgressBar bar; }

    static void edgeToEdge(Activity a, View root) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsets.CONSUMED;
        });
    }

    static String gb(long bytes) {
        return bytes >= 1e9 ? String.format("%.1f GB", bytes / 1e9) : String.format("%.0f MB", bytes / 1e6);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        new JNIKiwix(this);
        engine = LibraryEngine.get(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        edgeToEdge(this, root);
        TextView title = new TextView(this);
        title.setText("Estudely Ark");
        title.setTextSize(24);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        title.setPadding(32, 24, 32, 0);
        root.addView(title);
        summary = new TextView(this);
        summary.setPadding(32, 4, 32, 12);
        root.addView(summary);

        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 3));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        download = new Button(this);
        download.setText("Download selected");
        download.setAllCaps(false);
        download.setOnClickListener(v -> startDownload());
        bar.addView(download, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button tidy = new Button(this);
        tidy.setText("Free space");
        tidy.setAllCaps(false);
        tidy.setOnClickListener(v -> confirmRemoveUnselected());
        bar.addView(tidy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(bar);
        CheckBox mobile = new CheckBox(this);
        mobile.setText("Download over mobile data too");
        mobile.setPadding(8, 0, 32, 0);
        mobile.setChecked(engine.mobileDataAllowed());
        mobile.setOnCheckedChangeListener((v, on) -> engine.setMobileDataAllowed(on));
        root.addView(mobile);
        CheckBox share = new CheckBox(this);
        share.setText("Share with others on Wi-Fi while charging");
        share.setPadding(8, 0, 32, 0);
        share.setChecked(engine.sharingOn());
        share.setOnCheckedChangeListener((v, on) -> { engine.setSharing(on); SeedJob.schedule(this, on); refresh(); });
        root.addView(share);

        log = new TextView(this);
        log.setTextSize(11);
        log.setPadding(32, 8, 32, 8);
        log.setTextIsSelectable(true);
        ScrollView logScroll = new ScrollView(this);
        logScroll.addView(log);
        if (BuildConfig.DEBUG) root.addView(logScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        else root.addView(logScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);

        buildRows();
        // A force-stop (or "clear data" elsewhere) cancels scheduled jobs; re-arm sharing if it is switched on.
        if (engine.sharingOn()) SeedJob.schedule(this, true);
        if (BuildConfig.DEBUG) handleTestIntent(getIntent());
    }

    private void buildRows() {
        list.removeAllViews();
        rows.clear();
        Set<String> sel = engine.selected();
        String lastGroup = "";
        for (ModuleCatalog.Module m : engine.catalog.modules) {
            String group = m.optional ? "Optional" : "Core";
            if (!group.equals(lastGroup)) {
                TextView h = new TextView(this);
                h.setText(group.toUpperCase());
                h.setTextSize(12);
                h.setLetterSpacing(0.08f);
                h.setPadding(32, 20, 32, 4);
                list.addView(h);
                lastGroup = group;
            }
            Row r = new Row();
            r.m = m;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(16, 8, 32, 8);
            r.box = new CheckBox(this);
            r.box.setChecked(sel.contains(m.id));
            r.box.setOnCheckedChangeListener((v, on) -> onToggle());
            row.addView(r.box);
            LinearLayout text = new LinearLayout(this);
            text.setOrientation(LinearLayout.VERTICAL);
            TextView t = new TextView(this);
            t.setText(m.title + "  ·  " + gb(m.bytes));
            t.setTextSize(16);
            text.addView(t);
            TextView d = new TextView(this);
            d.setText(m.description);
            d.setTextSize(12);
            text.addView(d);
            r.status = new TextView(this);
            r.status.setTextSize(12);
            text.addView(r.status);
            r.bar = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            r.bar.setMax(1000);
            r.bar.setVisibility(View.GONE);
            text.addView(r.bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.setOnClickListener(v -> openModule(r));
            list.addView(row);
            rows.add(r);
        }
        refresh();
    }

    private void onToggle() {
        Set<String> ids = new HashSet<>();
        for (Row r : rows) if (r.box.isChecked()) ids.add(r.m.id);
        engine.setSelected(ids);
        refresh();
    }

    private void refresh() {
        boolean dl = DownloadService.isRunning();
        long[] done = engine.isRunning() ? engine.moduleDone(engine.catalog.modules) : null;
        long chosen = 0;
        for (Row r : rows) {
            if (r.box.isChecked()) chosen += r.m.bytes;
            LibraryEngine.State s = engine.state(r.m);
            String text;
            switch (s) {
                case READY: text = "✓ Ready. Tap to open"; break;
                case FAILED: text = "✗ Check failed. Download again to repair"; break;
                case NOT_SELECTED: text = ""; break;
                default:
                    int idx = engine.catalog.modules.indexOf(r.m);
                    if (done != null && idx >= 0) {
                        text = "Downloading: " + gb(done[idx]) + " of " + gb(r.m.bytes);
                        r.bar.setProgress((int) (1000 * done[idx] / Math.max(1, r.m.bytes)));
                    } else text = dl ? "Waiting to download" : "Not downloaded yet. Tap Download";
            }
            r.bar.setVisibility(done != null && s != LibraryEngine.State.READY && s != LibraryEngine.State.NOT_SELECTED ? View.VISIBLE : View.GONE);
            r.status.setText(text);
        }
        String shared = engine.sharingOn() ? " · shared " + gb(engine.uploaded()) + (SeedJob.isActive() ? " (sharing now)" : "") : "";
        if (chosen == 0) summary.setText("Tick the parts you want, then tap Download. After that everything works without internet. Free on this phone: " + gb(LibraryEngine.allocatableBytes(this)));
        else summary.setText("Selected " + gb(chosen) + " · free on this phone " + gb(LibraryEngine.allocatableBytes(this)) + shared);
        download.setEnabled(!dl);
        download.setText(dl ? "Downloading…" : "Download selected");
    }

    private final Runnable ticker = new Runnable() {
        @Override public void run() { refresh(); ui.postDelayed(this, 2000); }
    };

    @Override protected void onResume() {
        super.onResume();
        engine.addListener(logListener);
        if (engine.pausedByLimit() && !DownloadService.isRunning()) {
            // Opening the app resets Android's time budget, so carry on where the limit stopped us.
            engine.setPausedByLimit(false);
            getSystemService(android.app.NotificationManager.class).cancel(2);
            engine.log("Continuing the paused download");
            startForegroundService(new Intent(this, DownloadService.class));
        }
        ui.post(ticker);
    }

    @Override protected void onPause() {
        engine.removeListener(logListener);
        ui.removeCallbacks(ticker);
        super.onPause();
    }

    private void startDownload() {
        Set<String> ids = engine.selected();
        if (ids.isEmpty()) { toast("Tick the parts you want first."); return; }
        long need = engine.bytesStillNeeded(ids), free = LibraryEngine.allocatableBytes(this);
        if (need > free) {
            new AlertDialog.Builder(this).setTitle("Not enough space")
                .setMessage("These parts need " + gb(need) + " more, but this phone has " + gb(free) + " free. Untick some parts, or free space and try again.")
                .setPositiveButton("OK", null).show();
            return;
        }
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
        startForegroundService(new Intent(this, DownloadService.class));
        refresh();
    }

    private void confirmRemoveUnselected() {
        new AlertDialog.Builder(this).setTitle("Remove unticked parts?")
            .setMessage("Deletes downloaded files of parts that are not ticked, to free space.")
            .setPositiveButton("Remove", (d, w) -> new Thread(() -> { engine.deleteUnselected(); ui.post(this::refresh); }).start())
            .setNegativeButton("Cancel", null).show();
    }

    private void openModule(Row r) {
        if (engine.state(r.m) != LibraryEngine.State.READY) { toast(r.m.title + " is not downloaded and checked yet."); return; }
        startActivity(new Intent(this, ModuleActivity.class).putExtra("module", r.m.id));
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        if (BuildConfig.DEBUG) handleTestIntent(i);
    }

    private void toast(String s) { android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show(); }

    /** Debug builds only: adb test hooks. --es select "id1,id2" --ez download true --ez webSeedOnly true --es directPeer host:port --es openMap|openPdf|openZim X */
    private void handleTestIntent(Intent i) {
        String sel = i.getStringExtra("select");
        if (sel != null) {
            Set<String> ids = new HashSet<>();
            for (String s : sel.split(",")) if (!s.isEmpty()) ids.add(s);
            engine.setSelected(ids);
            buildRows();
        }
        // Readers, for device tests without big downloads: openMap = folder under the pack, openPdf/openZim = file path.
        String map = i.getStringExtra("openMap"), pdf = i.getStringExtra("openPdf"), zim = i.getStringExtra("openZim");
        if (map != null) startActivity(new Intent(this, MapActivity.class).putExtra("folder", map));
        if (pdf != null) startActivity(new Intent(this, PdfActivity.class).putExtra("path", pdf));
        if (zim != null) startActivity(new Intent(this, ZimActivity.class).putExtra("path", zim));
        if (i.getBooleanExtra("download", false)) {
            Intent s = new Intent(this, DownloadService.class);
            s.putExtra("webSeedOnly", i.getBooleanExtra("webSeedOnly", false));
            s.putExtra("directPeer", i.getStringExtra("directPeer"));
            startForegroundService(s);
        }
    }
}
