package de.renew.engine.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ObjectArrayStructureTest {
    @Test
    void testRoundtrip() {
        ObjectArrayStructureTypeFactory factory = new ObjectArrayStructureTypeFactory();
        assertTrue(factory.canHandle(Object[].class));
        assertTrue(factory.canHandle(String[].class));
        assertFalse(factory.canHandle(List.class));
        StructureType type = factory.createStructureType(String[].class);
        assertNotNull(type);

        assertTrue(type.canHandle(String[].class));
        assertFalse(type.canHandle(Object[].class));
        assertEquals(1, type.length(new String[]{"a"}));
        assertEquals("a", type.get(new String[]{"a"}, 0));
        assertTrue(type.checkElement(0, "a"));
        assertTrue(type.checkElement(0, null));
        String[] structure = (String[]) type.build(List.of("a"));
        assertEquals(String[].class, structure.getClass());
        assertArrayEquals(new String[]{"a"}, structure);

        type.setId((short)42);
        assertEquals(42, type.getId());
    }
}