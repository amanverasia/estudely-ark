package cc.esdy.ark;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Native presentation primitives for Ark's bookshelf. No networking or library state lives here. */
public final class ArkUi {
    public final Context context;
    public final boolean dark;
    public final int paper, ink, blue, muted, line, card, soft;
    private final float density;

    public ArkUi(Context context) {
        this.context = context;
        density = context.getResources().getDisplayMetrics().density;
        dark = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        paper = dark ? 0xff151a27 : 0xfff7f8fc;
        ink = dark ? 0xffe6ebf7 : 0xff202b43;
        blue = dark ? 0xff9ab1ff : 0xff3855b6;
        muted = dark ? 0xffa0abc1 : 0xff657189;
        line = dark ? 0xff303a4f : 0xffe4e8f1;
        card = dark ? 0xff1e2637 : Color.WHITE;
        soft = dark ? 0xff293756 : 0xffe8edfc;
    }

    public int dp(float value) { return Math.round(value * density); }
    private float sp(float value) { return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, context.getResources().getDisplayMetrics()); }

    public LinearLayout column() {
        LinearLayout result = new LinearLayout(context);
        result.setOrientation(LinearLayout.VERTICAL);
        return result;
    }

    public LinearLayout row() {
        LinearLayout result = new LinearLayout(context);
        result.setOrientation(LinearLayout.HORIZONTAL);
        result.setGravity(Gravity.CENTER_VERTICAL);
        return result;
    }

    public TextView text(String value, float size, int color, boolean bold) {
        TextView result = new TextView(context);
        result.setText(value);
        result.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        result.setTextColor(color);
        result.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        result.setIncludeFontPadding(false);
        result.setLineSpacing(dp(2), 1f);
        return result;
    }

    public GradientDrawable bg(int color, float radiusDp) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(color);
        result.setCornerRadius(dp(radiusDp));
        return result;
    }

    public GradientDrawable bg(int color, float radiusDp, int strokeColor) {
        GradientDrawable result = bg(color, radiusDp);
        result.setStroke(dp(1), strokeColor);
        return result;
    }

    /** A sized icon. Its containing button supplies the spoken label and touch target. */
    public View icon(String name, int color, float sizeDp) {
        Icon result = new Icon(context, name, color);
        result.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        result.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return result;
    }

    public FrameLayout iconButton(String name, String description, View.OnClickListener listener) {
        FrameLayout result = new FrameLayout(context);
        result.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        result.setMinimumHeight(dp(48));
        result.setMinimumWidth(dp(48));
        result.setContentDescription(description);
        result.setFocusable(true);
        result.setBackground(ripple(card, 24));
        View glyph = icon(name, blue, 22);
        result.addView(glyph, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        result.setOnClickListener(listener);
        return result;
    }

    public TextView button(String value, boolean primary, View.OnClickListener listener) {
        TextView result = text(value, 14, primary ? (dark ? 0xff152140 : Color.WHITE) : blue, true);
        result.setGravity(Gravity.CENTER);
        result.setPadding(dp(16), dp(12), dp(16), dp(12));
        result.setMinHeight(dp(48));
        result.setBackground(ripple(primary ? blue : card, 12));
        result.setFocusable(true);
        result.setOnClickListener(listener);
        return result;
    }

    public RippleDrawable ripple(int color, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(dark ? 0x329ab1ff : 0x223855b6), bg(color, radiusDp), bg(Color.WHITE, radiusDp));
    }

    public void clickable(View view, int color, float radiusDp, View.OnClickListener listener) {
        view.setBackground(ripple(color, radiusDp));
        view.setFocusable(true);
        view.setOnClickListener(listener);
    }

    public View divider() {
        View result = new View(context);
        result.setBackgroundColor(line);
        result.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        result.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return result;
    }

    public View space(float sizeDp) {
        View result = new View(context);
        result.setLayoutParams(new LinearLayout.LayoutParams(1, dp(sizeDp)));
        result.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return result;
    }

    /** Use alongside MainActivity.edgeToEdge(root), which owns system-bar insets. */
    @SuppressWarnings("deprecation")
    public void applyWindow(Activity activity) {
        activity.getWindow().setStatusBarColor(paper);
        activity.getWindow().setNavigationBarColor(paper);
        int flags = activity.getWindow().getDecorView().getSystemUiVisibility();
        if (dark) flags &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        else flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        activity.getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    public String iconName(ModuleCatalog.Module module) {
        String id = module.id.toLowerCase(java.util.Locale.ROOT);
        if (id.contains("food")) return "leaf";
        if (id.contains("computing")) return "code";
        if (id.contains("health")) return "health";
        if (id.contains("map")) return "map";
        if (id.contains("repair") || id.contains("ifixit")) return "repair";
        if (id.contains("energy")) return "sun";
        return "book";
    }

    public String shortTitle(ModuleCatalog.Module module) {
        String id = module.id.toLowerCase(java.util.Locale.ROOT);
        if (id.contains("food")) return "Food & growing";
        if (id.contains("computing")) return "Computing";
        if (id.contains("health")) return "Health & sanitation";
        if (id.contains("repair") && !id.contains("ifixit")) return "Repair & practical skills";
        return module.title;
    }

    /** Only the book cover; the caller adds live readiness and size metadata below it. */
    public View cover(ModuleCatalog.Module module, View.OnClickListener listener) {
        BookCover result = new BookCover(module);
        result.setMinimumHeight(dp(205));
        result.setFocusable(true);
        result.setClickable(true);
        result.setContentDescription(shortTitle(module) + ", open subject");
        result.setForeground(new RippleDrawable(ColorStateList.valueOf(0x253855b6), null, bg(Color.WHITE, 12)));
        result.setOnClickListener(listener);
        return result;
    }

    public Dial progressDial(int percent) { return new Dial(context, percent); }

    public final class Dial extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int progress;

        Dial(Context context, int percent) {
            super(context);
            setProgress(percent);
            setLayoutParams(new LinearLayout.LayoutParams(dp(100), dp(100)));
        }

        public void setProgress(int percent) {
            progress = Math.max(-1, Math.min(100, percent));
            setContentDescription(progress < 0 ? "Download progress not yet available" : progress + " percent downloaded");
            invalidate();
        }

        public int getProgress() { return progress; }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth(), height = getHeight();
            float side = Math.min(width, height), inset = side * .07f;
            RectF circle = new RectF((width - side) / 2 + inset, (height - side) / 2 + inset,
                    (width + side) / 2 - inset, (height + side) / 2 - inset);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(side * .05f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(soft);
            canvas.drawOval(circle, paint);
            paint.setColor(blue);
            if (progress >= 0) canvas.drawArc(circle, -90, progress * 3.6f, false, paint);
            else canvas.drawArc(circle, -90, 70, false, paint);
            String label = progress >= 0 ? progress + "%" : "—";
            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(Math.min(sp(24), side * .26f));
            Paint.FontMetrics fm = paint.getFontMetrics();
            canvas.drawText(label, width / 2, height / 2 - (fm.ascent + fm.descent) / 2, paint);
        }
    }

    private final class BookCover extends View {
        private final ModuleCatalog.Module module;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final int background, foreground, spine;
        private final String title;

        BookCover(ModuleCatalog.Module module) {
            super(ArkUi.this.context);
            this.module = module;
            String id = module.id.toLowerCase(java.util.Locale.ROOT);
            if (id.contains("computing")) { background = 0xff344985; foreground = 0xfff9faff; spine = 0xff203465; title = "The\ncomputing\nlibrary"; }
            else if (id.contains("food")) { background = 0xffe8e0f0; foreground = 0xff4a3b60; spine = 0xffc5b7d2; title = "Food &\ngrowing"; }
            else if (id.contains("health")) { background = 0xfff0dfe9; foreground = 0xff6c4058; spine = 0xffc59aad; title = "Health &\nsanitation"; }
            else if (id.contains("repair") || id.contains("ifixit")) { background = 0xfff4e4bc; foreground = 0xff6a522b; spine = 0xffd1b577; title = id.contains("ifixit") ? "iFixit\nrepair guides" : "Repair &\npractical\nskills"; }
            else if (id.contains("map")) { background = 0xffd9e6e8; foreground = 0xff365365; spine = 0xff9fbcc4; title = "Map of\nIndia"; }
            else if (id.contains("education")) { background = 0xffdfead9; foreground = 0xff42633e; spine = 0xffaac39d; title = "Education"; }
            else if (id.contains("energy")) { background = 0xffeddcbd; foreground = 0xff6b5834; spine = 0xffc9ad75; title = "Energy\nknow-how"; }
            else { background = 0xffdfe5f4; foreground = 0xff34466d; spine = 0xffa3b2d5; title = "General\nknowledge"; }
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            float fontScale = context.getResources().getConfiguration().fontScale;
            int desiredHeight = dp(205 * Math.max(1f, Math.min(fontScale, 2f)));
            setMeasuredDimension(resolveSize(dp(145), widthMeasureSpec), resolveSize(desiredHeight, heightMeasureSpec));
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight(), radius = dp(12);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(background);
            canvas.drawRoundRect(0, 0, w, h, radius, radius, paint);
            // A squared binding edge and a fine inner crease make these feel like books, not generic cards.
            canvas.drawRect(0, 0, dp(12), h, paint);
            paint.setColor(spine);
            canvas.drawRoundRect(0, 0, dp(5), h, dp(2), dp(2), paint);
            paint.setColor(0x50ffffff);
            canvas.drawRect(dp(9), 0, dp(9.6f), h, paint);

            canvas.save();
            Path clip = new Path();
            clip.addRoundRect(new RectF(0, 0, w, h), radius, radius, Path.Direction.CW);
            canvas.clipPath(clip);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(.7f));
            paint.setColor(module.id.toLowerCase(java.util.Locale.ROOT).contains("computing") ? 0xff7b91c4 : spine);
            float orbit = dp(68), cx = w + dp(15), cy = h * .52f;
            canvas.drawCircle(cx, cy, orbit, paint);
            canvas.drawCircle(cx, cy, orbit - dp(13), paint);
            canvas.restore();

            titlePaint.setColor(foreground);
            titlePaint.setTextSize(sp(25));
            titlePaint.setTypeface(Typeface.create("serif", Typeface.NORMAL));
            float padding = dp(16);
            int titleWidth = Math.max(1, (int) (w - padding * 2));
            // Keep long subject words intact on a narrow two-column shelf. At larger font scales the
            // caller uses a wider column, so the normal sp-scaled size still has room to grow.
            float longestWord = 0;
            for (String word : title.split("\\s+")) longestWord = Math.max(longestWord, titlePaint.measureText(word));
            if (longestWord > titleWidth) titlePaint.setTextSize(titlePaint.getTextSize() * titleWidth / longestWord);
            StaticLayout layout = StaticLayout.Builder.obtain(title, 0, title.length(), titlePaint, titleWidth)
                    .setIncludePad(false).setLineSpacing(0, 1.04f).setAlignment(Layout.Alignment.ALIGN_NORMAL).build();
            canvas.save();
            canvas.translate(padding, dp(18));
            layout.draw(canvas);
            canvas.restore();

            canvas.save();
            canvas.translate(padding, h - dp(77));
            canvas.scale(dp(38) / 24f, dp(38) / 24f);
            paint.setColor(foreground);
            paint.setStrokeWidth(1.15f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
            drawGlyph(canvas, paint, iconName(module));
            canvas.restore();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(foreground);
            paint.setAlpha(155);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            paint.setTextSize(sp(11));
            float labelWidth = paint.measureText("Estudely Ark");
            if (labelWidth > titleWidth) paint.setTextSize(paint.getTextSize() * titleWidth / labelWidth);
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("Estudely Ark", padding, h - dp(18), paint);
            paint.setAlpha(255);
        }
    }

    private static final class Icon extends View {
        private final String name;
        private final int color;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Icon(Context context, String name, int color) { super(context); this.name = name; this.color = color; }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save();
            float scale = Math.min(getWidth(), getHeight()) / 24f;
            canvas.translate((getWidth() - 24 * scale) / 2, (getHeight() - 24 * scale) / 2);
            canvas.scale(scale, scale);
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.65f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            drawGlyph(canvas, paint, name);
            canvas.restore();
        }
    }

    private static void line(Canvas c, Paint p, float... xy) {
        Path path = new Path();
        path.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) path.lineTo(xy[i], xy[i + 1]);
        c.drawPath(path, p);
    }

    private static void drawGlyph(Canvas c, Paint p, String name) {
        Path path = new Path();
        switch (name) {
            case "grid":
                c.drawRoundRect(3, 3, 10, 10, 1, 1, p); c.drawRoundRect(14, 3, 21, 10, 1, 1, p);
                c.drawRoundRect(3, 14, 10, 21, 1, 1, p); c.drawRoundRect(14, 14, 21, 21, 1, 1, p); return;
            case "download": line(c,p,12,3,12,15); line(c,p,7,10,12,15,17,10); line(c,p,4,17,4,21,20,21,20,17); return;
            case "map": line(c,p,3,5,9,3,15,5,21,3,21,19,15,21,9,19,3,21,3,5); line(c,p,9,3,9,19); line(c,p,15,5,15,21); return;
            case "leaf":
                path.moveTo(20,3); path.cubicTo(8,2,2,7,5,14); path.cubicTo(9,22,21,16,20,3); c.drawPath(path,p);
                line(c,p,3,21,16,8); line(c,p,9,15,9,10); line(c,p,13,11,17,11); return;
            case "code": line(c,p,8,7,3,12,8,17); line(c,p,16,7,21,12,16,17); line(c,p,13,2,11,22); return;
            case "health":
                path.moveTo(12,20); path.cubicTo(6,16,1,11,3,6); path.cubicTo(5,1,10,3,12,6); path.cubicTo(15,1,21,3,21,7); path.cubicTo(22,12,16,17,12,20); c.drawPath(path,p);
                line(c,p,6,10,9,10,11,6,13,14,15,10,18,10); return;
            case "repair":
                path.moveTo(15,3); path.cubicTo(10,2,7,6,9,11); path.lineTo(3,17); path.cubicTo(0,21,5,24,7,21); path.lineTo(13,15); path.cubicTo(18,17,22,13,21,8); path.lineTo(17,11); path.lineTo(13,7); path.close(); c.drawPath(path,p); return;
            case "check": line(c,p,5,12,9,16,19,6); return;
            case "plus": line(c,p,12,5,12,19); line(c,p,5,12,19,12); return;
            case "next": line(c,p,5,12,19,12); line(c,p,14,7,19,12,14,17); return;
            case "back": line(c,p,19,12,5,12); line(c,p,10,7,5,12,10,17); return;
            case "pause": line(c,p,8,5,8,19); line(c,p,16,5,16,19); return;
            case "play": line(c,p,8,5,19,12,8,19,8,5); return;
            case "close": line(c,p,6,6,18,18); line(c,p,6,18,18,6); return;
            case "shield": line(c,p,12,3,20,6,20,12); path.moveTo(20,12); path.cubicTo(20,17,12,21,12,21); path.cubicTo(12,21,4,17,4,12); path.lineTo(4,6); path.lineTo(12,3); c.drawPath(path,p); line(c,p,8,12,11,15,16,9); return;
            case "wifi":
                path.moveTo(2,8); path.quadTo(12,0,22,8); c.drawPath(path,p); path.reset();
                path.moveTo(5,12); path.quadTo(12,6,19,12); c.drawPath(path,p); path.reset();
                path.moveTo(8,16); path.quadTo(12,12,16,16); c.drawPath(path,p); c.drawPoint(12,20,p); return;
            case "settings":
                c.drawCircle(12,12,7,p); c.drawCircle(12,12,2.6f,p);
                for (int i=0;i<8;i++) { double a=i*Math.PI/4; c.drawLine(12+(float)Math.cos(a)*7,12+(float)Math.sin(a)*7,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10,p); } return;
            case "storage":
                c.drawRoundRect(3,4,21,20,3,3,p); line(c,p,3,14,21,14); c.drawPoint(17,17,p); return;
            case "pdf":
                line(c,p,5,2,14,2,20,8,20,22,5,22,5,2); line(c,p,14,2,14,8,20,8); line(c,p,8,13,16,13); line(c,p,8,17,14,17); return;
            case "search": c.drawCircle(10.5f,10.5f,6.5f,p); line(c,p,16,16,21,21); return;
            case "sun":
                c.drawCircle(12,12,4,p); for (int i=0;i<8;i++) { double a=i*Math.PI/4; c.drawLine(12+(float)Math.cos(a)*7,12+(float)Math.sin(a)*7,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10,p); } return;
            default:
                path.moveTo(12,5); path.cubicTo(9,3,6,3,3,4); path.lineTo(3,19); path.cubicTo(6,18,9,18,12,20); path.cubicTo(15,18,18,18,21,19); path.lineTo(21,4); path.cubicTo(18,3,15,3,12,5); c.drawPath(path,p); line(c,p,12,5,12,20);
        }
    }
}
