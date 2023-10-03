package de.renew.engine.structure;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecordStructureTest {
    public record TestRecord(String s, int i){}
    public record NonTestRecord(String s){}
    record NonPublicTestRecord(String s){}

    @Test
    void testRoundtrip() {
        RecordStructureTypeFactory factory = new RecordStructureTypeFactory();
        assertTrue(factory.canHandle(TestRecord.class));
        assertFalse(factory.canHandle(NonPublicTestRecord.class));
        assertFalse(factory.canHandle(List.class));
        StructureType type = factory.createStructureType(TestRecord.class);
        assertNotNull(type);

        assertTrue(type.canHandle(TestRecord.class));
        assertFalse(type.canHandle(Record.class));
        assertFalse(type.canHandle(NonTestRecord.class));
        assertEquals(2, type.length(new TestRecord("a", 1)));
        assertEquals("a", type.get(new TestRecord("a", 1), 0));
        assertEquals(1, type.get(new TestRecord("a", 1), 1));
        assertNull(type.get(new TestRecord(null, 1), 0));
        assertTrue(type.checkElement(0, "a"));
        assertTrue(type.checkElement(0, null));
        assertFalse(type.checkElement(0, 1));
        assertTrue(type.checkElement(1, 1));
        assertFalse(type.checkElement(1, null));
        assertTrue(type.checkLength(2));
        assertFalse(type.checkLength(3));
        assertEquals(new TestRecord("a", 1), type.build(List.of("a", 1)));
        ArrayList<Object> containsNull = new ArrayList<>();
        containsNull.add(null);
        containsNull.add(1);
        assertEquals(new TestRecord(null, 1), type.build(containsNull));

        type.setId((short)42);
        assertEquals(42, type.getId());
    }
}