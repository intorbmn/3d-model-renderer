package com.shiroha.mmdskin.asset.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The fixture card was written by the studio's card.js, so this checks both sides agree. */
class ModelCardFileTest {
    @TempDir
    Path temp;

    private Path fixture() throws IOException {
        Path card = temp.resolve("Test Card.png");
        try (InputStream in = getClass().getResourceAsStream("/cards/test-card.png")) {
            assertNotNull(in);
            Files.copy(in, card);
        }
        return card;
    }

    @Test
    void extractsTheModelAndSettings() throws IOException {
        Path card = fixture();
        assertTrue(ModelCardFile.isCard(card));
        ModelCardFile.Extracted extracted = ModelCardFile.extract(card, temp.resolve("cache"));
        assertEquals("Test", extracted.name());
        assertEquals("pmx", extracted.modelType());
        assertEquals(4096, Files.size(extracted.modelFile()));
        assertEquals("texture", Files.readString(extracted.modelFile().getParent().resolve("tex/a.png")));

        ModelCardFile.Settings settings = ModelCardFile.readSettings(extracted.modelFile().getParent());
        assertEquals(1.25f, settings.scale(), 1e-6);
        assertArrayEquals(new float[] {0.9f, 0.8f, 0.7f}, settings.materialTints().get("face"), 1e-6f);

        // A second extraction reuses the folder.
        assertEquals(extracted.root(), ModelCardFile.extract(card, temp.resolve("cache")).root());
    }

    @Test
    void rejectsDamagedCards() throws IOException {
        Path card = fixture();
        byte[] bytes = Files.readAllBytes(card);
        bytes[bytes.length - 40] ^= 0x55; // inside the packed file data
        Files.write(card, bytes);
        assertThrows(IOException.class, () -> ModelCardFile.extract(card, temp.resolve("cache")));
        assertFalse(Files.exists(temp.resolve("cache").resolve("Test Card")));
    }

    @Test
    void plainImagesAreNotCards() throws IOException {
        Path png = temp.resolve("plain.png");
        byte[] bytes = Files.readAllBytes(fixture());
        Files.write(png, java.util.Arrays.copyOf(bytes, 70));
        assertFalse(ModelCardFile.isCard(png));
    }

    @Test
    void unsafePathsAreRejected() {
        assertFalse(ModelCardFile.isSafePath("../evil"));
        assertFalse(ModelCardFile.isSafePath("/abs"));
        assertFalse(ModelCardFile.isSafePath("C:/x"));
        assertFalse(ModelCardFile.isSafePath("a//b"));
        assertTrue(ModelCardFile.isSafePath("model/a.pmx"));
    }
}
