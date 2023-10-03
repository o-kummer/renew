package de.renew.engine.structure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TupleStructureTypeTest {

    private StructureType testling;

    @BeforeEach
    void initTestling() {
        testling = new TupleStructureTypeFactory().createStructureType(Tuple.class);
    }

    @Test
    void testTupleCreation() {
        assertEqualsWithHashCode(Tuple.of(),
                testling.build(List.of()));
        assertEqualsWithHashCode(Tuple.of("foo"),
                testling.build(List.of("foo")));
        assertEqualsWithHashCode(Tuple.of("foo", "bar"),
                testling.build(List.of("foo", "bar")));
        assertEqualsWithHashCode(Tuple.of("foo", "bar", "baz"),
                testling.build(List.of("foo", "bar", "baz")));
        assertEqualsWithHashCode(Tuple.of("foo", "bar", "baz", "faz"),
                testling.build(List.of("foo", "bar", "baz", "faz")));
        assertEqualsWithHashCode(Tuple.of("foo", "bar", "baz", "faz", null),
                testling.build(Arrays.asList("foo", "bar", "baz", "faz", null)));

    }

    private void assertEqualsWithHashCode(Object expected, Object actual) {
        assertEquals(expected, actual);
        assertEquals(expected.hashCode(), actual.hashCode());
    }

    @Test
    void testTupleAccessors() {
        Tuple value = Tuple.of("foo", "bar", "baz", "faz", null);

        assertEquals(5, testling.length(value));
        assertEquals("foo", testling.get(value, 0));
        assertNull(testling.get(value, 4));
    }

    @Test
    void testAllowedObjects() {
        assertTrue(testling.checkLength(42));
        assertTrue(testling.canHandle(Tuple.class));
        assertFalse(testling.canHandle(List.class));
        assertTrue(testling.checkElement(42, "foo"));
    }
}