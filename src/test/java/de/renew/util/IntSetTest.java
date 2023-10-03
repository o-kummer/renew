package de.renew.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntSetTest {
    @Test
    void roundtrip() {
        IntSet intSet = new IntSet();

        intSet.set(3);
        assertTrue(intSet.get(3));
        assertFalse(intSet.get(2));
        assertFalse(intSet.get(4));

        intSet.set(2);
        assertTrue(intSet.get(2));
        assertFalse(intSet.get(1));
        assertFalse(intSet.get(4));

        intSet.set(3);
        assertTrue(intSet.get(2));
        assertFalse(intSet.get(1));
        assertFalse(intSet.get(4));

        intSet.clear(2);
        assertTrue(intSet.get(3));
        assertFalse(intSet.get(2));
        assertFalse(intSet.get(4));
    }
}
