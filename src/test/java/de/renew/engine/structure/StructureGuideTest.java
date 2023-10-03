package de.renew.engine.structure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class StructureGuideTest {
    private StructureTypes structureTypes;
    private int listTypeId;
    private StructureGuide testling;

    @Mock
    private StructureVisitor visitor;

    public record Visit(Object element, List<Integer> position) {}

    private Visit visit(Object element, int... position) {
        List<Integer> positionList = new ArrayList<>();
        for (int value : position) {
            positionList.add(value);
        }
        return new Visit(element, positionList);

    }

    private List<Visit> visits = new ArrayList<>();

    private void assertVisit(Visit... expectedVisits) {
        assertEquals(List.of(expectedVisits), visits);
    }

    @BeforeEach
    void initTestling() {
        structureTypes = new StructureTypes();
        listTypeId = structureTypes.getType(List.class).getId();

        testling = new StructureGuide(structureTypes);

        doAnswer(invocation -> {
            StructureCursor cursor = invocation.getArgument(0);
            Object element = invocation.getArgument(1);
            List<Integer> position = new ArrayList<>();
            for (int i = 0; i < cursor.getDepth(); i++) {
                position.addAll(List.of(cursor.getStructureTypeId(i), cursor.getLength(i), cursor.getPosition(i)));
            }
            visits.add(new Visit(element, position));
            return null;
        }).when(visitor).visit(any(StructureCursor.class), any(), anyLong());
    }

    @Test
    void object() {
        Object o = new Object();
        testling.guide(visitor, o);
        assertVisit(visit(o));
    }

    @Test
    void list() {
        testling.guide(visitor, List.of("a", "b"));
        assertVisit(
                visit("a", listTypeId, 2, 0),
                visit("b", listTypeId, 2, 1, listTypeId, 1, 0),
                visit(List.of("b"), listTypeId, 2, 1),
                visit(List.of("a", "b"))
                );
    }
}