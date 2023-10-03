package de.renew.engine.structure;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StructureCursorTest {
    @Test
    void roundtrip() {
        StructureCursor testling = new StructureCursor();
        assertEquals(0L, testling.getPositionHash());

        testling.enter(1, 3, 2);
        long hash1 = testling.getPositionHash();
        assertNotEquals(0L, hash1);
        assertEquals(1, testling.getDepth());
        assertEquals(1, testling.getStructureTypeId(0));
        assertEquals(3, testling.getLength(0));
        assertEquals(2, testling.getPosition(0));

        testling.enter(4, 6, 5);
        long hash2 = testling.getPositionHash();
        assertNotEquals(0L, hash2);
        assertEquals(2, testling.getDepth());
        assertEquals(1, testling.getStructureTypeId(0));
        assertEquals(3, testling.getLength(0));
        assertEquals(2, testling.getPosition(0));
        assertEquals(4, testling.getStructureTypeId(1));
        assertEquals(6, testling.getLength(1));
        assertEquals(5, testling.getPosition(1));

        testling.leave();
        assertEquals(hash1, testling.getPositionHash());
        assertEquals(1, testling.getDepth());
        assertEquals(1, testling.getStructureTypeId(0));
        assertEquals(3, testling.getLength(0));
        assertEquals(2, testling.getPosition(0));

        testling.leave();
        assertEquals(0L, testling.getPositionHash());
    }

    @Test
    void deepNesting() {
        Set<Long> hashes = new HashSet<>();
        StructureCursor testling = new StructureCursor();
        for (int i = 0; i < 10000; i++) {
            testling.enter(1, 3, 2);
            assertTrue(hashes.add(testling.getPositionHash()));
        }
    }

    @Test
    void typeToId() {
        StructureCursor testling = new StructureCursor();
        StructureTypes structureTypes = new StructureTypes();
        StructureType type = structureTypes.getType(Tuple.class);
        testling.enter(type, 2, 1);
        long hash = testling.getPositionHash();
        testling.leave();

        testling.enter(type.getId(), 2, 1);
        assertEquals(hash, testling.getPositionHash());
    }
}