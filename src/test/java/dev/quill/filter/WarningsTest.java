package dev.quill.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WarningsTest {

    private static final long MIN = 60_000L;

    @TempDir
    File dir;

    private Warnings warnings() {
        Warnings w = new Warnings(new File(dir, "warnings.yml"), Logger.getAnonymousLogger());
        w.decayMinutes(10);
        return w;
    }

    @Test
    void countsUpAndFadesAfterTheDecayTime() {
        Warnings w = warnings();
        UUID id = UUID.randomUUID();
        assertEquals(1, w.add(id, 0));
        assertEquals(2, w.add(id, 5 * MIN));
        assertEquals(2, w.get(id, 6 * MIN));
        assertEquals(0, w.get(id, 20 * MIN));
        assertEquals(1, w.add(id, 20 * MIN));
    }

    @Test
    void clearForgetsThePlayer() {
        Warnings w = warnings();
        UUID id = UUID.randomUUID();
        w.add(id, 0);
        w.clear(id);
        assertEquals(0, w.get(id, 0));
    }

    @Test
    void survivesARestart() {
        Warnings w = warnings();
        UUID id = UUID.randomUUID();
        long now = System.currentTimeMillis();
        w.add(id, now);
        w.add(id, now);
        w.save();
        Warnings again = warnings();
        again.load();
        assertEquals(2, again.get(id, now));
    }
}
