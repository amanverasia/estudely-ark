package cc.esdy.ark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The phone's view of the pack: which folders of the torrent a user can pick, with sizes taken from the
 * torrent itself. Desktop-only parts (reader programs, AI runners, Organic Maps files) are left out, and the
 * map module is only the desktop vector map, which the app shows with the pack's own viewer.
 */
public final class ModuleCatalog {
    /** One pickable part of the pack. `folder` is the path inside the pack, e.g. "Core/03-Food-and-Growing". */
    public static final class Module {
        public final String id, folder, title, description;
        public final boolean optional;
        public long bytes;
        public int files;

        Module(String folder, String title, String description, boolean optional) {
            this.id = folder; this.folder = folder; this.title = title; this.description = description; this.optional = optional;
        }

        public boolean contains(String relPath) { return relPath.startsWith(folder + "/"); }
    }

    /** Folders never offered on a phone. */
    static final String[] EXCLUDE = {
        "Shared-Readers/", "AI/", "KEYS/", "Core/05-Maps/Android-Organic-Maps/",
    };
    /** Pack files every module needs: the signed file list. */
    public static final String[] ALWAYS = {"MANIFEST.tsv", "MANIFEST.tsv.sig"};

    private static final Map<String, String[]> KNOWN = new LinkedHashMap<>();
    static {
        // folder -> {title, description}; anything else found in the torrent gets a title from its folder name.
        KNOWN.put("Core/01-General-Knowledge", new String[]{"General knowledge", "Simple English Wikipedia with pictures, CIA World Factbook"});
        KNOWN.put("Core/02-Health-and-Sanitation", new String[]{"Health and sanitation", "All 16 Hesperian health books, Wikipedia Medicine, water and sanitation, nursing textbooks"});
        KNOWN.put("Core/03-Food-and-Growing", new String[]{"Food and growing", "Food preparation and storage, gardening"});
        KNOWN.put("Core/04-Repair-and-Practical-Skills", new String[]{"Repair and practical skills", "Appropedia, repair wiki, knots"});
        KNOWN.put("Core/05-Maps/Desktop-Map", new String[]{"Map of India", "Every street in India, with place search. Works offline"});
        KNOWN.put("Core/06-Education-and-Books", new String[]{"Education", "Science simulations, school textbooks, maths, physics and chemistry"});
        KNOWN.put("Core/07-Computing-and-Documentation", new String[]{"Computing", "Programming manuals, freeCodeCamp, Arch Wiki"});
        KNOWN.put("Extras/iFixit", new String[]{"iFixit repair guides", "Step-by-step repair guides for phones, computers and more"});
        KNOWN.put("Extras/Energypedia", new String[]{"Energypedia", "Solar, cooking stoves and other energy know-how"});
    }

    public final List<Module> modules;

    private ModuleCatalog(List<Module> modules) { this.modules = Collections.unmodifiableList(modules); }

    /** Builds the catalogue from (path relative to the pack folder, size) pairs. */
    public static ModuleCatalog build(List<String> relPaths, List<Long> sizes) {
        Map<String, Module> byFolder = new LinkedHashMap<>();
        for (int i = 0; i < relPaths.size(); i++) {
            String p = relPaths.get(i);
            if (excluded(p)) continue;
            String folder = folderOf(p);
            if (folder == null) continue; // top-level files (README, launchers, manifest)
            Module m = byFolder.get(folder);
            if (m == null) {
                String[] k = KNOWN.get(folder);
                m = new Module(folder, k != null ? k[0] : titleFrom(folder), k != null ? k[1] : "", folder.startsWith("Extras/"));
                byFolder.put(folder, m);
            }
            m.bytes += sizes.get(i);
            m.files++;
        }
        List<Module> out = new ArrayList<>(byFolder.values());
        out.sort((a, b) -> {
            int ka = order(a.folder), kb = order(b.folder);
            return ka != kb ? Integer.compare(ka, kb) : a.folder.compareTo(b.folder);
        });
        return new ModuleCatalog(out);
    }

    public Module find(String id) {
        for (Module m : modules) if (m.id.equals(id)) return m;
        return null;
    }

    static boolean excluded(String rel) {
        for (String e : EXCLUDE) if (rel.startsWith(e)) return true;
        return false;
    }

    /** "Core/05-Maps/Desktop-Map/x" -> "Core/05-Maps/Desktop-Map"; "Core/03-Food/x.zim" -> "Core/03-Food"; top-level -> null. */
    static String folderOf(String rel) {
        String[] parts = rel.split("/");
        if (parts.length < 2) return null;
        if (parts[0].equals("Core") && parts[1].equals("05-Maps")) return parts.length >= 4 ? "Core/05-Maps/" + parts[2] : null;
        if (parts.length < 3) return null;
        return parts[0] + "/" + parts[1];
    }

    private static int order(String folder) {
        int i = 0;
        for (String k : KNOWN.keySet()) { if (k.equals(folder)) return i; i++; }
        return folder.startsWith("Core/") ? 50 : 100;
    }

    static String titleFrom(String folder) {
        String last = folder.substring(folder.lastIndexOf('/') + 1).replaceFirst("^\\d+-", "");
        return last.replace('-', ' ');
    }
}
