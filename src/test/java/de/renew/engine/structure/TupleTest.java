package de.renew.engine.structure;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class TupleTest {
    private void testTuple(Object ... expectedElements) {
        Tuple tuple = Tuple.of(expectedElements);
        String string = tuple.toString();
        assertEquals(expectedElements.length, tuple.size());
        for (int i = 0; i < tuple.size(); i++) {
            assertEquals(expectedElements[i], tuple.get(i));
            assertTrue(string.contains(Objects.toString(tuple.get(i))));
        }
        assertThrowsExactly(IndexOutOfBoundsException.class, () -> tuple.get(tuple.size()));
        assertThrowsExactly(IndexOutOfBoundsException.class, () -> tuple.get(-1));
    }

    @Test
    void testTuple0() {
        testTuple();
    }

    @Test
    void testTuple1() {
        testTuple("foo");
    }

    @Test
    void testTuple2() {
        testTuple("foo", "bar");
    }

    @Test
    void testTuple3() {
        testTuple("foo", "bar", "baz");
    }

    @Test
    void testTuple4() {
        testTuple("foo", "bar", "baz", "faz");
    }

    @Test
    void testTuple5() {
        testTuple("foo", "bar", "baz", "faz", null);
    }

    @Test
    void testEquals() {
        assertEquals(Tuple.of("foo"), Tuple.of("foo"));
        assertNotEquals(Tuple.of("foo"), Tuple.of("bar"));
        assertNotEquals(Tuple.of("foo"), Tuple.of("foo", "bar"));
        assertNotEquals(Tuple.of("foo", "bar"), Tuple.of("foo"));
        assertNotEquals(Tuple.of("foo"), "foo");
    }
}