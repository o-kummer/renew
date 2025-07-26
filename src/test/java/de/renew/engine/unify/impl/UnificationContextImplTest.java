package de.renew.engine.unify.impl;

import de.renew.engine.structure.StructureVisitor;
import de.renew.engine.structure.Tuple;
import de.renew.engine.unify.Binding;
import de.renew.engine.unify.OnBind;
import de.renew.engine.unify.Snapshot;
import de.renew.engine.unify.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UnificationContextImplTest {
    private UnificationContextImpl testling;

    @BeforeEach
    void initTestling() {
        testling = new UnificationContextImpl();
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

    @Test
    void testHasStructureTypes() {
        assertNotNull(testling.getStructureTypes());
    }

    @Test
    void testSimpleVariable() {
        Variable v1 = v();
        assertNotBound(v1);
        assertNotComputable(v1);
    }

    @Test
    void testBoundStructure() {
        Variable v1 = t("foo");
        assertBound(v1);
        assertComputable(v1);
        assertValue(Tuple.of("foo"), v1);

        Binding binding = testling.getComputer().compute();
        assertEquals(Tuple.of("foo"), binding.getValue(v1));
    }

    @Test
    void testStructureWithWrongLength() {
        assertThrows(IllegalArgumentException.class, () -> testling.structure(List.class, "foo"));
    }

    @Test
    void testUnboundStructure() {
        Variable v1 = v();
        Variable v2 = t(v1);
        assertNotBound(v2);
        assertNotComputable(v2);
        assertThrows(IllegalStateException.class, v2::getValue);
    }

    @Test
    void testDetectAlienVariable() {
        Variable v1 = new UnificationContextImpl().variable();
        assertThrows(IllegalArgumentException.class, () -> t(v1));
    }

    @Test
    void testComputableComputation() {
        Variable v1 = testling.computation((values) -> "bar", "foo");
        assertNotBound(v1);
        assertComputable(v1);

        assertBindingValue("bar", v1);
    }

    @Test
    void testUncomputableComputation() {
        Variable v1 = testling.computation((values) -> "bar", testling.variable());
        assertNotBound(v1);
        assertNotComputable(v1);
    }

    @Test
    void testBoundStructureInStructure() {
        Variable v1 = t("foo");
        Variable v2 = t(v1);
        assertBound(v2);
        assertComputable(v2);
        assertValue(Tuple.of(Tuple.of("foo")), v2);

        Binding binding = testling.getComputer().compute();
        assertEquals(Tuple.of(Tuple.of("foo")), binding.getValue(v2));
    }

    @Test
    void testComputableComputationInStructure() {
        Variable v1 = testling.computation((values) -> "bar", "foo");
        Variable v2 = t(v1);
        assertNotBound(v2);
        assertComputable(v2);

        assertBindingValue(Tuple.of("bar"), v2);
    }

    @Test
    void testBoundStructureInComputation() {
        Variable v1 = t("foo");
        Variable v2 = testling.computation((values) -> "bar", v1);
        assertNotBound(v2);
        assertComputable(v2);

        assertBindingValue("bar", v2);
    }

    @Test
    void testManyVariables() {
        Variable v = t("foo");
        for (int i = 0; i < 1000; i++) {
            v = t(v);
        }
        assertBound(v);
        assertComputable(v);
        assertNotNull(v.getValue());

        Binding binding = testling.getComputer().compute();
        assertNotNull(binding.getValue(v));
    }

    @Test
    void testDetectAlienVariables() {
        assertThrows(IllegalArgumentException.class, () -> {
            UnificationContextImpl otherContext = new UnificationContextImpl();
            t(otherContext.variable());
        });
        assertThrows(IllegalArgumentException.class, () -> t(new Variable() {
            @Override
            public boolean isBound() {
                return false;
            }

            @Override
            public boolean isComputable() {
                return false;
            }

            @Override
            public Object getValue() {
                return null;
            }

            @Override
            public void guide(StructureVisitor visitor, boolean passValue) {
            }

            @Override
            public void onBind(OnBind callback) {
            }
        }));
    }

    @Test
    void testUnifyValues() {
        assertTrue(testling.unify(null, null));
        assertTrue(testling.unify("foo", "foo"));
        assertFalse(testling.unify(null, "foo"));
        assertFalse(testling.unify("foo", null));
        assertFalse(testling.unify("bar", "foo"));
    }

    @Test
    void testUnifyVariableWithBacktracking() {
        Variable v1 = v();
        Variable v2 = t(v1);
        Snapshot snapshot = testling.snapshot();

        assertTrue(testling.unify(v1, "foo"));
        assertBound(v1);
        assertComputable(v1);
        assertEquals("foo", v1.getValue());
        assertBound(v2);
        assertComputable(v2);
        assertValue(Tuple.of("foo"), v2);

        snapshot.restore();
        assertNotBound(v1);
        assertNotComputable(v1);
        assertNotBound(v2);
        assertNotComputable(v2);

        assertTrue(testling.unify(v1, "bar"));
        assertBound(v1);
        assertComputable(v1);
        assertEquals("bar", v1.getValue());
        assertBound(v2);
        assertComputable(v2);
        assertValue(Tuple.of("bar"), v2);
    }

    @Test
    void testOnRollback() {
        boolean[] rollbackCalled = {false};
        Variable v1 = v();
        Snapshot snapshot = testling.snapshot();
        testling.onRollback(() -> rollbackCalled[0] = true);
        assertTrue(testling.unify(v1, "foo"));
        assertBound(v1);
        assertFalse(rollbackCalled[0]);

        snapshot.restore();
        assertTrue(rollbackCalled[0]);
        assertNotBound(v1);
    }

    @Test
    void testUnifyManyVariables() {
        for (int j = 0; j < 10; j++) {
            Snapshot snapshot = testling.snapshot();
            List<Variable> variables = new ArrayList<>();
            for (int i = 0; i < 10000; i++) {
                variables.add(v());
            }
            Snapshot snapshotInner = testling.snapshot();
            for (int i = 0; i < 10000; i++) {
                testling.unify(variables.get(i), i);
            }
            snapshotInner.restore();
            for (int i = 0; i < 10000; i++) {
                assertNotBound(variables.get(i));
            }
            snapshot.restore();
        }
    }

    @Test
    void testRebindVariable() {
        Variable v1 = v();
        Snapshot snapshot = testling.snapshot();

        assertTrue(testling.unify("foo", v1));
        assertBound(v1);
        assertComputable(v1);
        assertEquals("foo", v1.getValue());
        assertBindingValue("foo", v1);

        snapshot.restore();
        assertNotBound(v1);
        assertNotComputable(v1);

        assertTrue(testling.unify("bar", v1));
        assertBound(v1);
        assertComputable(v1);
        assertEquals("bar", v1.getValue());
        assertBindingValue("bar", v1);

        snapshot.restore();
        assertNotBound(v1);
        assertNotComputable(v1);
    }

    @Test
    void testRebindStructure() {
        Variable v1 = v();
        Variable v2 = t(v1);
        Snapshot snapshot = testling.snapshot();

        assertTrue(testling.unify(Tuple.of("foo"), v2));
        assertBound(v1);
        assertComputable(v1);
        assertBound(v2);
        assertComputable(v2);
        assertEquals("foo", v1.getValue());
        assertBindingValue("foo", v1);
        assertEquals(Tuple.of("foo"), v2.getValue());
        assertBindingValue(Tuple.of("foo"), v2);

        snapshot.restore();
        assertNotBound(v1);
        assertNotComputable(v1);
        assertNotBound(v2);
        assertNotComputable(v2);

        assertTrue(testling.unify(Tuple.of("bar"), v2));
        assertBound(v1);
        assertComputable(v1);
        assertBound(v2);
        assertComputable(v2);
        assertEquals("bar", v1.getValue());
        assertBindingValue("bar", v1);
        assertEquals(Tuple.of("bar"), v2.getValue());
        assertBindingValue(Tuple.of("bar"), v2);
    }

    @Test
    void testUnifyVariableFlipped() {
        Variable v1 = v();
        Variable v2 = t(v1);
        Snapshot snapshot = testling.snapshot();

        assertTrue(testling.unify("foo", v1));
        assertBound(v1);
        assertComputable(v1);
        assertEquals("foo", v1.getValue());
        assertBound(v2);
        assertComputable(v2);
        assertValue(Tuple.of("foo"), v2);

        snapshot.restore();
        assertNotBound(v1);
        assertNotComputable(v1);
        assertNotBound(v2);
        assertNotComputable(v2);
    }

    @Test
    void testUnifyVariableUsedTwice() {
        Variable v1 = v();
        Variable v2 = t(v1, v1);
        Snapshot snapshot = testling.snapshot();

        assertTrue(testling.unify(v1, "foo"));
        assertBound(v2);
        assertComputable(v2);
        assertValue(Tuple.of("foo", "foo"), v2);

        snapshot.restore();
        assertNotBound(v1);
        assertNotComputable(v1);
        assertNotBound(v2);
        assertNotComputable(v2);
    }

    @Test
    void testUnifyStructureWithBoundStructure() {
        Variable v1 = v();
        Variable v2 = t(v1);
        Snapshot snapshot = testling.snapshot();

        assertTrue(testling.unify(v2, Tuple.of("foo")));
        assertBound(v2);
        assertComputable(v2);
        assertValue(Tuple.of("foo"), v2);
        assertBound(v1);
        assertComputable(v1);
        assertEquals("foo", v1.getValue());

        snapshot.restore();
        assertNotBound(v2);
        assertNotComputable(v2);
        assertNotBound(v1);
        assertNotComputable(v1);
    }

    @Test
    void testCannotUnifyStructureWithNull() {
        Variable v1 = v();
        Variable v2 = t(v1);
        assertFalse(testling.unify(v2, null));
    }

    @Test
    void testCannotUnifyStructureWithNonStructure() {
        Variable v1 = v();
        Variable v2 = t(v1);
        assertFalse(testling.unify(v2, "foo"));
    }

    @Test
    void testCannotUnifyStructureWithStructureOfOtherType() {
        Variable v1 = v();
        Variable v2 = testling.structure(String[].class, v1);
        assertFalse(testling.unify(v2, Tuple.of("foo")));
    }

    @Test
    void testCannotUnifyStructureWithStructureOfOtherSize() {
        Variable v1 = v();
        Variable v2 = t(v1);
        assertFalse(testling.unify(v2, Tuple.of("foo", "bar")));
    }

    @Test
    void testCannotUnifyStructureWithMismatchingElement() {
        Variable v1 = v();
        Variable v2 = t(v1, "foo");
        assertFalse(testling.unify(v2, Tuple.of("foo", "bar")));
    }

    @Test
    void testCannotUnifyStructureElementWithInappropriateObject() {
        Variable v1 = v();
        Variable v2 = v();
        testling.structure(List.class, v1, v2);
        assertFalse(testling.unify(v2, "a"));
    }

    @Test
    void testUnifyToBindStructureInComputation() {
        Variable v1 = v();
        Variable v2 = t(v1);
        Variable v3 = testling.computation((values) -> "bar", v2);
        assertNotBound(v3);
        assertNotComputable(v3);

        Snapshot snapshot = testling.snapshot();
        assertTrue(testling.unify(v1, "foo"));
        assertBound(v1);
        assertBound(v2);
        assertNotBound(v3);
        assertComputable(v3);
        assertBindingValue("bar", v3);

        snapshot.restore();
        assertNotBound(v1);
        assertNotBound(v2);
        assertNotBound(v3);
        assertNotComputable(v3);
    }

    @Test
    void testUnifyVariableInComputation() {
        Variable v1 = v();
        Variable v2 = testling.computation((values) -> "bar", v1);
        Snapshot snapshot = testling.snapshot();

        assertTrue(testling.unify(v1, "foo"));
        assertEquals("foo", v1.getValue());
        assertBound(v1);
        assertNotBound(v2);
        assertComputable(v2);
        assertBindingValue("bar", v2);

        snapshot.restore();
        assertNotBound(v1);
        assertNotBound(v2);
        assertNotComputable(v2);
    }

    @Test
    void testUnifyVariableInComputationUsedTwice() {
        Variable v1 = v();
        Variable v2 = testling.computation((values) -> "bar", v1, v1);
        Snapshot snapshot = testling.snapshot();

        assertTrue(testling.unify(v1, "foo"));
        assertNotBound(v2);
        assertComputable(v2);
        assertBindingValue("bar", v2);

        snapshot.restore();
        assertNotBound(v2);
        assertNotComputable(v2);
    }

    @Test
    void testUnifyVariableInComputationInComputationUsedTwice() {
        Variable v1 = v();
        Variable v2 = testling.computation((values) -> "bar", v1);
        Variable v3 = testling.computation((values) -> "baz", v2, v2);
        Snapshot snapshot = testling.snapshot();

        assertTrue(testling.unify(v1, "foo"));
        assertNotBound(v2);
        assertComputable(v2);
        assertNotBound(v3);
        assertComputable(v3);
        assertBindingValue("bar", v2);
        assertBindingValue("baz", v3);

        snapshot.restore();
        assertNotBound(v2);
        assertNotComputable(v2);
        assertNotBound(v3);
        assertNotComputable(v3);
    }

    @Test
    void testUnifyObjectAndBoundVariable() {
        Variable v1 = v();
        Snapshot snapshot = testling.snapshot();

        assertTrue(testling.unify(v1, "foo"));
        assertBound(v1);
        assertComputable(v1);
        assertTrue(testling.unify(v1, "foo"));
        assertBound(v1);
        assertComputable(v1);
        assertTrue(testling.unify("foo", v1));
        assertBound(v1);
        assertComputable(v1);

        snapshot.restore();
        assertNotBound(v1);
        assertNotComputable(v1);
    }

    @Test
    void testUnifyObjectAndBoundStructure() {
        Variable v1 = t("foo");
        assertBound(v1);
        assertComputable(v1);
        Snapshot snapshot = testling.snapshot();

        assertTrue(testling.unify(v1, Tuple.of("foo")));

        snapshot.restore();
        assertBound(v1);
        assertComputable(v1);
    }

    @Test
    void testCannotUnifyObjectAndComputableStructure() {
        Variable v1 = testling.computation((values) -> "bar", "foo");
        Variable v2 = t(v1);
        assertNotBound(v2);
        assertComputable(v2);
        assertFalse(testling.unify(v2, Tuple.of("bar")));
    }

    @Test
    void testCannotUnifyObjectAndComputation() {
        Variable v1 = v();
        Variable v2 = testling.computation((values) -> "bar", v1);
        assertNotComputable(v2);
        assertFalse(testling.unify(v2, "bar"));
    }

    @Test
    void testUnifyVariableWithItself() {
        Variable v1 = v();
        assertTrue(testling.unify(v1, v1));
    }

    @Test
    void testUnifyBoundVariable() {
        Variable v1 = v();
        Variable v2 = v();
        assertTrue(testling.unify(v1, "foo"));
        Snapshot snapshot = testling.snapshot();

        assertTrue(testling.unify(v1, v2));
        assertBound(v2);
        assertComputable(v2);
        assertEquals("foo", v2.getValue());

        snapshot.restore();
        assertBound(v1);
        assertComputable(v1);
        assertNotBound(v2);
        assertNotComputable(v2);
    }

    @Test
    void testUnifyBoundVariableReverse() {
        Variable v1 = v();
        Variable v2 = v();
        assertTrue(testling.unify(v1, "foo"));
        Snapshot snapshot = testling.snapshot();

        assertTrue(testling.unify(v2, v1));
        assertBound(v2);
        assertComputable(v2);
        assertEquals("foo", v2.getValue());

        snapshot.restore();
        assertBound(v1);
        assertComputable(v1);
        assertNotBound(v2);
        assertNotComputable(v2);
    }

    @Test
    void testCannotUnifyStructureAndComputation() {
        Variable v1 = v();
        Variable v2 = t(v1);
        Variable computationVariable = testling.computation((values) -> "bar", v1);
        assertFalse(testling.unify(computationVariable, v2));
    }

    @Test
    void testUnifyBoundVariables() {
        Variable v1 = v();
        Variable v2 = v();
        assertTrue(testling.unify(v1, "foo"));
        assertTrue(testling.unify(v2, "foo"));
        Snapshot snapshot = testling.snapshot();

        assertTrue(testling.unify(v1, v2));

        snapshot.restore();
        assertBound(v1);
        assertBound(v2);
    }

    @Test
    void testCannotUnifyBoundVariablesWithDifferentValues() {
        Variable v1 = v();
        Variable v2 = v();
        assertTrue(testling.unify(v1, "foo"));
        assertTrue(testling.unify(v2, "bar"));
        assertFalse(testling.unify(v1, v2));

        assertBound(v1);
        assertBound(v2);
    }

    @Test
    void testCannotUnifyStructureAndComputationReverse() {
        Variable v1 = v();
        Variable v2 = t(v1);
        Variable v3 = testling.computation((values) -> "bar", v1);
        assertFalse(testling.unify(v3, v2));
    }

    @Test
    void testCannotUnifyUnboundStructuresWithWrongLength() {
        Variable v1 = v();
        Variable v2 = t(v1);
        Variable v3 = t(v1, v1);
        assertFalse(testling.unify(v2, v3));
    }

    @Test
    void testCannotUnifyDifferentComputations() {
        Variable v1 = v();
        Variable v2 = testling.computation((values) -> "bar", v1);
        Variable v3 = testling.computation((values) -> "bar", v1);
        assertFalse(testling.unify(v2, v3));
    }

    @Test
    void testCannotUnifyElementInUnboundStructures() {
        Variable v1 = v();
        Variable v2 = t("foo", v1);
        Variable v3 = t("bar", v1);
        assertFalse(testling.unify(v2, v3));
    }

    @Test
    void testEarlyCycleDetection() {
        Variable v1 = v();
        Variable v2 = t(v1);
        Variable v3 = t(v2);
        Variable v4 = t(v3);
        assertFalse(testling.unify(v4, v3));
    }

    @Test
    void testLateCycleDetection() {
        Variable v1 = v();
        Variable v2 = t(v1);
        assertFalse(testling.unify(v1, v2));
        assertNotBound(v1);
        assertNotComputable(v1);
        assertNotBound(v2);
        assertNotComputable(v2);
    }

    @Test
    void testUnifyStructureToUnifyVariables() {
        Variable v1 = v();
        Variable v2 = v();
        Variable v3 = v();
        Variable v4 = t(v1, v2);
        Variable v5 = t(v2, v3);
        assertTrue(testling.unify(v4, v5));
        assertNotBound(v1);
        assertNotBound(v2);
        assertNotBound(v3);
        assertNotBound(v4);
        assertNotBound(v5);

        assertTrue(testling.unify(v1, "foo"));
        assertBound(v1);
        assertBound(v2);
        assertBound(v3);
        assertBound(v4);
        assertBound(v5);
        assertEquals("foo", v1.getValue());
        assertEquals("foo", v2.getValue());
        assertEquals("foo", v3.getValue());
        assertValue(Tuple.of("foo", "foo"), v4);
        assertValue(Tuple.of("foo", "foo"), v5);
    }

    @Test
    void testUnifyVariableAndStructure() {
        Variable v1 = v();
        Variable v2 = v();
        Variable v3 = t(v2);
        assertTrue(testling.unify(v1, v3));
        assertNotBound(v1);
        assertNotBound(v2);
        assertNotBound(v3);
        assertTrue(testling.unify(v2, "foo"));
        assertBound(v1);
        assertBound(v2);
        assertBound(v3);
        assertValue(Tuple.of("foo"), v1);
        assertValue("foo", v2);
        assertValue(Tuple.of("foo"), v1);
    }

    @Test
    void testUnifyVariableAndStructureReverse() {
        Variable v1 = v();
        Variable v2 = v();
        Variable v3 = t(v2);
        assertTrue(testling.unify(v3, v1));
        assertNotBound(v1);
        assertNotBound(v2);
        assertNotBound(v3);
        assertTrue(testling.unify(v2, "foo"));
        assertBound(v1);
        assertBound(v2);
        assertBound(v3);
        assertValue(Tuple.of("foo"), v1);
        assertValue("foo", v2);
        assertValue(Tuple.of("foo"), v1);
    }

    @Test
    void testUnifyTwoSteps() {
        Variable v1 = v();
        Variable v2 = v();
        Variable v3 = t(v2, "bar");
        assertTrue(testling.unify(v3, v1));
        assertNotBound(v1);
        assertNotBound(v2);
        assertNotBound(v3);
        assertTrue(testling.unify(v2, "foo"));
        assertBound(v1);
        assertBound(v2);
        assertBound(v3);
        assertValue(Tuple.of("foo", "bar"), v1);
        assertValue("foo", v2);
        assertValue(Tuple.of("foo", "bar"), v1);
    }

}