package cc.esdy.ark;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Contents of one downloaded module: archives (ZIM), PDFs grouped by folder, or the map. */
public class ModuleActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LibraryEngine engine = LibraryEngine.get(this);
        ModuleCatalog.Module m = engine.catalog.find(getIntent().getStringExtra("module"));
        if (m == null) { finish(); return; }
        File dir = new File(engine.packDir(), m.folder);
        if (new File(dir, "index.html").isFile() && new File(dir, "maps.json").isFile()) {
            startActivity(new Intent(this, MapActivity.class).putExtra("folder", m.folder));
            finish();
            return;
        }
        setTitle(m.title);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        MainActivity.edgeToEdge(this, root);
        TextView head = new TextView(this);
        head.setText(m.title);
        head.setTextSize(22);
        head.setTypeface(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD);
        head.setPadding(32, 24, 32, 8);
        root.addView(head);
        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(32, 16, 32, 32);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        // Group files by the folder they're in (e.g. one Hesperian book per folder).
        Map<String, List<File>> groups = new TreeMap<>();
        collect(dir, dir, groups);
        for (Map.Entry<String, List<File>> g : groups.entrySet()) {
            if (!g.getKey().isEmpty()) {
                TextView h = new TextView(this);
                h.setText(g.getKey().replace('-', ' '));
                h.setTypeface(Typeface.SERIF, Typeface.BOLD);
                h.setTextSize(16);
                h.setPadding(0, 28, 0, 6);
                list.addView(h);
            }
            for (File f : g.getValue()) {
                TextView t = new TextView(this);
                t.setText(label(f));
                t.setTextSize(16);
                t.setPadding(0, 14, 0, 14);
                t.setTextColor(0xff2b4c7e);
                t.setOnClickListener(v -> open(f));
                list.addView(t);
            }
        }
    }

    private static void collect(File base, File d, Map<String, List<File>> groups) {
        File[] fs = d.listFiles();
        if (fs == null) return;
        java.util.Arrays.sort(fs);
        for (File f : fs) {
            if (f.isDirectory()) collect(base, f, groups);
            else if (f.getName().endsWith(".zim") || f.getName().endsWith(".pdf")) {
                String key = f.getParentFile().equals(base) ? "" : base.toPath().relativize(f.getParentFile().toPath()).toString();
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
            }
        }
    }

    /** "wikipedia_en_medicine_nopic_2026-10.zim" -> "Wikipedia en medicine nopic (2026-10)"; PDFs keep their name. */
    static String label(File f) {
        String n = f.getName().replaceFirst("\\.(zim|pdf)$", "");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.*)_(\\d{4}-\\d{2})$").matcher(n);
        String date = "";
        if (m.matches()) { n = m.group(1); date = " (" + m.group(2) + ")"; }
        n = n.replace('_', ' ').replace('-', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1) + date;
    }

    private void open(File f) {
        Class<?> c = f.getName().endsWith(".pdf") ? PdfActivity.class : ZimActivity.class;
        startActivity(new Intent(this, c).putExtra("path", f.getAbsolutePath()));
    }
}
