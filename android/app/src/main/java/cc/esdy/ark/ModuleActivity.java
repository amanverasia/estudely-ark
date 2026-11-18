package cc.esdy.ark;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** A downloaded subject's books and documents, presented as a native, offline reading shelf. */
public class ModuleActivity extends ArkActivity {
    private ArkUi ui;
    private LinearLayout list;
    private TextView documentCount;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new ArkUi(this);
        ui.applyWindow(this);
        LibraryEngine engine = LibraryEngine.get(this);
        if (engine.isMoving() || !engine.storageAvailable()) {
            String message = engine.moveNeedsRecovery() ? engine.recoveryMessage()
                    : engine.isMoving() ? "Wait for the library move to finish."
                    : engine.storageName() + " is unavailable. Reinsert it to read.";
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        ModuleCatalog.Module module = engine.catalog.find(getIntent().getStringExtra("module"));
        if (module == null) { finish(); return; }
        setTitle(module.title);

        LinearLayout root = ui.column();
        root.setBackgroundColor(ui.paper);
        MainActivity.edgeToEdge(this, root);
        LinearLayout toolbar = ui.row();
        toolbar.setPadding(ui.dp(8), ui.dp(4), ui.dp(16), ui.dp(4));
        toolbar.addView(ui.iconButton("back", "Back to library", v -> finish()));
        TextView library = ui.text("Library", 14, ui.blue, true);
        library.setPadding(ui.dp(4), 0, 0, 0);
        library.setOnClickListener(v -> finish());
        library.setMinHeight(ui.dp(48));
        library.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(library);
        root.addView(toolbar);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        list = ui.column();
        list.setPadding(ui.dp(22), ui.dp(14), ui.dp(22), ui.dp(32));
        scroll.addView(list, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        TextView heading = ui.text(ui.shortTitle(module), 30, ui.ink, true);
        heading.setLetterSpacing(-.035f);
        list.addView(heading);
        TextView description = ui.text(module.description, 13, ui.muted, false);
        description.setPadding(0, ui.dp(10), 0, ui.dp(19));
        list.addView(description);

        boolean verified = engine.state(module) == LibraryEngine.State.READY;
        LinearLayout status = ui.row();
        status.setPadding(ui.dp(12), ui.dp(11), ui.dp(12), ui.dp(11));
        status.setBackground(ui.bg(ui.soft, 11));
        status.addView(ui.icon(verified ? "shield" : "download", ui.blue, 18));
        TextView statusText = ui.text(verified ? "Saved and checked · " + MainActivity.gb(module.bytes)
                : "Download and check this subject before reading", 12, ui.blue, false);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        statusParams.leftMargin = ui.dp(9);
        status.addView(statusText, statusParams);
        list.addView(status);

        if (!verified) {
            TextView missing = ui.text("Return to Subjects to download these files. Your library checks every file before it opens.", 14, ui.muted, false);
            missing.setPadding(0, ui.dp(22), 0, ui.dp(18));
            list.addView(missing);
            list.addView(ui.button("Back to library", true, v -> finish()));
            return;
        }

        File directory = new File(engine.packDir(), module.folder);
        if (new File(directory, "index.html").isFile() && new File(directory, "maps.json").isFile()) {
            startActivity(new Intent(this, MapActivity.class).putExtra("folder", module.folder));
            finish();
            return;
        }

        documentCount = ui.text("Finding your books…", 12, ui.muted, false);
        documentCount.setPadding(0, ui.dp(21), 0, ui.dp(5));
        list.addView(documentCount);
        // A subject can contain hundreds of chapter PDFs. Scan names away from the main thread.
        new Thread(() -> {
            Map<String, List<File>> groups = new TreeMap<>();
            collect(directory, directory, groups);
            runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) showFiles(groups); });
        }, "subject-books").start();
    }

    private void showFiles(Map<String, List<File>> groups) {
        int pdfs = 0, archives = 0;
        for (List<File> files : groups.values()) for (File file : files) {
            if (file.getName().endsWith(".pdf")) pdfs++; else archives++;
        }
        if (pdfs + archives == 0) {
            documentCount.setText("No readable books found");
            TextView empty = ui.text("These files may have been removed. Download the subject again from Subjects to restore them.", 14, ui.muted, false);
            empty.setPadding(0, ui.dp(15), 0, ui.dp(18));
            list.addView(empty);
            return;
        }
        List<String> kinds = new ArrayList<>();
        if (archives > 0) kinds.add(archives + (archives == 1 ? " searchable archive" : " searchable archives"));
        if (pdfs > 0) kinds.add(pdfs + (pdfs == 1 ? " PDF" : " PDFs"));
        documentCount.setText(String.join(" · ", kinds) + " · ready offline");

        for (Map.Entry<String, List<File>> group : groups.entrySet()) {
            String title = group.getKey().isEmpty() ? "Books and reference" : group.getKey().replace('-', ' ').replace('_', ' ');
            TextView groupTitle = ui.text(title, 16, ui.ink, true);
            groupTitle.setPadding(0, ui.dp(22), 0, ui.dp(11));
            list.addView(groupTitle);
            LinearLayout panel = ui.column();
            panel.setBackground(ui.bg(ui.card, 14, ui.line));
            for (int i = 0; i < group.getValue().size(); i++) {
                if (i > 0) {
                    View divider = ui.divider();
                    LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1));
                    dp.leftMargin = ui.dp(62); dp.rightMargin = ui.dp(13);
                    panel.addView(divider, dp);
                }
                panel.addView(fileRow(group.getValue().get(i)));
            }
            list.addView(panel);
        }
    }

    private View fileRow(File file) {
        boolean pdf = file.getName().endsWith(".pdf");
        LinearLayout row = ui.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(13), ui.dp(13), ui.dp(12), ui.dp(13));
        row.setMinimumHeight(ui.dp(72));
        android.widget.FrameLayout symbol = new android.widget.FrameLayout(this);
        symbol.setBackground(ui.bg(ui.soft, 10));
        View glyph = ui.icon(pdf ? "pdf" : "book", ui.blue, 21);
        symbol.addView(glyph, new android.widget.FrameLayout.LayoutParams(ui.dp(21), ui.dp(21), Gravity.CENTER));
        row.addView(symbol, new LinearLayout.LayoutParams(ui.dp(36), ui.dp(40)));

        LinearLayout copy = ui.column();
        copy.addView(ui.text(label(file), 14, ui.ink, true));
        TextView detail = ui.text((pdf ? "PDF" : "Searchable archive") + " · " + MainActivity.gb(file.length()), 11, ui.muted, false);
        detail.setPadding(0, ui.dp(5), 0, 0);
        copy.addView(detail);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        copyParams.leftMargin = ui.dp(12); copyParams.rightMargin = ui.dp(8);
        row.addView(copy, copyParams);
        row.addView(ui.icon("next", ui.muted, 18));
        ui.clickable(row, ui.card, 12, v -> open(file));
        row.setContentDescription(label(file) + ", " + (pdf ? "PDF" : "searchable archive") + ", " + MainActivity.gb(file.length()) + ", open");
        row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        copy.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        return row;
    }

    private static void collect(File base, File directory, Map<String, List<File>> groups) {
        File[] files = directory.listFiles();
        if (files == null) return;
        java.util.Arrays.sort(files);
        for (File file : files) {
            if (file.isDirectory()) collect(base, file, groups);
            else if (file.getName().endsWith(".zim") || file.getName().endsWith(".pdf")) {
                String key = file.getParentFile().equals(base) ? "" : base.toPath().relativize(file.getParentFile().toPath()).toString();
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(file);
            }
        }
    }

    /** "wikipedia_en_medicine_nopic_2026-10.zim" -> "Wikipedia en medicine nopic (2026-10)"; PDFs keep their name. */
    static String label(File file) {
        String name = file.getName().replaceFirst("\\.(zim|pdf)$", "");
        java.util.regex.Matcher match = java.util.regex.Pattern.compile("^(.*)_(\\d{4}-\\d{2})$").matcher(name);
        String date = "";
        if (match.matches()) { name = match.group(1); date = " (" + match.group(2) + ")"; }
        name = name.replace('_', ' ').replace('-', ' ');
        return name.isEmpty() ? file.getName() : Character.toUpperCase(name.charAt(0)) + name.substring(1) + date;
    }

    private void open(File file) {
        Class<?> reader = file.getName().endsWith(".pdf") ? PdfActivity.class : ZimActivity.class;
        startActivity(new Intent(this, reader).putExtra("path", file.getAbsolutePath()));
    }
}
