package cc.esdy.ark;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Moves the library folder between storage locations (phone storage, SD card) without risking the only copy.
 * Plain Java (no Android), so the crash-recovery rules are unit-tested.
 *
 * Order: journal COPY -> copy into "<name>.moving" next to the target, hashing every file -> read every copied
 * file back and compare hashes (SD cards can fail silently) -> persist those hashes in the journal's storage ->
 * fsync and rename to the target -> durably switch the library -> journal DELETE -> delete the old copy.
 * Recovery verifies the active copy against those hashes before deleting anything. If its storage is unavailable
 * or its contents do not check out, both copies are left alone.
 */
final class LibraryMover {
    /** Space kept free on the target beyond the library itself. */
    static final long MARGIN_BYTES = 200_000_000L;
    /** FAT32 (common on older or small SD cards) can't hold files of 4 GiB or more. */
    static final long FAT32_MAX_FILE = 4L * 1024 * 1024 * 1024 - 1;

    interface Progress {
        void update(long doneBytes, long totalBytes);
        boolean cancelled();
        default void phase(String phase) { }
    }

    interface Commit {
        void run() throws IOException;
    }

    enum Phase { NONE, COPY, DELETE }

    /** Persistent move record plus the hashes needed to validate a copy after a crash or power loss. */
    static final class Journal {
        final File file;
        private final File hashes;
        Journal(File file) { this.file = file; hashes = new File(file.getPath() + ".hashes"); }

        void write(Phase phase, File from, File to) throws IOException {
            File tmp = new File(file.getPath() + ".tmp");
            writeSynced(tmp, (phase + "\n" + from.getAbsolutePath() + "\n" + to.getAbsolutePath() + "\n").getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            syncDirectory(file.getParentFile());
        }

        /** {phase, from, to} or null when no valid move is recorded. */
        String[] read() {
            try {
                if (!file.isFile()) return null;
                String[] l = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).split("\n");
                if (l.length < 3) return null;
                Phase phase = Phase.valueOf(l[0]);
                if (phase == Phase.NONE || l[1].isEmpty() || l[2].isEmpty()
                        || !new File(l[1]).isAbsolute() || !new File(l[2]).isAbsolute()) return null;
                return new String[]{l[0], l[1], l[2]};
            } catch (IOException | IllegalArgumentException e) { return null; }
        }

        void writeHashes(Map<String, String> values) throws IOException {
            StringBuilder text = new StringBuilder();
            for (Map.Entry<String, String> e : new TreeMap<>(values).entrySet()) {
                String path = Base64.getUrlEncoder().withoutPadding().encodeToString(e.getKey().getBytes(StandardCharsets.UTF_8));
                text.append(path).append('\t').append(e.getValue()).append('\n');
            }
            File tmp = new File(hashes.getPath() + ".tmp");
            writeSynced(tmp, text.toString().getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(), hashes.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            syncDirectory(hashes.getParentFile());
        }

        Map<String, String> readHashes() {
            try {
                if (!hashes.isFile()) return null;
                Map<String, String> out = new HashMap<>();
                for (String line : new String(Files.readAllBytes(hashes.toPath()), StandardCharsets.UTF_8).split("\n")) {
                    if (line.isEmpty()) continue;
                    String[] parts = line.split("\t");
                    if (parts.length != 2 || !parts[1].matches("[0-9a-f]{64}")) return null;
                    String path = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
                    if (!validRelativePath(path) || out.put(path, parts[1]) != null) return null;
                }
                return out;
            } catch (IOException | IllegalArgumentException e) { return null; }
        }

        boolean isClear() {
            return !Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)
                    && !Files.exists(hashes.toPath(), LinkOption.NOFOLLOW_LINKS);
        }

        void clear() throws IOException {
            IOException failure = null;
            try { Files.deleteIfExists(file.toPath()); } catch (IOException e) { failure = e; }
            try { Files.deleteIfExists(hashes.toPath()); } catch (IOException e) {
                if (failure == null) failure = e; else failure.addSuppressed(e);
            }
            syncDirectory(file.getParentFile());
            if (failure != null) throw failure;
        }
    }

