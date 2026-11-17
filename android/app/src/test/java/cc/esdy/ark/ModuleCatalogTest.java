package cc.esdy.ark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/** Builds the catalogue from the real v1.0 MANIFEST.tsv (same paths and sizes as the torrent). */
public class ModuleCatalogTest {
    private static ModuleCatalog fromManifest() throws Exception {
        List<String> paths = new ArrayList<>();
        List<Long> sizes = new ArrayList<>();
        try (InputStream in = ModuleCatalogTest.class.getResourceAsStream("/MANIFEST.tsv")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] c = line.split("\t");
                paths.add(c[0]);
                sizes.add(Long.parseLong(c[1]));
            }
        }
        return ModuleCatalog.build(paths, sizes);
    }

    @Test public void phoneModulesInOrder() throws Exception {
        ModuleCatalog c = fromManifest();
        List<String> ids = new ArrayList<>();
        for (ModuleCatalog.Module m : c.modules) ids.add(m.id);
        assertEquals(List.of(
            "Core/01-General-Knowledge", "Core/02-Health-and-Sanitation", "Core/03-Food-and-Growing",
            "Core/04-Repair-and-Practical-Skills", "Core/05-Maps/Desktop-Map", "Core/06-Education-and-Books",
            "Core/07-Computing-and-Documentation", "Extras/iFixit", "Extras/Energypedia"), ids);
    }

    @Test public void desktopOnlyPartsAreLeftOut() throws Exception {
        ModuleCatalog c = fromManifest();
        for (ModuleCatalog.Module m : c.modules) {
            assertFalse(m.id, m.id.startsWith("AI/"));
            assertFalse(m.id, m.id.startsWith("Shared-Readers"));
            assertFalse(m.id, m.id.contains("Android-Organic-Maps"));
        }
    }

    @Test public void sizesComeFromTheFileList() throws Exception {
        ModuleCatalog c = fromManifest();
        ModuleCatalog.Module food = c.find("Core/03-Food-and-Growing");
        assertNotNull(food);
        assertEquals(2, food.files);
        assertEquals(112499438L + 97822565L, food.bytes);
        ModuleCatalog.Module map = c.find("Core/05-Maps/Desktop-Map");
        assertTrue("map should hold the 2.6 GB tiles", map.bytes > 2_500_000_000L);
        assertTrue(c.find("Extras/iFixit").optional);
        assertFalse(food.optional);
    }

    @Test public void folderRules() {
        assertNull(ModuleCatalog.folderOf("README.txt"));
        assertEquals("Core/03-Food-and-Growing", ModuleCatalog.folderOf("Core/03-Food-and-Growing/x.zim"));
        assertEquals("Core/05-Maps/Desktop-Map", ModuleCatalog.folderOf("Core/05-Maps/Desktop-Map/fonts/glyphs.bin"));
        assertEquals("Shared Readers", ModuleCatalog.titleFrom("Shared-Readers"));
        assertEquals("Food and Growing", ModuleCatalog.titleFrom("Core/03-Food-and-Growing"));
    }
}
