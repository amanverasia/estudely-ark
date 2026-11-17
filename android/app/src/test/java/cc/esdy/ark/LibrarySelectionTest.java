package cc.esdy.ark;

import static org.junit.Assert.assertEquals;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class LibrarySelectionTest {
    @Test public void addingKeepsAnActiveDownloadAndSavedSubjects() {
        assertEquals(Set.of("food", "map", "repair"), LibrarySelection.add(
                Set.of("food", "map"), Set.of("food"), Set.of("repair")));
    }

    @Test public void savedSubjectsAreRetainedEvenIfAbsentFromTheUiSelection() {
        assertEquals(Set.of("food", "computing", "health"), LibrarySelection.add(
                Set.of(), Set.of("food", "computing"), Set.of("health")));
    }

    @Test public void mergingIsIdempotentAndDoesNotMutateInputs() {
        Set<String> selected = new HashSet<>(Set.of("map"));
        Set<String> ready = new HashSet<>(Set.of("food"));
        Set<String> additions = new HashSet<>(Set.of("map", "repair"));
        Set<String> first = LibrarySelection.add(selected, ready, additions);
        assertEquals(first, LibrarySelection.add(first, ready, additions));
        assertEquals(Set.of("map"), selected);
        assertEquals(Set.of("food"), ready);
        assertEquals(Set.of("map", "repair"), additions);
    }
}