    private static boolean validRelativePath(String path) {
        if (path.isEmpty() || path.startsWith("/") || path.contains("\\")) return false;
        for (String part : path.split("/")) if (part.isEmpty() || part.equals(".") || part.equals("..")) return false;
        return true;
    }

    static long treeSize(File file) throws IOException {
        rejectSymlink(file);
        if (file.isFile()) return file.length();
        File[] list = file.listFiles();
        if (list == null) throw new IOException("cannot read " + file);
        long n = 0;
        for (File f : list) n += treeSize(f);
        return n;
    }

    static long largestFile(File file) throws IOException {
        rejectSymlink(file);
        if (file.isFile()) return file.length();
        File[] list = file.listFiles();
        if (list == null) throw new IOException("cannot read " + file);
        long m = 0;
        for (File f : list) m = Math.max(m, largestFile(f));
        return m;
    }

    /** Deletes links themselves, never their targets. */
    static void deleteTree(File f) throws IOException {
        if (!Files.exists(f.toPath(), LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(f.toPath())) { Files.delete(f.toPath()); return; }
        File[] list = f.listFiles();
        if (f.isDirectory() && list == null) throw new IOException("cannot read " + f);
        if (list != null) for (File c : list) deleteTree(c);
        Files.deleteIfExists(f.toPath());
    }

    /**
     * Moves the folder {@code from} to {@code to} (which must not exist yet).
     * @param freeAtTarget usable bytes on the target volume
     * @param fat32 the target may be FAT32 (refuse files of 4 GiB or more)
     * @param commit switches the library to {@code to}; must be durable when it returns
     */
    static void move(File from, File to, Journal journal, long freeAtTarget, boolean fat32,
                     Progress progress, Commit commit) throws IOException, InterruptedException {
        if (!from.isDirectory()) throw new IOException("nothing to move");
        if (to.exists()) throw new IOException("the target already has a library folder");
        if (to.getParentFile() == null || from.getAbsolutePath().equals(to.getAbsolutePath()))
            throw new IOException("choose a different storage location");
        long total = treeSize(from);
        if (total + MARGIN_BYTES > freeAtTarget)
            throw new IOException("not enough space: the library needs " + total + " bytes plus room to spare");
        if (fat32 && largestFile(from) > FAT32_MAX_FILE)
            throw new IOException("a file is larger than this card's file system allows (4 GB)");
        File staging = new File(to.getParentFile(), to.getName() + ".moving");
        if (staging.exists()) deleteTree(staging);
        journal.write(Phase.COPY, from, to);
        Map<String, String> hashes = new HashMap<>();
        long[] done = {0};
        try {
            progress.phase("Copying");
            copyTree(from, staging, "", hashes, done, total, progress);
            progress.phase("Checking the copy");
            verifyTree(staging, hashes, progress);
            syncDirectories(staging);
            journal.writeHashes(hashes);
            if (!staging.renameTo(to)) throw new IOException("cannot finish the copy");
            syncDirectory(to.getParentFile());
        } catch (IOException | InterruptedException | RuntimeException e) {
            try {
                deleteTree(staging);
                deleteTree(to); // rename may have succeeded before its parent-directory fsync failed
                journal.clear();
            } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
        progress.phase("Finishing");
        commit.run();
        journal.write(Phase.DELETE, from, to);
        deleteTree(from);
        syncDirectory(from.getParentFile());
        journal.clear();
    }

    /**
     * Finishes or undoes an interrupted move. {@code current} is the folder the library points at now.
     * The Android caller also checks that current's storage volume is mounted before calling this.
     * Returns a short description of what was done, or null if there was nothing to do.
     */
    static String recover(Journal journal, File current) throws IOException {
        String[] j = journal.read();
        if (j == null) return null;
        Phase phase = Phase.valueOf(j[0]);
        File from = new File(j[1]).getAbsoluteFile(), to = new File(j[2]).getAbsoluteFile();
        File active = current == null ? null : current.getAbsoluteFile();
        if (active == null || !active.isDirectory())
            return "the library move is paused until its current storage is available again";
        if (from.equals(to) || (!active.equals(from) && !active.equals(to)))
            throw new IOException("the move journal doesn't match the current library; no files were deleted");
        if (active.equals(from) && phase != Phase.COPY)
            throw new IOException("the move journal is inconsistent; no files were deleted");
        File staging = new File(to.getParentFile(), to.getName() + ".moving");
        String result;
        if (active.equals(to)) {
            // COPY here means the durable switch happened just before the process died; DELETE is the usual case.
            verifyActive(journal, to);
            deleteTree(from);
            deleteTree(staging);
            result = "finished moving the library";
        } else {
            // Before the switch only staging can be incomplete. A renamed target must have hashes; use them to
            // prove the active source is intact before deleting that alternate complete copy.
            if (to.exists()) verifyActive(journal, from);
            deleteTree(staging);
            deleteTree(to);
            result = "an interrupted move was undone; the library stayed where it was";
        }
        journal.clear();
        return result;
    }

    private static void verifyActive(Journal journal, File active) throws IOException {
        Map<String, String> hashes = journal.readHashes();
        if (hashes == null) throw new IOException("the copied-file hashes are unavailable; no files were deleted");
        try {
            verifyTree(active, hashes, null);
        } catch (InterruptedException impossible) { throw new IOException(impossible); }
    }

    private static void copyTree(File src, File dst, String rel, Map<String, String> hashes, long[] done, long total,
                                 Progress progress) throws IOException, InterruptedException {
        rejectSymlink(src);
        if (!dst.isDirectory() && !dst.mkdirs()) throw new IOException("cannot create " + dst);
        File[] list = src.listFiles();
        if (list == null) throw new IOException("cannot read " + src);
        List<File> files = new ArrayList<>(java.util.Arrays.asList(list));
        files.sort((a, b) -> a.getName().compareTo(b.getName()));
        byte[] buf = new byte[1 << 20];
        for (File f : files) {
            rejectSymlink(f);
            String r = rel.isEmpty() ? f.getName() : rel + "/" + f.getName();
            File out = new File(dst, f.getName());
            if (f.isDirectory()) { copyTree(f, out, r, hashes, done, total, progress); continue; }
            MessageDigest md = sha256();
            try (InputStream in = new FileInputStream(f); OutputStream o = new FileOutputStream(out)) {
                for (int n; (n = in.read(buf)) > 0; ) {
                    if (progress.cancelled()) throw new InterruptedException("cancelled");
                    o.write(buf, 0, n);
                    md.update(buf, 0, n);
                    done[0] += n;
                    progress.update(done[0], total);
                }
                ((FileOutputStream) o).getFD().sync();
            }
            hashes.put(r, hex(md.digest()));
        }
    }

    private static void verifyTree(File dir, Map<String, String> hashes, Progress progress)
            throws IOException, InterruptedException {
        for (Map.Entry<String, String> e : hashes.entrySet()) {
            if (progress != null && progress.cancelled()) throw new InterruptedException("cancelled");
            File f = new File(dir, e.getKey());
            if (Files.isSymbolicLink(f.toPath()) || !f.isFile() || !hashFile(f).equals(e.getValue()))
                throw new IOException("the copy of " + e.getKey() + " doesn't match the original");
        }
    }

    private static void rejectSymlink(File f) throws IOException {
        if (Files.isSymbolicLink(f.toPath())) throw new IOException("symbolic links are not supported: " + f);
    }

    private static void writeSynced(File f, byte[] data) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(data);
            out.getFD().sync();
        }
    }

    /** Linux/Android directory fsync: makes file creation, renames and journal updates durable across power loss. */
    private static void syncDirectory(File dir) throws IOException {
        if (dir == null) throw new IOException("missing parent folder");
        try (FileChannel channel = FileChannel.open(dir.toPath(), StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    private static void syncDirectories(File dir) throws IOException {
        File[] list = dir.listFiles();
        if (list == null) throw new IOException("cannot read " + dir);
        for (File f : list) if (f.isDirectory()) syncDirectories(f);
        syncDirectory(dir);
    }

    static String hashFile(File f) throws IOException {
        MessageDigest md = sha256();
        byte[] buf = new byte[1 << 20];
        try (InputStream in = new FileInputStream(f)) { for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n); }
        return hex(md.digest());
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String hex(byte[] d) {
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
