package de.renew.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UnsynchronizedIntStackTest {
    @Test
    void testRoundtrip() {
        UnsynchronizedIntStack stack = new UnsynchronizedIntStack();
        assertTrue(stack.isEmpty());
        stack.add(42);
        assertFalse(stack.isEmpty());
        assertEquals(42, stack.getLast());
        stack.removeLast();
        assertTrue(stack.isEmpty());
    }

    @Test
    void testBigStack() {
        UnsynchronizedIntStack stack = new UnsynchronizedIntStack();
        for (int i = 0; i <= 99; i++) {
            stack.add(i);
        }
        for (int i = 99; i >= 0; i--) {
            assertFalse(stack.isEmpty());
            assertEquals(i, stack.getLast());
            stack.removeLast();
        }
        assertTrue(stack.isEmpty());
    }
}