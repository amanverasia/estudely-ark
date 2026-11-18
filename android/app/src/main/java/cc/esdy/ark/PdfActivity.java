package cc.esdy.ark;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shows a PDF with Android's built-in renderer, one page per row. Pages render on a background thread.
 * Zoom: pinch, double-tap (1x / 2x) or the - / + buttons. Zooming widens the page list inside a horizontal
 * scroller, so a zoomed page pans sideways while the list still scrolls down normally.
 * The last page read is remembered per file.
 */
public class PdfActivity extends ArkActivity {
    static final float MIN_ZOOM = 1f, MAX_ZOOM = 3f;
    /** Widest bitmap rendered (px). Beyond this, a zoomed page is scaled up from this size to limit memory. */
    static final int MAX_RENDER_WIDTH = 2048;

    private PdfRenderer renderer;
    private ParcelFileDescriptor fd;
    private ListView pages;
    private ZoomScroller scroller;
    private TextView pageCount;
    private View previous, next, zoomOut, zoomIn;
    private ArkUi ui;
    private String path;
    private float zoom = 1f;
    private int viewportWidth;
    /** Page to jump to once the list has its real width; -1 when there is none to restore. */
    private int restorePage = -1;
    private float[] aspect; // page height / width, filled lazily
    private final ExecutorService renderThread = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(cacheBytes()) {
        @Override protected int sizeOf(String k, Bitmap v) { return v.getByteCount(); }
    };

