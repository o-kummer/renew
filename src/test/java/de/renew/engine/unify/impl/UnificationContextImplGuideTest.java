package de.renew.engine.unify.impl;

import de.renew.engine.structure.StructureCursor;
import de.renew.engine.structure.StructureVisitor;
import de.renew.engine.structure.Tuple;
import de.renew.engine.unify.Binding;
import de.renew.engine.unify.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class UnificationContextImplGuideTest {
    private UnificationContextImpl testling;
    private int listTypeId;
    private List<Visit> visits = new ArrayList<>();

    @Mock
    private StructureVisitor visitor;

    @BeforeEach
    void initTestling() {
        testling = new UnificationContextImpl();
        listTypeId = testling.getStructureTypes().getType(List.class).getId();
    }

    private void initGuideCapture() {
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

    public record Visit(Object element, List<Integer> position) {}

    private Visit visit(Object element, int... position) {
        List<Integer> positionList = new ArrayList<>();
        for (int value : position) {
            positionList.add(value);
        }
        return new Visit(element, positionList);

    }

    private void assertVisit(Visit... expectedVisits) {
        assertEquals(List.of(expectedVisits), visits);
    }

    private static void assertBound(Variable variable1) {
        assertTrue(variable1.isBound());
    }

    private static void assertNotBound(Variable variable1) {
        assertFalse(variable1.isBound());
    }

    private static void assertComputable(Variable variable) {
        assertTrue(variable.isComputable());
    }

    private static void assertNotComputable(Variable variable) {
        assertFalse(variable.isComputable());
    }

    private static void assertValue(Object expected, Variable variable) {
        assertEquals(expected, variable.getValue());
    }

    private void assertBindingValue(Object expected, Variable variable) {
        Binding binding = testling.getComputer().compute();
        assertEquals(expected, binding.getValue(variable));
    }

    private Variable v() {
        return testling.variable();
    }

    private Variable t(Object... objects) {
        return testling.structure(Tuple.class, objects);
    }


    /**
     * Test that the visitor is not called for a variable that contains no bound
     * substructures.
     */
    @Test
    void testGuideUnbound() {
        Variable v = v();
        v.guide(visitor, true);
        Mockito.verifyNoInteractions(visitor);
    }

    /**
     * Test that the visitor is called for a non-structure.
     */
    @Test
    void testGuideBoundValue() {
        initGuideCapture();

        Variable v = v();
        testling.unify(v, "foo");
        v.guide(visitor, true);
        assertVisit(visit("foo"));
    }

    /**
     * Test that the visitor is called for the top element of a known structure subtree.
     */
    @Test
    void testGuideBoundListValue() {
        initGuideCapture();

        Variable v = v();
        testling.unify(v, List.of("foo"));
        v.guide(visitor, true);
        assertVisit(visit(List.of("foo")));
    }

    /**
     * Test that the visitor is called for the top element of a known structure subtree.
     */
    @Test
    void testGuideHashOnly() {
        List<Long> hashes = new ArrayList<>();
        doAnswer(invocation -> {
            hashes.add(invocation.getArgument(2));
            return null;
        }).when(visitor).visit(any(StructureCursor.class), any(), anyLong());

        // Slowly build a bound structure.
        Variable v = v();
        Variable t = t(v);
        testling.unify(v, "foo");

        t.guide(visitor, true);
        ArrayList<Long> hashesWhenPassingValue = new ArrayList<>(hashes);
        hashes.clear();

        t.guide(visitor, false);
        ArrayList<Long> hashesWhenNotPassingValue = new ArrayList<>(hashes);
        assertEquals(hashesWhenPassingValue, hashesWhenNotPassingValue);
    }

    /**
     * Test that the visitor is called with the proper location of a known object
     * in a partially bound structure.
     */
    @Test
    void testGuidePartiallyBoundListValue() {
        initGuideCapture();

        Variable v = v();
        Variable structure = testling.structure(List.class, "foo", v);
        structure.guide(visitor, true);
        assertVisit(visit("foo", listTypeId, 2, 0));
    }

    /**
     * Test that a fresh visitor is used if the guide method is called from a guide call.
     */
    @Test
    void testGuideInGuide() {
        initGuideCapture();

        Variable v = v();
        Variable structure = testling.structure(List.class, "foo", v);
        structure.guide((cursor, element, hash) -> {
            structure.guide(visitor, true);
        }, true);
        assertVisit(visit("foo", listTypeId, 2, 0));
    }

    /**
     * Test that the cursor state is reset if the visitor fails.
     */
    @Test
    void testRobust() {
        initGuideCapture();

        Variable v = v();
        Variable structure = testling.structure(List.class, "foo", v);
        try {
            structure.guide((cursor, element, hash) -> {
                throw new IllegalStateException("for test");
            }, true);
            fail("exception swallowed");
        } catch (IllegalStateException e) {
            // Expected.
        }
        structure.guide(visitor, true);
        assertVisit(visit("foo", listTypeId, 2, 0));
    }
}