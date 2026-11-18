package cc.esdy.ark;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Version, signing certificate, links, and the licences and credits for the app and the library's content. */
public class AboutActivity extends ArkActivity {
    static final String SITE = "https://ark.esdy.cc", SOURCE = "https://github.com/amanverasia/estudely-ark";
    private ArkUi ui;
    private LinearLayout body;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ui = new ArkUi(this);
        ui.applyWindow(this);
        boolean licences = getIntent().getBooleanExtra("licences", false);
        LinearLayout root = ui.column();
        root.setBackgroundColor(ui.paper);
        MainActivity.edgeToEdge(this, root);
        LinearLayout toolbar = ui.row();
        toolbar.setPadding(ui.dp(8), ui.dp(3), ui.dp(22), ui.dp(3));
        toolbar.addView(ui.iconButton("back", "Back", v -> finish()));
        String title = licences ? "Licences and credits" : "About Estudely Ark";
        setTitle(title);
        TextView heading = ui.text(title, 16, ui.ink, true);
        heading.setPadding(ui.dp(4), 0, 0, 0);
        toolbar.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(toolbar);
        root.addView(ui.divider());
        ScrollView scroll = new ScrollView(this);
        body = ui.column();
        body.setPadding(ui.dp(22), ui.dp(16), ui.dp(22), ui.dp(28));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        if (licences) buildLicences(); else buildAbout();
    }

    private void buildAbout() {
        add(ui.text("A free, offline library for India. Download the subjects you want once, then read them without internet.", 14, ui.ink, false), 0);
        add(ui.text("Version " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")", 13, ui.muted, false), 14);

        add(ui.text("Signed by", 14, ui.ink, true), 24);
        TextView cert = ui.text(certificateSha256(), 12, ui.muted, false);
        cert.setTypeface(Typeface.MONOSPACE);
        cert.setTextIsSelectable(true);
        add(cert, 6);
        add(ui.text("Official copies show this SHA-256 certificate fingerprint, the same one published on " + SITE.replace("https://", "") + ".", 12, ui.muted, false), 6);

        add(ui.button("Licences and credits", false, v -> startActivity(new Intent(this, AboutActivity.class).putExtra("licences", true))), 24);
        add(ui.button("Privacy policy", false, v -> open(SITE + "/privacy")), 10);
        add(ui.button("Source code", false, v -> open(SOURCE)), 10);
        add(ui.button("Report a problem", false, v -> open(SOURCE + "/issues")), 10);
        add(ui.text("The app is free software under the GNU GPL, version 3 or later, and comes with no warranty. "
                + "The library's content is made by others and keeps its own licences; some of it may only be shared for free.", 12, ui.muted, false), 22);
    }

    private void buildLicences() {
        add(ui.text("Software", 15, ui.ink, true), 0);
        add(monospace(readAsset("notices.txt")), 8);
        add(ui.text("Library content", 15, ui.ink, true), 24);
        add(monospace(readAsset("credits.txt")), 8);
    }

    private TextView monospace(String text) {
        TextView t = ui.text(text, 11, ui.ink, false);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        return t;
    }

    private void add(android.view.View v, float topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = ui.dp(topDp);
        body.addView(v, p);
    }

    private void open(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { Toast.makeText(this, url, Toast.LENGTH_LONG).show(); } // no browser: at least show the address
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name)) { return new String(LibraryEngine.readAll(in), StandardCharsets.UTF_8); }
        catch (Exception e) { return "(" + name + " could not be read)"; }
    }

    /** SHA-256 of this installed app's signing certificate, read from Android (not hard-coded). */
    @SuppressWarnings("deprecation")
    private String certificateSha256() {
        try {
            Signature[] sigs;
            if (Build.VERSION.SDK_INT >= 28) {
                PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                sigs = info.signingInfo.getApkContentsSigners();
            } else {
                sigs = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES).signatures;
            }
            byte[] d = MessageDigest.getInstance("SHA-256").digest(sigs[0].toByteArray());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < d.length; i++) { if (i > 0) sb.append(i % 8 == 0 ? "\n" : ":"); sb.append(String.format("%02x", d[i])); }
            return sb.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
