package de.renew.engine.unify.impl;

import de.renew.engine.structure.StructureTypes;
import de.renew.engine.unify.Binding;
import de.renew.engine.unify.Computer;
import de.renew.util.UnsynchronizedIntStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static de.renew.engine.unify.impl.UnificationContextImpl.*;

public class ComputerImpl implements Computer {
    private static final Object TODO = new Object();
    private static final Object PLANNED = new Object();
    private final int[] states;
    private final Object[] valuesAndFunctions;
    private final StructureTypes structureTypes;

    public ComputerImpl(int[] states, Object[] valuesAndFunctions, StructureTypes structureTypes) {
        this.states = states;
        this.valuesAndFunctions = valuesAndFunctions;
        this.structureTypes = structureTypes;
    }

    @Override
    public Binding compute() {
        Object[] values = new Object[valuesAndFunctions.length];
        Arrays.fill(values, TODO);

        UnsynchronizedIntStack todo = new UnsynchronizedIntStack();
        for (int i = 0; i < valuesAndFunctions.length; i++) {
            todo.add(i);
            while (!todo.isEmpty()) {
                int current = todo.getLast();
                if (values[current] == TODO) {
                    // Next time the computation will be done. This time the components will be scheduled.
                    int baseIndex = STATE_SIZE * current;
                    int canonicalVariableId = states[baseIndex + CANONICAL_ID_OFFSET_IN_STATE];
                    if (canonicalVariableId == current) {
                        int typeId = states[baseIndex + TYPE_ID_OFFSET_IN_STATE];
                        if (typeId == SIMPLE_TYPE || typeId == ELEMENT_TYPE) {
                            values[current] = valuesAndFunctions[current];
                            todo.removeLast();
                        } else {
                            values[current] = PLANNED;
                            int length = states[baseIndex + MISC_OFFSET_IN_STATE] & LENGTH_OR_INDEX_MASK_IN_STATE;
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
                    int baseIndex = STATE_SIZE * current;
                    int canonicalVariableId = states[baseIndex + CANONICAL_ID_OFFSET_IN_STATE];
                    if (canonicalVariableId == current) {
                        // A structure or a computation.
                        int typeId = states[baseIndex + TYPE_ID_OFFSET_IN_STATE];
                        List<Object> elements = new ArrayList<>();
                        int length = states[baseIndex + MISC_OFFSET_IN_STATE] & LENGTH_OR_INDEX_MASK_IN_STATE;
                        for (int j = 0; j < length; j++) {
                            elements.add(values[current + 1 + j]);
                        }

                        if (typeId == COMPUTATION_TYPE) {
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
