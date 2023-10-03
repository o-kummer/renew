package de.renew.engine.net.impl;

import de.renew.engine.net.SimpleWeight;
import de.renew.engine.structure.StructureCursor;
import de.renew.engine.structure.StructureGuide;
import de.renew.engine.structure.StructureTypes;
import de.renew.engine.structure.StructureVisitor;
import de.renew.engine.unify.UnificationContext;
import de.renew.engine.unify.Variable;
import de.renew.engine.unify.impl.UnificationContextImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

class PlaceInstanceImplStructureTest {
    private UnificationContext unificationContext;
    private StructureTypes structureTypes;
    private int listTypeId;
    private PlaceInstanceImpl<SimpleWeight> testling;

    public record Visit(Object element, List<Integer> position) {
    }

    @BeforeEach
    void initTestling() {
        unificationContext = new UnificationContextImpl();
        structureTypes = unificationContext.getStructureTypes();
        listTypeId = structureTypes.getType(List.class).getId();

        testling = new PlaceInstanceImpl<>(SimpleWeight.ZERO, SimpleIdSource.INSTANCE, structureTypes);
    }

    @Test
    void getSingleCandidate() {
        testling.update(SimpleWeight.updater(1), List.of("a", "b"));
        testling.update(SimpleWeight.updater(1), List.of("c", "d"));
        StructureCursor cursor = new StructureCursor();
        cursor.enter(listTypeId, 2, 0);
        String element = "a";
        long elementHash = StructureGuide.longHash(structureTypes, element);
        List<Object> candidates = testling.getCandidates(cursor, element, elementHash);
        assertEquals(List.of(List.of("a", "b")), candidates);
    }

    @Test
    void getTwoCandidates() {
        testling.update(SimpleWeight.updater(1), List.of("a", "b"));
        testling.update(SimpleWeight.updater(1), List.of("c", "d"));
        testling.update(SimpleWeight.updater(1), List.of("a", "e"));
        StructureCursor cursor = new StructureCursor();
        cursor.enter(listTypeId, 2, 0);
        String element = "a";
        long elementHash = StructureGuide.longHash(structureTypes, element);
        List<Object> candidates = testling.getCandidates(cursor, element, elementHash);
        assertEquals(Set.of(List.of("a", "b"), List.of("a", "e")), new HashSet<>(candidates));
    }

    @Test
    void getSingleCandidateAFterRemoval() {
        testling.update(SimpleWeight.updater(1), List.of("a", "b"));
        testling.update(SimpleWeight.updater(1), List.of("a", "d"));
        testling.update(SimpleWeight.updater(-1), List.of("a", "d"));
        StructureCursor cursor = new StructureCursor();
        cursor.enter(listTypeId, 2, 0);
        String element = "a";
        long elementHash = StructureGuide.longHash(structureTypes, element);
        List<Object> candidates = testling.getCandidates(cursor, element, elementHash);
        assertEquals(List.of(List.of("a", "b")), candidates);
    }
}