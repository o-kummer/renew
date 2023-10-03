package de.renew.engine.structure;

import de.renew.util.LongMath;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static de.renew.engine.structure.LongHashUtil.*;
import static org.junit.jupiter.api.Assertions.*;

class LongHashUtilTest {
    @Test
    void longPeriod() {
        assertNotEquals(1, LongMath.pow(HASH_FACTOR1, LongMath.pow(2, 61)));
        assertNotEquals(1, LongMath.pow(HASH_FACTOR2, LongMath.pow(2, 61)));
        assertNotEquals(1, LongMath.pow(HASH_FACTOR3, LongMath.pow(2, 61)));
    }

    @Test
    void noCollisions() {
        noCollisions(HASH_FACTOR1, HASH_FACTOR2);
        noCollisions(HASH_FACTOR1, HASH_FACTOR3);
        noCollisions(HASH_FACTOR2, HASH_FACTOR3);
    }

    private void noCollisions(long f1, long f2) {
        Set<Long> hashes = new HashSet<>();
        int iterations = 100_000;
        // Doing the full test with 100_000_000 iterations takes 8GB heap.
        for (int i = 1; i < iterations; i++) {
            hashes.add(LongMath.pow(f1, i));
        }
        for (int i = 1; i < iterations; i++) {
            assertFalse(hashes.contains(LongMath.pow(f2, i)));
        }
    }
}