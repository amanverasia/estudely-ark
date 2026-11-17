package cc.esdy.ark;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.view.WindowManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.kiwix.libkiwix.JNIKiwix;

/** Bookshelf, subject catalogue and live downloads. LibraryEngine remains the source of truth. */
public class MainActivity extends Activity {
    private enum Screen { LIBRARY, SUBJECTS, DOWNLOADS }
    private LibraryEngine engine;
    private ArkUi style;
    private SharedPreferences presentation;
    private Screen screen = Screen.LIBRARY;
    private boolean savedOnly, alive, storageBusy;
    private final Set<String> pending = new HashSet<>();
    private final Map<Screen, Integer> scrollPositions = new HashMap<>();
    private LinearLayout root, body, footer, navigation;
    private ScrollView scroll;
    private Dialog sheetDialog;
    private Snapshot snapshot;
    private String renderedKey = "";
    private final List<Consumer<Snapshot>> bindings = new ArrayList<>();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private final Consumer<String> listener = message -> ui.post(this::requestSnapshot);

    private static final class Snapshot {
        final List<ModuleCatalog.Module> ready = new ArrayList<>(), incomplete = new ArrayList<>();
        final Set<String> selected = new HashSet<>();
        final Map<String, LibraryEngine.State> states = new LinkedHashMap<>();
        final Map<String, Long> done = new HashMap<>();
        long free, uploaded;
        boolean running, paused, limited, wifi, mobile, sharing;
        String checking, message;
        long bytes(ModuleCatalog.Module m) {
            return states.get(m.id) == LibraryEngine.State.READY ? m.bytes : Math.min(m.bytes, Math.max(0, done.getOrDefault(m.id, 0L)));
        }
    }

