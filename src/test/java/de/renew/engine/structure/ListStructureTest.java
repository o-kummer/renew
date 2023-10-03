package de.renew.engine.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ListStructureTest {
    @Test
    void testRoundtrip() {
        ListStructureTypeFactory factory = new ListStructureTypeFactory();
        assertTrue(factory.canHandle(List.class));
        assertFalse(factory.canHandle(Object[].class));
        StructureType type = factory.createStructureType(List.class);
        assertNotNull(type);

        assertTrue(type.canHandle(List.class));
        assertFalse(type.canHandle(Object[].class));
        assertEquals(2, type.length(List.of("a", "b")));
        assertEquals("a", type.get(List.of("a", "b"), 0));
        assertEquals(List.of("b"), type.get(List.of("a", "b"), 1));
        assertTrue(type.checkElement(0, "a"));
        assertTrue(type.checkElement(0, 1));
        assertFalse(type.checkElement(1, "c"));
        assertFalse(type.checkElement(1, 1));
        assertTrue(type.checkElement(1, List.of("c")));
        assertEquals(List.of("a", "b"), type.build(List.of("a", List.of("b"))));

        type.setId((short)42);
        assertEquals(42, type.getId());
    }
}