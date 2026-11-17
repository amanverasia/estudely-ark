package cc.esdy.ark;

import java.util.HashSet;
import java.util.Set;

/** Selection operations that never silently discard a saved subject when adding another one. */
final class LibrarySelection {
    private LibrarySelection() {}

    static Set<String> add(Set<String> selected, Set<String> verified, Set<String> additions) {
        Set<String> merged = new HashSet<>(selected);
        merged.addAll(verified);
        merged.addAll(additions);
        return merged;
    }
}