    static void edgeToEdge(Activity a, View root) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsets.CONSUMED;
        });
    }

    static String gb(long bytes) {
        return bytes >= 1e9 ? String.format(Locale.getDefault(), "%.1f GB", bytes / 1e9)
                : String.format(Locale.getDefault(), "%.0f MB", bytes / 1e6);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        new JNIKiwix(this);
        engine = LibraryEngine.get(this);
        style = new ArkUi(this);
        style.applyWindow(this);
        presentation = getPreferences(MODE_PRIVATE);
        pending.addAll(presentation.getStringSet("subjectDraft", new HashSet<>()));
        pending.removeIf(id -> engine.catalog.find(id) == null);
        if (state != null) {
            try { screen = Screen.valueOf(state.getString("screen", "LIBRARY")); } catch (IllegalArgumentException ignored) {}
            savedOnly = state.getBoolean("savedOnly");
            for (Screen s : Screen.values()) scrollPositions.put(s, state.getInt("scroll:" + s.name()));
        }
        snapshot = readSnapshot();
        buildShell();
        render(true);
        if (engine.sharingOn()) SeedJob.schedule(this, true);
        if (BuildConfig.DEBUG) handleTestIntent(getIntent());
    }

    private void buildShell() {
        root = style.column();
        root.setBackgroundColor(style.paper);
        edgeToEdge(this, root);
        LinearLayout header = style.row();
        header.setPadding(dp(22), dp(3), dp(12), dp(3));
        TextView brand = style.text("ark.", 25, style.ink, true);
        brand.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        SpannableString wordmark = new SpannableString("ark.");
        wordmark.setSpan(new ForegroundColorSpan(style.blue), 3, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        brand.setText(wordmark);
        header.addView(brand);
        TextView by = style.text("Estudely", 11, style.muted, false);
        by.setPadding(dp(10), dp(5), 0, 0);
        header.addView(by, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(style.iconButton("settings", "Library settings", v -> showSettings()));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(58)));
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        body = style.column();
        body.setPadding(dp(22), dp(12), dp(22), dp(18));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        footer = style.column();
        footer.setPadding(dp(22), dp(8), dp(22), dp(12));
        root.addView(footer);
        navigation = style.row();
        navigation.setPadding(dp(5), dp(6), dp(5), dp(6));
        navigation.setBackground(style.bg(style.card, 20, style.line));
        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(-1, -2);
        navParams.setMargins(dp(13), dp(3), dp(13), dp(11));
        root.addView(navigation, navParams);
        setContentView(root);
    }

    private Snapshot readSnapshot() {
        Snapshot s = new Snapshot();
        s.selected.addAll(engine.selected());
        long[] done = engine.moduleDone(engine.catalog.modules);
        for (int i = 0; i < engine.catalog.modules.size(); i++) {
            ModuleCatalog.Module m = engine.catalog.modules.get(i);
            LibraryEngine.State state = engine.state(m);
            s.states.put(m.id, state);
            s.done.put(m.id, done[i]);
            if (state == LibraryEngine.State.READY) s.ready.add(m);
            else if (s.selected.contains(m.id)) s.incomplete.add(m);
        }
        s.running = DownloadService.isRunning(); s.paused = engine.pausedByUser(); s.limited = engine.pausedByLimit();
        s.wifi = LibraryEngine.unmetered(this); s.mobile = engine.mobileDataAllowed();
        s.sharing = engine.sharingOn(); s.uploaded = engine.uploaded();
        s.free = LibraryEngine.allocatableBytes(this); s.checking = engine.checkingModule(); s.message = engine.latestMessage();
        if (s.checking == null && s.states.containsValue(LibraryEngine.State.CHECKING)) s.checking = "torrent";
        return s;
    }

    private void requestSnapshot() {
        if (!alive || isDestroyed() || !refreshing.compareAndSet(false, true)) return;
        background.execute(() -> {
            try {
                Snapshot next = readSnapshot();
                ui.post(() -> {
                    refreshing.set(false);
                    if (isDestroyed()) return;
                    snapshot = next;
                    if (pending.removeAll(next.selected)) saveDraft();
                    render(false);
                });
            } catch (RuntimeException e) { ui.post(() -> refreshing.set(false)); }
        });
    }

    private String layoutKey() {
        return screen + ":" + savedOnly + ":" + snapshot.states + ":" + snapshot.running + ":" + snapshot.paused
                + ":" + snapshot.wifi + ":" + snapshot.mobile + ":" + snapshot.checking + ":" + pending + ":" + storageBusy;
    }

    private void render(boolean force) {
        if (body == null || isDestroyed()) return;
        String key = layoutKey();
        if (!force && key.equals(renderedKey)) { updateBindings(); return; }
        int y = scrollPositions.getOrDefault(screen, scroll.getScrollY());
        if (!renderedKey.isEmpty()) y = scroll.getScrollY();
        renderedKey = key;
        bindings.clear(); body.removeAllViews(); footer.removeAllViews(); navigation.removeAllViews();
        footer.setVisibility(screen == Screen.SUBJECTS && !savedOnly ? View.VISIBLE : View.GONE);
        if (screen == Screen.LIBRARY) buildLibrary();
        else if (screen == Screen.SUBJECTS) buildSubjects();
        else buildDownloads();
        buildNavigation();
        updateBindings();
        final int restore = y;
        scroll.post(() -> scroll.scrollTo(0, restore));
    }

    private void updateBindings() { for (Consumer<Snapshot> binding : bindings) binding.accept(snapshot); }

    private void show(Screen next) {
        scrollPositions.put(screen, scroll.getScrollY());
        screen = next;
        renderedKey = "";
        render(true);
        scroll.post(() -> scroll.scrollTo(0, scrollPositions.getOrDefault(next, 0)));
    }

    private void heading(String text, String description) {
        TextView title = style.text(text, 32, style.ink, true);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        title.setLetterSpacing(-.035f);
        body.addView(title);
        if (description != null) {
            TextView intro = style.text(description, 12, style.muted, false);
            add(body, intro, 9);
        }
    }

    private void buildLibrary() {
        heading("Your bookshelf.", null);
        long stored = 0; for (ModuleCatalog.Module m : snapshot.ready) stored += m.bytes;
        TextView meta = style.text(snapshot.ready.size() + " " + plural(snapshot.ready.size(), "subject") + " ready"
                + (snapshot.ready.isEmpty() ? "" : " · " + gb(stored) + " on this phone"), 12, style.muted, false);
        add(body, meta, 10);
        if (snapshot.ready.isEmpty()) {
            LinearLayout empty = style.column();
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(24), dp(34), dp(24), dp(30));
            empty.setBackground(style.bg(style.soft, 18));
            empty.addView(style.icon("book", style.blue, 54));
            TextView welcome = style.text("A little room for knowledge.", 20, style.ink, true);
            welcome.setGravity(Gravity.CENTER); add(empty, welcome, 18);
            TextView copy = style.text("Choose a subject, download it once, and keep reading without internet.", 14, style.muted, false);
            copy.setGravity(Gravity.CENTER); add(empty, copy, 11);
            add(empty, style.button("Choose your first subject", true, v -> openSubjects()), 22);
            add(body, empty, 28);
        } else {
            section("Ready when you are", "All subjects", this::openSubjects);
            int columns = getResources().getConfiguration().fontScale > 1.25f
                    || getResources().getConfiguration().screenWidthDp < 340 ? 1 : 2;
            for (int i = 0; i < snapshot.ready.size(); i += columns) {
                LinearLayout row = style.row(); row.setGravity(Gravity.TOP);
                for (int j = 0; j < columns; j++) {
                    if (i + j >= snapshot.ready.size()) { row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1)); continue; }
                    ModuleCatalog.Module m = snapshot.ready.get(i + j);
                    LinearLayout tile = style.column();
                    View cover = style.cover(m, v -> openModule(m));
                    tile.addView(cover, new LinearLayout.LayoutParams(-1, -2));
                    TextView info = style.text(gb(m.bytes) + " · saved and checked", 11, style.muted, false);
                    add(tile, info, 10);
                    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
                    if (j > 0) p.leftMargin = dp(13);
                    row.addView(tile, p);
                }
                add(body, row, i == 0 ? 0 : 20);
            }
        }
        if (!snapshot.incomplete.isEmpty()) buildCompactDownload();
        add(body, style.button(snapshot.ready.isEmpty() ? "Browse all subjects" : "Find your next subject", false, v -> openSubjects()), 21);
        TextView note = style.text("Saved subjects work without internet.", 11, style.muted, false);
        note.setGravity(Gravity.CENTER); add(body, note, 12);
    }

    private void buildCompactDownload() {
        LinearLayout card = style.column(); card.setPadding(dp(13), dp(10), dp(7), dp(13));
        card.setBackground(style.bg(style.soft, 15));
        LinearLayout row = style.row();
        row.addView(style.icon(snapshot.incomplete.size() == 1 ? style.iconName(snapshot.incomplete.get(0)) : "download", style.blue, 24));
        LinearLayout copy = style.column(); copy.setPadding(dp(11), 0, 0, 0);
        copy.addView(style.text(downloadTitle(), 13, style.ink, true));
        TextView detail = style.text("", 11, style.muted, false); add(copy, detail, 4);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(style.iconButton("next", "View downloads", v -> show(Screen.DOWNLOADS)));
        card.addView(row);
        ProgressBar progress = progressBar(); add(card, progress, 10);
        bindings.add(s -> { detail.setText(downloadStatus() + " · " + gb(totalDone()) + " of " + gb(totalWanted())); progress.setProgress(percent()); });
        add(body, card, 27);
    }

    private void buildSubjects() {
        heading("More to explore.", "Choose the parts you want to keep.\nEverything else can wait.");
        LinearLayout filters = style.row(); filters.setPadding(dp(4), dp(4), dp(4), dp(4));
        filters.setBackground(style.bg(style.soft, 12));
        filter(filters, "Available · " + (engine.catalog.modules.size() - snapshot.ready.size()), !savedOnly, false);
        filter(filters, "On this phone · " + snapshot.ready.size(), savedOnly, true);
        add(body, filters, 19);
        LinearLayout catalogue = panel();
        int count = 0;
        for (ModuleCatalog.Module m : engine.catalog.modules) {
            boolean ready = snapshot.states.get(m.id) == LibraryEngine.State.READY;
            if (savedOnly != ready) continue;
            if (count++ > 0) catalogue.addView(style.divider());
            catalogue.addView(subjectRow(m, ready));
        }
        if (count == 0) {
            TextView empty = style.text("Your downloaded subjects will appear here.", 14, style.muted, false);
            empty.setPadding(dp(16), dp(24), dp(16), dp(24)); catalogue.addView(empty);
        }
        add(body, catalogue, 15);
        if (!savedOnly) {
            LinearLayout total = style.row();
            TextView chosen = style.text(pending.size() + " " + plural(pending.size(), "subject") + " selected", 12, style.ink, true);
            total.addView(chosen, new LinearLayout.LayoutParams(0, -2, 1));
            long bytes = 0; for (String id : pending) { ModuleCatalog.Module m = engine.catalog.find(id); if (m != null) bytes += m.bytes; }
            total.addView(style.text(gb(bytes), 12, style.muted, false)); footer.addView(total);
            TextView download = style.button("Add to downloads", true, v -> addDownloads());
            download.setEnabled(!pending.isEmpty() && !storageBusy); download.setAlpha(download.isEnabled() ? 1f : .45f);
            add(footer, download, 10);
            TextView note = style.text((snapshot.mobile ? "Mobile data allowed. " : "Wi-Fi only. ") + gb(snapshot.free) + " available.", 11, style.muted, false);
            note.setGravity(Gravity.CENTER); add(footer, note, 8);
        }
    }

    private View subjectRow(ModuleCatalog.Module m, boolean ready) {
        boolean active = snapshot.selected.contains(m.id) && !ready;
        boolean selected = pending.contains(m.id);
        LinearLayout row = style.row(); row.setPadding(dp(13), dp(13), dp(13), dp(13)); row.setMinimumHeight(dp(70));
        if (selected) row.setBackgroundColor(style.soft);
        FrameLayout icon = new FrameLayout(this); icon.setBackground(style.bg(subjectTint(m), 10));
        icon.addView(style.icon(style.iconName(m), subjectInk(m), 21), new FrameLayout.LayoutParams(dp(21), dp(21), Gravity.CENTER));
        row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(38)));
        LinearLayout words = style.column(); words.setPadding(dp(11), 0, dp(7), 0);
        words.addView(style.text(style.shortTitle(m), 13, style.ink, true));
        TextView subtitle = style.text("", 11, style.muted, false); add(words, subtitle, 4);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        if (ready) {
            subtitle.setText(gb(m.bytes) + " · saved and checked");
            row.addView(style.icon("next", style.blue, 19));
            row.setOnClickListener(v -> openModule(m));
            row.setContentDescription(m.title + ", saved and checked, open subject");
        } else if (active) {
            subtitle.setText(gb(m.bytes));
            TextView status = style.text("", 11, style.blue, true); row.addView(status);
            bindings.add(s -> { subtitle.setText(gb(m.bytes) + " · " + moduleStatus(m)); status.setText(s.states.get(m.id) == LibraryEngine.State.CHECKING ? "Checking" : modulePercent(m) + "%"); });
            row.setOnClickListener(v -> show(Screen.DOWNLOADS));
            row.setContentDescription(m.title + ", in downloads, view progress");
        } else {
            subtitle.setText(gb(m.bytes) + (m.optional ? " · optional" : "") + " · " + conciseDescription(m));
            FrameLayout check = new FrameLayout(this);
            check.setBackground(style.bg(selected ? style.blue : style.card, 10, selected ? style.blue : style.line));
            if (selected) check.addView(style.icon("check", style.dark ? style.paper : android.graphics.Color.WHITE, 14), new FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER));
            row.addView(check, new LinearLayout.LayoutParams(dp(20), dp(20)));
            row.setOnClickListener(v -> { if (pending.contains(m.id)) pending.remove(m.id); else pending.add(m.id); saveDraft(); render(true); });
            row.setSelected(selected);
            row.setContentDescription(m.title + ", " + gb(m.bytes) + ", " + (selected ? "selected, tap to deselect" : "tap to select"));
        }
        row.setFocusable(true);
        return row;
    }

    private String conciseDescription(ModuleCatalog.Module m) {
        switch (style.iconName(m)) {
            case "leaf": return "Food and gardening guides";
            case "code": return "Programming and computer manuals";
            case "health": return "Health books and nursing guides";
            case "map": return "Offline streets and place search";
            case "repair": return "Repair manuals and practical skills";
            default: return m.description;
        }
    }

    private void filter(LinearLayout filters, String title, boolean on, boolean saved) {
        TextView control = style.text(title, 12, on ? style.blue : style.muted, on);
        control.setGravity(Gravity.CENTER); control.setPadding(dp(5), dp(10), dp(5), dp(10)); control.setMinHeight(dp(44));
        if (on) control.setBackground(style.bg(style.card, 9));
        control.setOnClickListener(v -> { savedOnly = saved; scrollPositions.put(Screen.SUBJECTS, 0); scroll.scrollTo(0, 0); render(true); });
        control.setFocusable(true); control.setSelected(on);
        filters.addView(control, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void buildDownloads() {
        heading("Downloads.", snapshot.incomplete.isEmpty() ? "Your library, ready when you are." : "A little more knowledge, coming your way.");
        if (!snapshot.incomplete.isEmpty()) {
            LinearLayout card = panel(); card.setPadding(dp(16), dp(18), dp(16), dp(15));
            LinearLayout top = style.row();
            ArkUi.Dial dial = style.progressDial(percent()); top.addView(dial);
            LinearLayout copy = style.column(); copy.setPadding(dp(16), 0, 0, 0);
            copy.addView(style.text(downloadTitle(), 16, style.ink, true));
            TextView amount = style.text("", 13, style.muted, false); add(copy, amount, 7);
            TextView state = style.text("", 11, style.muted, false); add(copy, state, 6);
            top.addView(copy, new LinearLayout.LayoutParams(0, -2, 1)); card.addView(top);
            LinearLayout controls = style.row();
            TextView hint = style.text(snapshot.mobile ? "Mobile data allowed" : "Wi-Fi only", 11, style.muted, false);
            controls.addView(hint, new LinearLayout.LayoutParams(0, -2, 1));
            boolean pausing = snapshot.running && !snapshot.paused;
            TextView action = style.button(pausing ? "Pause" : (snapshot.paused || snapshot.limited || totalDone() > 0 ? "Resume" : "Start download"), false, v -> {
                if (DownloadService.isRunning() && !engine.pausedByUser()) DownloadService.pause(this);
                else resumeDownloads();
                requestSnapshot();
            });
            action.setBackground(style.bg(style.soft, 10)); controls.addView(action);
            add(card, controls, 17); add(body, card, 22);
            bindings.add(s -> { dial.setProgress(s.checking != null ? -1 : percent()); amount.setText(gb(totalDone()) + " of " + gb(totalWanted())); state.setText(downloadStatus()); });
            section("In this download", "Add subjects", this::openSubjects);
            LinearLayout tasks = panel(); int n = 0;
            for (ModuleCatalog.Module m : snapshot.incomplete) {
                if (n++ > 0) tasks.addView(style.divider());
                tasks.addView(downloadRow(m));
            }
            body.addView(tasks);
        } else {
            LinearLayout empty = style.column(); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(20), dp(26), dp(20), dp(26));
            empty.addView(style.icon(snapshot.ready.isEmpty() ? "download" : "check", style.blue, 46));
            TextView message = style.text(snapshot.ready.isEmpty() ? "No downloads yet." : "Everything is ready.", 20, style.ink, true);
            message.setGravity(Gravity.CENTER); add(empty, message, 16);
            add(empty, style.button("Choose subjects", false, v -> openSubjects()), 19); add(body, empty, 20);
        }
        if (!snapshot.ready.isEmpty()) {
            section("Saved and checked", snapshot.ready.size() + " " + plural(snapshot.ready.size(), "subject"), null);
            LinearLayout ready = panel(); int n = 0;
            for (ModuleCatalog.Module m : snapshot.ready) {
                if (n++ > 0) ready.addView(style.divider());
                LinearLayout row = simpleRow(style.iconName(m), style.shortTitle(m), gb(m.bytes) + " · ready offline");
                row.addView(style.icon("check", style.blue, 18));
                row.setOnClickListener(v -> openModule(m)); row.setFocusable(true); ready.addView(row);
            }
            body.addView(ready);
        }
        TextView trust = style.text("Every downloaded file is checked against the library's signed file list.", 12, style.muted, false);
        add(body, trust, 20);
        TextView status = style.text("", 12, style.muted, false); add(body, status, 12);
        bindings.add(s -> { String copy = friendlyMessage(); status.setText(copy); status.setVisibility(copy.isEmpty() ? View.GONE : View.VISIBLE); });
        add(body, style.button("Manage phone storage", false, v -> showStorage()), 17);
    }

    private View downloadRow(ModuleCatalog.Module m) {
        LinearLayout column = style.column(); column.setPadding(dp(13), dp(12), dp(13), dp(12));
        LinearLayout row = simpleRow(style.iconName(m), style.shortTitle(m), gb(m.bytes)); row.setPadding(0, 0, 0, dp(9));
        TextView status = style.text("", 11, style.blue, true); row.addView(status); column.addView(row);
        ProgressBar progress = progressBar(); column.addView(progress);
        bindings.add(s -> { status.setText(moduleStatus(m)); progress.setProgress(modulePercent(m)); });
        return column;
    }

    private LinearLayout simpleRow(String icon, String title, String detail) {
        LinearLayout row = style.row(); row.setPadding(dp(13), dp(13), dp(13), dp(13));
        row.addView(style.icon(icon, style.blue, 22));
        LinearLayout copy = style.column(); copy.setPadding(dp(11), 0, dp(8), 0);
        copy.addView(style.text(title, 13, style.ink, true)); add(copy, style.text(detail, 11, style.muted, false), 5);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1)); return row;
    }

    private void buildNavigation() {
        for (Screen target : Screen.values()) {
            boolean on = target == screen;
            LinearLayout tab = style.column(); tab.setGravity(Gravity.CENTER); tab.setPadding(dp(3), dp(3), dp(3), dp(3)); tab.setMinimumHeight(dp(52));
            FrameLayout pill = new FrameLayout(this); if (on) pill.setBackground(style.bg(style.soft, 16));
            pill.addView(style.icon(target == Screen.LIBRARY ? "book" : target == Screen.SUBJECTS ? "grid" : "download", on ? style.blue : style.muted, 21), new FrameLayout.LayoutParams(dp(21), dp(21), Gravity.CENTER));
            tab.addView(pill, new LinearLayout.LayoutParams(dp(51), dp(29)));
            String label = target == Screen.LIBRARY ? "Library" : target == Screen.SUBJECTS ? "Subjects" : "Downloads";
            TextView text = style.text(label, 11, on ? style.blue : style.muted, on); text.setGravity(Gravity.CENTER); add(tab, text, 3);
            tab.setOnClickListener(v -> show(target)); tab.setFocusable(true); tab.setSelected(on); tab.setContentDescription(label + (on ? ", selected" : ""));
            navigation.addView(tab, new LinearLayout.LayoutParams(0, -2, 1));
        }
    }

    private void section(String title, String action, Runnable click) {
        LinearLayout row = style.row();
        row.addView(style.text(title, 15, style.ink, true), new LinearLayout.LayoutParams(0, -2, 1));
        if (action != null) {
            TextView label = style.text(action, 11, click == null ? style.muted : style.blue, false);
            label.setPadding(dp(8), dp(10), 0, dp(10)); label.setMinHeight(dp(40)); label.setGravity(Gravity.CENTER_VERTICAL);
            if (click != null) { label.setOnClickListener(v -> click.run()); label.setFocusable(true); }
            row.addView(label);
        }
        add(body, row, 18); body.addView(style.space(5));
    }

    private void showSettings() {
        LinearLayout content = style.column(); content.setPadding(dp(21), dp(8), dp(21), dp(18));
        TextView intro = style.text("Downloads and sharing stay under your control.", 13, style.muted, false); add(content, intro, 6);
        Switch wifi = switchRow(content, "Download on Wi-Fi only", "Wait for Wi-Fi to avoid using mobile data.", !engine.mobileDataAllowed());
        wifi.setOnCheckedChangeListener((b, checked) -> { engine.setMobileDataAllowed(!checked); requestSnapshot(); });
        Switch share = switchRow(content, "Share your downloaded library", "Only on Wi-Fi while this phone is charging. Sharing waits while downloads are manually paused.", engine.sharingOn());
        share.setOnCheckedChangeListener((b, checked) -> { engine.setSharing(checked); SeedJob.schedule(this, checked); requestSnapshot(); });
        TextView shared = style.text("Shared so far: " + gb(engine.uploaded()), 12, style.muted, false); add(content, shared, 12);
        add(content, style.button("Manage phone storage", false, v -> showStorage()), 18);
        TextView privacy = style.text("No account, ads or analytics. Your library stays on this phone. BitTorrent peers can see your IP address while downloading or sharing.", 12, style.muted, false); add(content, privacy, 18);
        if (BuildConfig.DEBUG) add(content, style.button("Diagnostics", false, v -> new AlertDialog.Builder(this).setTitle("Latest download message").setMessage(engine.latestMessage()).setPositiveButton("Close", null).show()), 14);
        showSheet("Library settings", content);
    }

    private int subjectTint(ModuleCatalog.Module module) {
        if (style.dark) return style.soft;
        switch (style.iconName(module)) {
            case "health": return 0xfff1e5ec;
            case "repair": return 0xfff4ead7;
            case "leaf": return 0xffe5eee4;
            default: return style.soft;
        }
    }
    private int subjectInk(ModuleCatalog.Module module) {
        if (style.dark) return style.blue;
        switch (style.iconName(module)) {
            case "health": return 0xff925b76;
            case "repair": return 0xff8d7140;
            case "leaf": return 0xff51704d;
            default: return style.blue;
        }
    }

    /** A native, scrollable bottom sheet, with controls reachable at large text sizes. */
    private void showSheet(String title, View content) {
        if (sheetDialog != null) sheetDialog.dismiss();
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet = style.column();
        sheet.setBackground(style.bg(style.card, 24));
        sheet.setPadding(0, dp(9), 0, dp(20));
        View handle = new View(this); handle.setBackground(style.bg(style.line, 3));
        LinearLayout.LayoutParams grip = new LinearLayout.LayoutParams(dp(36), dp(4));
        grip.gravity = Gravity.CENTER_HORIZONTAL; grip.topMargin = dp(2); sheet.addView(handle, grip);
        LinearLayout header = style.row(); header.setPadding(dp(22), dp(7), dp(12), 0);
        header.addView(style.text(title, 24, style.ink, true), new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(style.iconButton("close", "Close " + title.toLowerCase(Locale.ROOT), v -> dialog.dismiss()));
        sheet.addView(header);
        final int maxContent = (int) (getResources().getDisplayMetrics().heightPixels * .74f) - dp(76);
        ScrollView scroller = new ScrollView(this) {
            @Override protected void onMeasure(int w, int h) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxContent, MeasureSpec.AT_MOST));
            }
        };
        scroller.setVerticalScrollBarEnabled(false); scroller.addView(content); sheet.addView(scroller);
        dialog.setContentView(sheet);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes(); params.gravity = Gravity.BOTTOM; params.dimAmount = .35f;
            window.setAttributes(params);
        }
        dialog.show();
        if (window != null) window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sheetDialog = dialog;
    }

    private Switch switchRow(LinearLayout content, String title, String detail, boolean checked) {
        LinearLayout row = style.row(); row.setPadding(0, dp(18), 0, dp(15));
        LinearLayout words = style.column(); words.addView(style.text(title, 14, style.ink, true)); add(words, style.text(detail, 12, style.muted, false), 6);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        Switch toggle = new Switch(this); toggle.setChecked(checked); toggle.setContentDescription(title); toggle.setMinWidth(dp(52)); toggle.setMinHeight(dp(48));
        toggle.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{style.blue, style.muted}));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2); p.leftMargin = dp(15); row.addView(toggle, p);
        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked())); content.addView(row); content.addView(style.divider()); return toggle;
    }

    private void showStorage() {
        LinearLayout content = style.column(); content.setPadding(dp(20), dp(8), dp(20), dp(18));
        content.addView(style.text(gb(snapshot.free) + " available on this phone", 14, style.muted, false));
        boolean any = false;
        for (ModuleCatalog.Module m : engine.catalog.modules) {
            long bytes = snapshot.bytes(m);
            if (bytes == 0 && !snapshot.selected.contains(m.id)) continue;
            any = true;
            LinearLayout row = simpleRow(style.iconName(m), style.shortTitle(m), gb(bytes) + (snapshot.states.get(m.id) == LibraryEngine.State.READY ? " · checked" : " · partial download"));
            row.setPadding(0, dp(14), 0, dp(14));
            TextView remove = style.text("Remove", 12, style.blue, true); remove.setPadding(dp(8), dp(14), 0, dp(14)); remove.setMinHeight(dp(48));
            remove.setEnabled(!snapshot.running && !SeedJob.isActive() && !storageBusy); remove.setAlpha(remove.isEnabled() ? 1f : .4f);
            remove.setContentDescription("Remove " + m.title + " from this phone"); remove.setOnClickListener(v -> confirmRemove(m)); row.addView(remove); content.addView(row); content.addView(style.divider());
        }
        if (!any) add(content, style.text("No downloaded subjects to remove yet.", 14, style.muted, false), 22);
        add(content, style.text("Pause downloads and sharing before removing a subject. Removed subjects can be downloaded again later.", 12, style.muted, false), 18);
        showSheet("Phone storage", content);
    }

    private void confirmRemove(ModuleCatalog.Module m) {
        new AlertDialog.Builder(this).setTitle("Remove " + style.shortTitle(m) + "?")
                .setMessage("This deletes only this subject's downloaded files from this phone. You can download it again later.")
                .setNegativeButton("Keep", null).setPositiveButton("Remove", (dialog, which) -> {
                    if (DownloadService.isRunning() || SeedJob.isActive() || engine.isRunning()) { toast("Pause downloads and sharing first."); return; }
                    storageBusy = true; render(true);
                    background.execute(() -> {
                        try { engine.removeModule(m); ui.post(() -> { pending.remove(m.id); saveDraft(); toast("Subject removed."); }); }
                        catch (Exception e) { ui.post(() -> toast("Could not remove this subject. Pause downloads and sharing, then try again.")); }
                        finally { ui.post(() -> { storageBusy = false; requestSnapshot(); }); }
                    });
                }).show();
    }

    private void addDownloads() {
        if (pending.isEmpty()) return;
        final Set<String> additions = new HashSet<>(pending);
        storageBusy = true; render(true);
        background.execute(() -> {
            long need = engine.bytesStillNeeded(additions), free = LibraryEngine.allocatableBytes(this);
            ui.post(() -> {
                storageBusy = false;
                if (isDestroyed()) return;
                if (need > free) {
                    new AlertDialog.Builder(this).setTitle("Not enough space").setMessage("These subjects need " + gb(need) + " more, but this phone has " + gb(free) + " available. Choose fewer subjects or remove a saved one.").setPositiveButton("OK", null).show(); render(true); return;
                }
                engine.addSelected(additions); pending.removeAll(additions); saveDraft();
                show(Screen.DOWNLOADS); resumeDownloads(); requestSnapshot();
            });
        });
    }

    private void resumeDownloads() {
        if (engine.selected().isEmpty()) { openSubjects(); return; }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
        startForegroundService(new Intent(this, DownloadService.class).setAction(DownloadService.ACTION_RESUME));
    }

    private String downloadTitle() { return snapshot.incomplete.size() == 1 ? style.shortTitle(snapshot.incomplete.get(0)) : snapshot.incomplete.size() + " subjects"; }
    private long totalWanted() { long n = 0; for (ModuleCatalog.Module m : snapshot.incomplete) n += m.bytes; return n; }
    private long totalDone() { long n = 0; for (ModuleCatalog.Module m : snapshot.incomplete) n += snapshot.bytes(m); return n; }
    private int percent() { return (int) Math.max(0, Math.min(100, totalWanted() > 0 ? totalDone() * 100 / totalWanted() : 0)); }
    private int modulePercent(ModuleCatalog.Module m) { return (int) Math.min(100, m.bytes > 0 ? snapshot.bytes(m) * 100 / m.bytes : 0); }
    private String downloadStatus() {
        if (snapshot.paused) return "Paused by you";
        if (snapshot.checking != null) return "Checking downloaded files";
        if (snapshot.running && !snapshot.mobile && !snapshot.wifi) return "Waiting for Wi-Fi";
        if (snapshot.running) return snapshot.wifi ? "Downloading on Wi-Fi" : "Downloading";
        if (snapshot.limited) return "Paused by Android";
        return totalDone() > 0 ? "Ready to resume" : "Ready to download";
    }
    private String moduleStatus(ModuleCatalog.Module m) {
        LibraryEngine.State s = snapshot.states.get(m.id);
        if (s == LibraryEngine.State.CHECKING) return "Checking files";
        if (s == LibraryEngine.State.FAILED) return "Check failed · try again";
        if (snapshot.paused) return "Paused";
        if (!snapshot.running) return snapshot.bytes(m) > 0 ? "Ready to resume" : "Not downloaded yet";
        if (!snapshot.mobile && !snapshot.wifi) return "Waiting for Wi-Fi";
        return gb(snapshot.bytes(m)) + " downloaded";
    }
    private String friendlyMessage() {
        String m = snapshot.message == null ? "" : snapshot.message;
        if (m.contains("Internet Archive copy not available")) return "The backup copy is unavailable. Still looking for other copies of the library.";
        if (m.startsWith("Download stopped:")) return "Download stopped. Tap Resume to try again.";
        if (m.contains("could not be checked")) return "A subject could not be verified. Download it again to try another check.";
        return "";
    }

    private void openSubjects() { savedOnly = false; show(Screen.SUBJECTS); }
    private void openModule(ModuleCatalog.Module m) {
        if (engine.state(m) != LibraryEngine.State.READY) { toast("This subject is still downloading or being checked."); return; }
        startActivity(new Intent(this, ModuleActivity.class).putExtra("module", m.id));
    }
    private void saveDraft() { presentation.edit().putStringSet("subjectDraft", new HashSet<>(pending)).apply(); }
    private int dp(float value) { return style.dp(value); }
    private String plural(int count, String singular) { return count == 1 ? singular : singular + "s"; }
    private LinearLayout panel() { LinearLayout p = style.column(); p.setBackground(style.bg(style.card, 15, style.line)); p.setClipToOutline(true); return p; }
    private ProgressBar progressBar() {
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); bar.setMax(100); bar.setProgressTintList(ColorStateList.valueOf(style.blue)); bar.setProgressBackgroundTintList(ColorStateList.valueOf(style.line));
        bar.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(5))); return bar;
    }
    private void add(LinearLayout parent, View child, float top) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(top); parent.addView(child, p); }
    private ScrollView scrollable(View child) { ScrollView s = new ScrollView(this); s.addView(child); return s; }
    private void toast(String message) { if (!isDestroyed()) android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show(); }

    private final Runnable ticker = new Runnable() { @Override public void run() { requestSnapshot(); if (alive) ui.postDelayed(this, 2000); } };
    @Override protected void onResume() {
        super.onResume(); alive = true; engine.addListener(listener);
        if (engine.pausedByLimit() && !engine.pausedByUser() && !DownloadService.isRunning()) {
            engine.setPausedByLimit(false); getSystemService(android.app.NotificationManager.class).cancel(2); resumeDownloads();
        }
        ui.post(ticker);
    }
    @Override protected void onPause() { alive = false; engine.removeListener(listener); ui.removeCallbacks(ticker); super.onPause(); }
    @Override protected void onDestroy() { if (sheetDialog != null) sheetDialog.dismiss(); ui.removeCallbacksAndMessages(null); background.shutdownNow(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        scrollPositions.put(screen, scroll.getScrollY()); state.putString("screen", screen.name()); state.putBoolean("savedOnly", savedOnly);
        for (Screen s : Screen.values()) state.putInt("scroll:" + s.name(), scrollPositions.getOrDefault(s, 0)); super.onSaveInstanceState(state);
    }
    @Override public void onBackPressed() { if (screen != Screen.LIBRARY) show(Screen.LIBRARY); else super.onBackPressed(); }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); if (BuildConfig.DEBUG) handleTestIntent(i); }
    private void handleTestIntent(Intent i) {
        String selected = i.getStringExtra("select");
        if (selected != null) { Set<String> ids = new HashSet<>(); for (String id : selected.split(",")) if (engine.catalog.find(id) != null) ids.add(id); engine.setSelected(ids); requestSnapshot(); }
        String map = i.getStringExtra("openMap"), pdf = i.getStringExtra("openPdf"), zim = i.getStringExtra("openZim");
        if (map != null) startActivity(new Intent(this, MapActivity.class).putExtra("folder", map));
        if (pdf != null) startActivity(new Intent(this, PdfActivity.class).putExtra("path", pdf));
        if (zim != null) startActivity(new Intent(this, ZimActivity.class).putExtra("path", zim));
        if (i.getBooleanExtra("download", false)) {
            Intent service = new Intent(this, DownloadService.class).setAction(DownloadService.ACTION_RESUME);
            service.putExtra("webSeedOnly", i.getBooleanExtra("webSeedOnly", false)); service.putExtra("directPeer", i.getStringExtra("directPeer")); startForegroundService(service);
        }
    }
}
