package de.renew.engine.unify.impl;

import de.renew.engine.structure.StructureTypes;
import de.renew.engine.unify.Binding;
import de.renew.engine.unify.Computer;
import de.renew.util.UnsynchronizedIntStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class ComputerImpl implements Computer {
    private static final Object TODO = new Object();
    private static final Object PLANNED = new Object();
    private final long[] states;
    private final Object[] valuesAndFunctions;
    private final StructureTypes structureTypes;

    public ComputerImpl(long[] states, Object[] valuesAndFunctions, StructureTypes structureTypes) {
        this.states = states;
        this.valuesAndFunctions = valuesAndFunctions;
        this.structureTypes = structureTypes;
    }

    @Override
    public Binding compute() {
        Object[] values = new Object[states.length];
        Arrays.fill(values, TODO);

        UnsynchronizedIntStack todo = new UnsynchronizedIntStack();
        for (int i = 0; i < states.length; i++) {
            todo.add(i);
            while (!todo.isEmpty()) {
                int current = todo.getLast();
                if (values[current] == TODO) {
                    // Next time the computation will be done. This time the components will be scheduled.
                    long state = states[current];
                    int canonicalVariableId = State.stateGetCanonicalVariableId(state);
                    if (canonicalVariableId == current) {
                        int typeId = State.stateGetTypeId(state);
                        if (typeId == State.SIMPLE_TYPE || typeId == State.ELEMENT_TYPE) {
                            values[current] = valuesAndFunctions[current];
                            todo.removeLast();
                        } else {
                            values[current] = PLANNED;
                            int length = State.stateGetLengthOrIndex(state);
                            for (int j = 0; j < length; j++) {
                                int elementVariableId = current + 1 + j;
                                todo.add(elementVariableId);
                            }
                        }
                    } else {
                        values[current] = PLANNED;
                        todo.add(canonicalVariableId);
                    }
                } else if (values[current] == PLANNED) {
                    todo.removeLast();
                    long state = states[current];
                    int canonicalVariableId = State.stateGetCanonicalVariableId(state);
                    if (canonicalVariableId == current) {
                        // A structure or a computation.
                        int typeId = State.stateGetTypeId(state);
                        List<Object> elements = new ArrayList<>();
                        int length = State.stateGetLengthOrIndex(state);
                        for (int j = 0; j < length; j++) {
                            elements.add(values[current + 1 + j]);
                        }

                        if (typeId == State.COMPUTATION_TYPE) {
                            @SuppressWarnings("unchecked") Function<List<?>, ?> fun = (Function<List<?>, ?>) valuesAndFunctions[current];
                            values[current] = fun.apply(elements);
                        } else {
                            values[current] = structureTypes.getType(typeId).build(elements);
                        }
                    } else {
                        values[current] = values[canonicalVariableId];
                    }
                } else {
                    todo.removeLast();
                }
            }
        }
        return new BindingImpl(values);
    }
}
