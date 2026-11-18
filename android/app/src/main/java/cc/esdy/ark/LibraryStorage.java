package cc.esdy.ark;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Phone and removable-storage roots that Android lets this app use directly, without a storage permission. */
final class LibraryStorage {
    static final class Location {
        final File root;
        final String name;
        final boolean removable, available;

        Location(File root, String name, boolean removable, boolean available) {
            this.root = root;
            this.name = name;
            this.removable = removable;
            this.available = available;
        }
    }

    private LibraryStorage() { }

    /** Internal storage plus every mounted app-specific SD-card folder. Emulated external storage is omitted. */
    static List<Location> locations(Context c) {
        List<Location> out = new ArrayList<>();
        File internal = c.getFilesDir();
        out.add(new Location(internal, "Phone storage", false, available(internal, false)));
        File[] external = c.getExternalFilesDirs(null);
        if (external != null) for (File dir : external) {
            try {
                if (dir == null || !Environment.isExternalStorageRemovable(dir)) continue;
                out.add(new Location(dir, "SD card", true, available(dir, true)));
            } catch (RuntimeException ignored) { }
        }
        return out;
    }

    static Location current(Context c, File root) {
        boolean phone = root.getAbsolutePath().equals(c.getFilesDir().getAbsolutePath());
        return new Location(root, phone ? "Phone storage" : "SD card", !phone, available(root, !phone));
    }

    static boolean available(Context c, File root) {
        return current(c, root).available;
    }

    private static boolean available(File root, boolean removable) {
        try {
            if (removable && !Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(root))) return false;
            return root.isDirectory() && root.canWrite();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