    private static int cacheBytes() {
        return (int) Math.min(Runtime.getRuntime().maxMemory() / 4, 96L * 1024 * 1024);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ui = new ArkUi(this);
        ui.applyWindow(this);
        path = getIntent().getStringExtra("path");
        File f = new File(path);
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
        aspect = new float[renderer.getPageCount()];

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
                int width = pageWidth();
                iv.setScaleType(ImageView.ScaleType.FIT_XY);
                iv.setBackgroundColor(Color.WHITE);
                iv.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(width * aspect(i)))));
                iv.setContentDescription("Page " + (i + 1));
                iv.setTag(i);
                int renderWidth = Math.min(width, MAX_RENDER_WIDTH);
                Bitmap cached = cache.get(key(i, renderWidth));
                iv.setImageBitmap(cached);
                if (cached == null) render(iv, i, renderWidth);
                return iv;
            }
        });

        scroller = new ZoomScroller();
        scroller.setFillViewport(true);
        scroller.setHorizontalScrollBarEnabled(true);
        scroller.addView(pages, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scroller.addOnLayoutChangeListener((v, l, t, r, bottom, ol, ot, or, ob) -> {
            if (r - l > 0 && r - l != viewportWidth) { viewportWidth = r - l; applyWidth(); restorePage(); }
        });
        root.addView(scroller, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout controls = ui.row();
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(ui.dp(12), ui.dp(6), ui.dp(12), ui.dp(6));
        controls.setBackgroundColor(ui.card);
        previous = ui.iconButton("back", "Previous page", v -> movePage(-1));
        previous.setBackground(ui.ripple(ui.soft, 12));
        controls.addView(previous);
        pageCount = ui.text("", 13, ui.ink, true);
        pageCount.setGravity(Gravity.CENTER);
        controls.addView(pageCount, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        zoomOut = zoomButton("−", "Zoom out", -1);
        controls.addView(zoomOut);
        zoomIn = zoomButton("+", "Zoom in", 1);
        controls.addView(zoomIn);
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
        int saved = prefs().getInt(path, 0);
        if (saved > 0 && saved < renderer.getPageCount()) restorePage = saved;
        // If the width is already known, jump now; otherwise the layout listener does it after applyWidth().
        int startPage = restorePage >= 0 ? restorePage : 0;
        if (restorePage >= 0 && viewportWidth > 0) restorePage();
        updatePageControls(startPage);
        updateZoomControls();
    }

    private SharedPreferences prefs() { return getSharedPreferences("pdf-positions", MODE_PRIVATE); }

    @Override protected void onPause() {
        super.onPause();
        if (pages != null) prefs().edit().putInt(path, pages.getFirstVisiblePosition()).apply();
    }

    private TextView zoomButton(String label, String description, int direction) {
        TextView t = ui.text(label, 22, ui.ink, true);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(ui.dp(48));
        t.setMinHeight(ui.dp(48));
        t.setContentDescription(description);
        ui.clickable(t, ui.card, 12, v -> setZoom(direction > 0 ? nextStep(zoom) : previousStep(zoom), viewportWidth / 2f, scroller.getHeight() / 2f));
        return t;
    }

    private static float nextStep(float z) { return z < 1.5f ? 1.5f : z < 2f ? 2f : MAX_ZOOM; }
    private static float previousStep(float z) { return z > 2f ? 2f : z > 1.5f ? 1.5f : MIN_ZOOM; }

    /** Width of a page image at the current zoom (list width minus its side padding). */
    private int pageWidth() {
        int list = Math.max(1, Math.round(viewportWidth * zoom));
        return Math.max(1, list - pages.getPaddingLeft() - pages.getPaddingRight());
    }

    private static String key(int page, int width) { return page + "@" + width; }

    private float aspect(int i) {
        if (aspect[i] == 0f) {
            synchronized (this) {
                try (PdfRenderer.Page p = renderer.openPage(i)) { aspect[i] = (float) p.getHeight() / p.getWidth(); }
            }
        }
        return aspect[i];
    }

    /** Renders page i in the background and puts it in iv if iv still shows that page. */
    private void render(ImageView iv, int i, int width) {
        renderThread.execute(() -> {
            if (!Integer.valueOf(i).equals(iv.getTag())) return; // row reused for another page before its turn
            String k = key(i, width);
            Bitmap bm = cache.get(k);
            if (bm == null) {
                bm = renderPage(i, width);
                if (bm == null) return;
                cache.put(k, bm);
            }
            Bitmap done = bm;
            runOnUiThread(() -> { if (Integer.valueOf(i).equals(iv.getTag())) iv.setImageBitmap(done); });
        });
    }

    private synchronized Bitmap renderPage(int i, int width) {
        if (renderer == null) return null;
        try (PdfRenderer.Page p = renderer.openPage(i)) {
            int h = Math.max(1, (int) ((long) width * p.getHeight() / p.getWidth()));
            Bitmap bm = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888);
            bm.eraseColor(Color.WHITE);
            p.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bm;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Jumps to the remembered page after the list has its final width. applyWidth() calls
     * notifyDataSetChanged(), which resets a selection made earlier in onCreate, so this has to run after it.
     */
    private void restorePage() {
        if (restorePage < 0) return;
        final int target = restorePage;
        restorePage = -1;
        pages.post(() -> {
            if (renderer == null || target >= renderer.getPageCount()) return;
            pages.setSelectionFromTop(target, 0);
            updatePageControls(target);
        });
    }

    /** Sets the list's width for the current zoom. */
    private void applyWidth() {
        ViewGroup.LayoutParams lp = pages.getLayoutParams();
        lp.width = Math.round(viewportWidth * zoom);
        pages.setLayoutParams(lp);
        ((BaseAdapter) pages.getAdapter()).notifyDataSetChanged();
    }

    /** Changes zoom, keeping the point under (focusX, focusY) of the visible area roughly in place. */
    private void setZoom(float z, float focusX, float focusY) {
        z = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
        if (Math.abs(z - zoom) < 0.01f || viewportWidth == 0) return;
        float ratio = z / zoom;
        int first = pages.getFirstVisiblePosition();
        View top = pages.getChildAt(0);
        int topOffset = top == null ? 0 : top.getTop();
        int newScrollX = Math.max(0, Math.round((scroller.getScrollX() + focusX) * ratio - focusX));
        // Keep the same spot of the first visible page under the focus point vertically.
        int newTop = Math.round(focusY - (focusY - topOffset) * ratio);
        zoom = z;
        applyWidth();
        pages.setSelectionFromTop(first, newTop);
        scroller.post(() -> scroller.scrollTo(newScrollX, 0));
        updateZoomControls();
    }

    private void updateZoomControls() {
        if (zoomOut == null) return;
        zoomOut.setEnabled(zoom > MIN_ZOOM);
        zoomOut.setAlpha(zoom > MIN_ZOOM ? 1f : .4f);
        zoomIn.setEnabled(zoom < MAX_ZOOM);
        zoomIn.setAlpha(zoom < MAX_ZOOM ? 1f : .4f);
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

    /**
     * Horizontal scroller around the page list that also handles pinch and double-tap zoom. While two fingers
     * are down, the list is scaled as a live preview; the new zoom is applied when the pinch ends.
     */
    private final class ZoomScroller extends HorizontalScrollView {
        private boolean scaling;
        private float pinch = 1f, focusX, focusY;
        private final ScaleGestureDetector scale = new ScaleGestureDetector(PdfActivity.this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector d) {
                scaling = true;
                pinch = 1f;
                focusX = d.getFocusX();
                focusY = d.getFocusY();
                pages.setPivotX(getScrollX() + focusX);
                pages.setPivotY(focusY);
                return true;
            }
            @Override public boolean onScale(ScaleGestureDetector d) {
                pinch = Math.max(MIN_ZOOM / zoom, Math.min(MAX_ZOOM / zoom, pinch * d.getScaleFactor()));
                pages.setScaleX(pinch);
                pages.setScaleY(pinch);
                return true;
            }
            @Override public void onScaleEnd(ScaleGestureDetector d) {
                pages.setScaleX(1f);
                pages.setScaleY(1f);
                setZoom(zoom * pinch, focusX, focusY);
            }
        });
        private final GestureDetector taps = new GestureDetector(PdfActivity.this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDoubleTap(MotionEvent e) {
                setZoom(zoom > 1.01f ? 1f : 2f, e.getX(), e.getY());
                return true;
            }
        });

        ZoomScroller() { super(PdfActivity.this); }

        // HorizontalScrollView measures its child with an unspecified width; the page list needs exactly
        // (viewport width x zoom), and the full height.
        @Override protected void measureChild(View child, int widthSpec, int heightSpec) { measureExactly(child, widthSpec, heightSpec); }
        @Override protected void measureChildWithMargins(View child, int widthSpec, int widthUsed, int heightSpec, int heightUsed) {
            measureExactly(child, widthSpec, heightSpec);
        }
        private void measureExactly(View child, int widthSpec, int heightSpec) {
            int w = child.getLayoutParams().width > 0 ? child.getLayoutParams().width : MeasureSpec.getSize(widthSpec);
            child.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightSpec), MeasureSpec.EXACTLY));
        }

        @Override public boolean dispatchTouchEvent(MotionEvent ev) {
            scale.onTouchEvent(ev);
            if (!scaling) taps.onTouchEvent(ev);
            if (ev.getPointerCount() > 1 || scaling) {
                if (ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                    // Stop the list's own scroll or fling when the second finger lands.
                    MotionEvent cancel = MotionEvent.obtain(ev);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel);
                    cancel.recycle();
                }
                int action = ev.getActionMasked();
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) scaling = false;
                return true;
            }
            return super.dispatchTouchEvent(ev);
        }
    }

    @Override protected void onDestroy() {
        renderThread.shutdownNow();
        synchronized (this) {
            try { if (renderer != null) renderer.close(); if (fd != null) fd.close(); } catch (Exception ignored) {}
            renderer = null;
        }
        super.onDestroy();
    }
}
