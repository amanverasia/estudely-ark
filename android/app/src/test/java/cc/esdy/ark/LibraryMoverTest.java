package cc.esdy.ark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LibraryMoverTest {
    @Rule public final TemporaryFolder tmp = new TemporaryFolder();

    private LibraryMover.Journal journal;

    @Before public void setUp() throws Exception {
        journal = new LibraryMover.Journal(new File(tmp.newFolder("state"), "move.journal"));
    }

    @Test public void successfulNestedMove() throws Exception {
        File from = tmp.newFolder("phone", "pack");
        File to = target("card");
        write(new File(from, "MANIFEST.tsv"), "manifest\n");
        write(new File(from, "nested/deep/book.zim"), "book contents");
        assertTrue(new File(from, "empty/child").mkdirs());
        AtomicBoolean committed = new AtomicBoolean();
        LibraryMover.Commit commit = () -> committed.set(true);

        LibraryMover.move(from, to, journal, spaceFor(from), false, progress(false), commit);

        assertTrue(committed.get());
        assertFalse(from.exists());
        assertEquals("manifest\n", read(new File(to, "MANIFEST.tsv")));
        assertEquals("book contents", read(new File(to, "nested/deep/book.zim")));
        assertTrue(new File(to, "empty/child").isDirectory());
        assertJournalCleared();
    }

    @Test public void insufficientSpaceLeavesSourceUntouched() throws Exception {
        File from = pack("phone", "source");
        File to = target("card");
        long justTooLittle = LibraryMover.treeSize(from) + LibraryMover.MARGIN_BYTES - 1;
        AtomicBoolean committed = new AtomicBoolean();

        assertThrows(IOException.class, () -> LibraryMover.move(
                from, to, journal, justTooLittle, false, progress(false), () -> committed.set(true)));

        assertEquals("source", read(new File(from, "book.txt")));
        assertFalse(to.exists());
        assertFalse(committed.get());
        assertNull(journal.read());
    }

    @Test public void cancellationLeavesSourceAndCleansStaging() throws Exception {
        File from = pack("phone", "source");
        File to = target("card");

        assertThrows(InterruptedException.class, () -> LibraryMover.move(
                from, to, journal, spaceFor(from), false, progress(true), () -> { }));

        assertEquals("source", read(new File(from, "book.txt")));
        assertFalse(to.exists());
        assertFalse(staging(to).exists());
        assertJournalCleared();
    }

    @Test public void preCommitRecoveryRemovesPartialStagingCopy() throws Exception {
        File from = pack("phone", "source");
        File to = target("card");
        write(new File(staging(to), "book.txt"), "partial");
        journal.write(LibraryMover.Phase.COPY, from, to);

        LibraryMover.recover(journal, from);

        assertEquals("source", read(new File(from, "book.txt")));
        assertFalse(staging(to).exists());
        assertFalse(to.exists());
        assertJournalCleared();
    }

    @Test public void postCommitCopyRecoveryFinishesMove() throws Exception {
        File from = pack("phone", "complete");
        File to = pack("card", "complete");
        record(LibraryMover.Phase.COPY, from, to, to);

        LibraryMover.recover(journal, to);

        assertFalse(from.exists());
        assertEquals("complete", read(new File(to, "book.txt")));
        assertJournalCleared();
    }

    @Test public void postCommitDeleteRecoveryFinishesMove() throws Exception {
        File from = pack("phone", "partially deleted source");
        File to = pack("card", "complete target");
        record(LibraryMover.Phase.DELETE, from, to, to);

        LibraryMover.recover(journal, to);

        assertFalse(from.exists());
        assertEquals("complete target", read(new File(to, "book.txt")));
        assertJournalCleared();
    }

    @Test public void unavailableActiveSourceKeepsTargetAndJournal() throws Exception {
        File from = target("removed-card");
        File to = pack("phone", "target");
        record(LibraryMover.Phase.COPY, from, to, to);

        LibraryMover.recover(journal, from);

        assertEquals("target", read(new File(to, "book.txt")));
        assertNotNull(journal.read());
    }

    @Test public void unavailableActiveTargetKeepsSourceAndJournal() throws Exception {
        File from = pack("phone", "source");
        File to = target("removed-card");
        record(LibraryMover.Phase.DELETE, from, to, from);

        LibraryMover.recover(journal, to);

        assertEquals("source", read(new File(from, "book.txt")));
        assertNotNull(journal.read());
    }

    @Test public void truncatedActiveTargetRetainsSource() throws Exception {
        File from = pack("phone", "complete source");
        File to = pack("card", "complete source");
        record(LibraryMover.Phase.DELETE, from, to, from);
        write(new File(to, "book.txt"), "");

        assertThrows(IOException.class, () -> LibraryMover.recover(journal, to));

        assertEquals("complete source", read(new File(from, "book.txt")));
        assertEquals("", read(new File(to, "book.txt")));
        assertNotNull(journal.read());
    }

    @Test public void inconsistentDeleteWithActiveSourceDeletesNothing() throws Exception {
        File from = pack("phone", "source");
        File to = pack("card", "target");
        record(LibraryMover.Phase.DELETE, from, to, to);

        assertThrows(IOException.class, () -> LibraryMover.recover(journal, from));

        assertEquals("source", read(new File(from, "book.txt")));
        assertEquals("target", read(new File(to, "book.txt")));
        assertNotNull(journal.read());
    }

    @Test public void unrelatedCurrentDeletesNothing() throws Exception {
        File from = pack("phone", "source");
        File to = pack("card", "target");
        File unrelated = tmp.newFolder("other", "pack");
        record(LibraryMover.Phase.COPY, from, to, to);

        assertThrows(IOException.class, () -> LibraryMover.recover(journal, unrelated));

        assertEquals("source", read(new File(from, "book.txt")));
        assertEquals("target", read(new File(to, "book.txt")));
        assertNotNull(journal.read());
    }

    @Test public void malformedJournalDeletesNothing() throws Exception {
        File from = pack("phone", "source");
        File to = pack("card", "target");
        write(journal.file, "not a move journal");

        assertNull(LibraryMover.recover(journal, from));

        assertEquals("source", read(new File(from, "book.txt")));
        assertEquals("target", read(new File(to, "book.txt")));
    }

    @Test public void commitFailureThenRecoveryRollsBackRenamedTarget() throws Exception {
        File from = pack("phone", "source");
        File to = target("card");
        LibraryMover.Commit failingCommit = () -> { throw new IOException("commit failed"); };

        assertThrows(IOException.class, () -> LibraryMover.move(
                from, to, journal, spaceFor(from), false, progress(false), failingCommit));
        assertTrue(to.isDirectory());
        assertEquals("source", read(new File(from, "book.txt")));

        LibraryMover.recover(journal, from);

        assertEquals("source", read(new File(from, "book.txt")));
        assertFalse(to.exists());
        assertJournalCleared();
    }

    @Test public void symlinkDeletionPreservesSentinelWhereSupported() throws Exception {
        File outside = tmp.newFolder("outside");
        File sentinel = new File(outside, "keep.txt");
        write(sentinel, "keep");
        File tree = tmp.newFolder("delete-me");
        try {
            Files.createSymbolicLink(new File(tree, "linked-directory").toPath(), outside.toPath());
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            Assume.assumeNoException("symbolic links are unavailable", e);
        }

        LibraryMover.deleteTree(tree);

        assertFalse(tree.exists());
        assertEquals("keep", read(sentinel));
    }

    private static LibraryMover.Progress progress(boolean cancelled) {
        return new LibraryMover.Progress() {
            @Override public void update(long doneBytes, long totalBytes) { }
            @Override public boolean cancelled() { return cancelled; }
        };
    }

    private File pack(String parent, String contents) throws IOException {
        File dir = tmp.newFolder(parent, "pack");
        write(new File(dir, "book.txt"), contents);
        return dir;
    }

    private File target(String parent) {
        return new File(new File(tmp.getRoot(), parent), "pack");
    }

    private static File staging(File to) {
        return new File(to.getParentFile(), to.getName() + ".moving");
    }

    private static long spaceFor(File from) throws IOException {
        return LibraryMover.treeSize(from) + LibraryMover.MARGIN_BYTES;
    }

    private void record(LibraryMover.Phase phase, File from, File to, File hashBasis) throws IOException {
        journal.write(phase, from, to);
        journal.writeHashes(Map.of("book.txt", LibraryMover.hashFile(new File(hashBasis, "book.txt"))));
    }

    private void assertJournalCleared() {
        assertFalse(journal.file.exists());
        assertFalse(new File(journal.file.getPath() + ".hashes").exists());
    }

    private static void write(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) assertTrue(parent.mkdirs());
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
