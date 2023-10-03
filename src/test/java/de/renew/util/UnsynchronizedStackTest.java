package de.renew.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UnsynchronizedStackTest {
    @Test
    void testRoundtrip() {
        UnsynchronizedStack<Object> stack = new UnsynchronizedStack<>();
        assertTrue(stack.isEmpty());
        stack.add("a");
        assertFalse(stack.isEmpty());
        assertEquals("a", stack.getLast());
        stack.removeLast();
        assertTrue(stack.isEmpty());
    }

    @Test
    void testBigStack() {
        UnsynchronizedStack<Object> stack = new UnsynchronizedStack<>();
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