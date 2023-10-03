package de.renew.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderedIntSetTest {
    @Test
    void roundtrip() {
        OrderedIntSet orderedIntSet = new OrderedIntSet();
        assertEquals(0, orderedIntSet.size());
        assertFalse(orderedIntSet.contains(3));

        assertTrue(orderedIntSet.add(3));
        assertEquals(1, orderedIntSet.size());
        assertEquals(3, orderedIntSet.get(0));
        assertTrue(orderedIntSet.contains(3));
        assertFalse(orderedIntSet.contains(2));
        assertFalse(orderedIntSet.contains(4));

        assertTrue(orderedIntSet.add(2));
        assertEquals(2, orderedIntSet.size());
        assertEquals(3, orderedIntSet.get(0));
        assertEquals(2, orderedIntSet.get(1));
        assertTrue(orderedIntSet.contains(2));
        assertFalse(orderedIntSet.contains(1));
        assertFalse(orderedIntSet.contains(4));

        assertFalse(orderedIntSet.add(3));
        assertEquals(2, orderedIntSet.size());
        assertEquals(3, orderedIntSet.get(0));
        assertEquals(2, orderedIntSet.get(1));
        assertTrue(orderedIntSet.contains(2));
        assertFalse(orderedIntSet.contains(1));
        assertFalse(orderedIntSet.contains(4));

        assertEquals(2, orderedIntSet.pop());
        assertEquals(1, orderedIntSet.size());
        assertEquals(3, orderedIntSet.get(0));
        assertTrue(orderedIntSet.contains(3));
        assertFalse(orderedIntSet.contains(2));
        assertFalse(orderedIntSet.contains(4));

        orderedIntSet.clear();
        assertEquals(0, orderedIntSet.size());
        assertFalse(orderedIntSet.contains(3));
    }

    @Test()
    void alreadyEmpty() {
        OrderedIntSet orderedIntSet = new OrderedIntSet();
        assertThrows(IllegalStateException.class, orderedIntSet::pop);
    }
}
