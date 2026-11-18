package cc.esdy.ark;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;

/** Shows a PDF with Android's built-in renderer, one page per row, rendered on demand. */
public class PdfActivity extends ArkActivity {
    private PdfRenderer renderer;
    private ParcelFileDescriptor fd;
    private ListView pages;
    private TextView pageCount;
    private View previous, next;
    private ArkUi ui;
    private final LruCache<Integer, Bitmap> cache = new LruCache<Integer, Bitmap>(64 * 1024 * 1024) {
        @Override protected int sizeOf(Integer k, Bitmap v) { return v.getByteCount(); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ui = new ArkUi(this);
        ui.applyWindow(this);
        File f = new File(getIntent().getStringExtra("path"));
        setTitle(f.getName());
        LinearLayout root = ui.column();
        root.setBackgroundColor(ui.paper);
        MainActivity.edgeToEdge(this, root);
        LinearLayout toolbar = ui.row();
        toolbar.setPadding(ui.dp(8), ui.dp(3), ui.dp(22), ui.dp(3));
        toolbar.addView(ui.iconButton("back", "Back to subject", v -> finish()));
        TextView heading = ui.text(ModuleActivity.label(f), 14, ui.ink, true);
        heading.setMaxLines(2);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        heading.setPadding(ui.dp(4), 0, 0, 0);
        toolbar.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(toolbar);
        setContentView(root);
        try {
            fd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(fd);
        } catch (Exception e) {
            TextView error = ui.text("Could not open " + f.getName() + ": " + e.getMessage(), 14, ui.ink, false);
            error.setPadding(ui.dp(22), ui.dp(20), ui.dp(22), ui.dp(20));
            root.addView(error);
            return;
        }
        pages = new ListView(this);
        pages.setDivider(new ColorDrawable(ui.paper));
        pages.setDividerHeight(ui.dp(12));
        pages.setBackgroundColor(ui.paper);
        pages.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
        pages.setClipToPadding(false);
        pages.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return renderer.getPageCount(); }
            @Override public Object getItem(int i) { return i; }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View convert, ViewGroup parent) {
                ImageView iv = convert instanceof ImageView ? (ImageView) convert : new ImageView(PdfActivity.this);
                iv.setAdjustViewBounds(true);
                iv.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                iv.setImageBitmap(page(i, Math.max(parent.getWidth(), 720)));
                iv.setContentDescription("Page " + (i + 1));
                return iv;
            }
        });
        root.addView(pages, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout controls = ui.row();
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(ui.dp(16), ui.dp(6), ui.dp(16), ui.dp(6));
        controls.setBackgroundColor(ui.card);
        previous = ui.iconButton("back", "Previous page", v -> movePage(-1));
        previous.setBackground(ui.ripple(ui.soft, 12));
        controls.addView(previous);
        pageCount = ui.text("", 13, ui.ink, true);
        pageCount.setGravity(Gravity.CENTER);
        controls.addView(pageCount, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        next = ui.iconButton("next", "Next page", v -> movePage(1));
        next.setBackground(ui.ripple(ui.soft, 12));
        controls.addView(next);
        root.addView(controls);
        pages.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int state) { }
            @Override public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                updatePageControls(firstVisibleItem);
            }
        });
        updatePageControls(0);
    }

    private void movePage(int direction) {
        if (renderer == null || renderer.getPageCount() == 0) return;
        int target = Math.max(0, Math.min(renderer.getPageCount() - 1, pages.getFirstVisiblePosition() + direction));
        pages.smoothScrollToPositionFromTop(target, 0);
    }

    private void updatePageControls(int position) {
        if (renderer == null || pageCount == null) return;
        int total = renderer.getPageCount();
        int current = Math.max(0, Math.min(position, Math.max(0, total - 1)));
        pageCount.setText("Page " + (total == 0 ? 0 : current + 1) + " of " + total);
        previous.setEnabled(current > 0);
        previous.setAlpha(current > 0 ? 1f : .4f);
        next.setEnabled(current + 1 < total);
        next.setAlpha(current + 1 < total ? 1f : .4f);
    }

    private synchronized Bitmap page(int i, int width) {
        Bitmap bm = cache.get(i);
        if (bm != null) return bm;
        try (PdfRenderer.Page p = renderer.openPage(i)) {
            int h = (int) ((long) width * p.getHeight() / p.getWidth());
            bm = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888);
            bm.eraseColor(Color.WHITE);
            p.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        }
        cache.put(i, bm);
        return bm;
    }

    @Override protected void onDestroy() {
        try { if (renderer != null) renderer.close(); if (fd != null) fd.close(); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
