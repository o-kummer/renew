package de.renew.engine.structure;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StructureTypesTest {
    public record TestRecord(String s, int i){}

    @Test
    void testRoundtrip() {
        StructureTypes testling = new StructureTypes();
        StructureType type = testling.getType(ArrayList.class);
        assertNotNull(type);
        assertTrue(type.canHandle(ArrayList.class));
        StructureType repeatedType = testling.getType(ArrayList.class);
        assertNotNull(repeatedType);
        assertTrue(repeatedType.canHandle(ArrayList.class));
        assertTrue(type == repeatedType);
        assertTrue(type == testling.getType(type.getId()));

        StructureType otherType = testling.getType(TestRecord.class);
        assertNotNull(otherType);
        assertTrue(otherType.canHandle(TestRecord.class));
        assertTrue(type != otherType);
        assertTrue(type.getId() != otherType.getId());
        assertTrue(otherType == testling.getType(otherType.getId()));

        assertNull(testling.getType(HashSet.class));
    }

    @Test
    void testTwoListTypes() {
        StructureTypes testling = new StructureTypes();
        StructureType type = testling.getType(ArrayList.class);
        assertNotNull(type);
        int typeId = type.getId();
        assertTrue(type.canHandle(List.class));
        StructureType type2 = testling.getType(LinkedList.class);
        assertEquals(typeId, type2.getId());
        assertEquals(type, type2);
    }

    @Test
    void testNotAStructure() {
        StructureTypes testling = new StructureTypes();
        StructureType type = testling.getType(String.class);
        assertNull(type);
    }
}