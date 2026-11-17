package cc.esdy.ark;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;

/** Shows a PDF with Android's built-in renderer, one page per row, rendered on demand. */
public class PdfActivity extends Activity {
    private PdfRenderer renderer;
    private ParcelFileDescriptor fd;
    private final LruCache<Integer, Bitmap> cache = new LruCache<Integer, Bitmap>(64 * 1024 * 1024) {
        @Override protected int sizeOf(Integer k, Bitmap v) { return v.getByteCount(); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        File f = new File(getIntent().getStringExtra("path"));
        setTitle(f.getName());
        try {
            fd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(fd);
        } catch (Exception e) {
            TextView t = new TextView(this);
            t.setText("Could not open " + f.getName() + ": " + e.getMessage());
            setContentView(t);
            return;
        }
        ListView lv = new ListView(this);
        lv.setDividerHeight(12);
        lv.setBackgroundColor(Color.LTGRAY);
        MainActivity.edgeToEdge(this, lv);
        lv.setAdapter(new BaseAdapter() {
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
        setContentView(lv);
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
