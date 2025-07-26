package de.renew.engine.unify.impl;

import de.renew.engine.structure.*;
import de.renew.engine.unify.Computer;
import de.renew.engine.unify.Snapshot;
import de.renew.engine.unify.UnificationContext;
import de.renew.engine.unify.Variable;
import de.renew.util.UnsynchronizedIntStack;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/*
To store:
- all variables:
  - canonical equivalent
    - a structure variable, if there is an equivalent structure variable
    - a computation, if there is an equivalent computation (which unify only with themselves)
  - next equivalent
  - isBound, if the value is known now
  - isComputable, if the value will be retrievable from {@link Computer}
- ordinary variables:
  - value, if bound
- structure variables:
  - type
  - length
  - value, if bound and retrieved, as a cache
- element variables
  - index in the containing structure or computation variable
- computation
  - length
  - function

Structure variables and computations are directly followed by their element variables.

For accessing the state of a variable, always retrieve the state of the canonical equivalent.
*/
public class UnificationContextImpl implements UnificationContext {
    private static final int RECORD_OLD_STATE = 0;
    private static final int RECORD_NULL_VARIABLE = 1;
    private static final int RECORD_SIZE = 2;
    private static final int RECORD_ON_ROLLBACK = 3;

    static final int MISC_OFFSET_IN_STATE = 0;
    static final int TYPE_ID_OFFSET_IN_STATE = 1;
    static final int NEXT_ID_OFFSET_IN_STATE = 2;
    static final int CANONICAL_ID_OFFSET_IN_STATE = 3;
    static final int STATE_SIZE = 4;
    static final int MAX_VARIABLE_ID = 0x3fffffff;

    static final int BOUND_MASK_IN_STATE = 0x80000000;
    static final int COMPUTABLE_MASK_IN_STATE = 0x40000000;
    static final int LENGTH_OR_INDEX_MASK_IN_STATE = 0x3fffffff;

    static final int SIMPLE_TYPE = 0xffffffff;
    static final int ELEMENT_TYPE = 0xfffffffe;
    static final int COMPUTATION_TYPE = 0xfffffffd;
    static final int STRUCTURE_TYPE_ID_COUNT = 0x7fffffff;

    private final StructureTypes structureTypes;

    private int maxSize = 256;
    private int size = 0;

    /**
     * Variable states are recorded in groups of 4 integers.
     *
     * <ul>
     *     <li>bits 0..29: length for structures and computations, index for elements; bit 30: bound; bit 31: computable (bound=1 implies computable=1)</li>
     *     <li>type id for structures; special codes for other kinds of variables</li>
     *     <li>id of next equivalent variable</li>
     *     <li>id of canonical variable</li>
     * </ul>
     * <p>
     * Only update elements of this array in these situations:
     * <ul>
     *     <li>from {@link #updateState}, making sure that the change can be undone;</li>
     *     <li>when the variable was just allocated and is known not to contain relevant data;</li>
     *     <li>when undoing a change.</li>
     * </ul>
     */
    private int[] states = new int[STATE_SIZE * maxSize];

    /**
     * Access this array for a canonical variable id. It contains:
     * For a computable variable, the function computing the value.
     * For a bound structure variables, either null, if the value has not been
     * constructed, or the structure value.
     * For other bound variables, the value.
     * <p>
     * Only update elements of this array in these situations:
     * <ul>
     *     <li>from {@link #updateValueOrFunction}, making sure that the change can be undone;</li>
     *     <li>when undoing a change.</li>
     * </ul>
     */
    private Object[] valuesAndFunctions = new Object[maxSize];

    private int historyMaxSize = 8192;
    private int historySize = 0;
    /*
       Interpretation of array content (start from the highest index):
       - 0x00000000 + size -> old size given in the lower bits
       - 0x01000000 + variable index -> value was null
       - 0x02000000 + state index, state -> old state entry
       - 0x03000000 -> runnable on rollback was registered
     */
    private int[] history = new int[historyMaxSize];
    private List<Object> historyData = new ArrayList<>();

    /**
     * A stack for remembering variables that must be marked bound.
     * This stack must be cleared after use.
     */
    private final UnsynchronizedIntStack toMarkBound = new UnsynchronizedIntStack();
    /**
     * A stack for remembering variables that must be marked computable.
     * This stack must be cleared after use.
     */
    private final UnsynchronizedIntStack toMarkComputable = new UnsynchronizedIntStack();

    private final BitSet unificationInProgress = new BitSet();

    private final UnsynchronizedIntStack needsOccurrenceCheck = new UnsynchronizedIntStack();
    private final UnsynchronizedIntStack occurrenceCheckStack = new UnsynchronizedIntStack();
    private final BitSet occurrenceCheckComplete = new BitSet();
    private final BitSet occurrenceCheckInProgress = new BitSet();
    /**
     * The structure cursor to use except in the unlikely case or recursive calls
     * that need more than one structure cursor.
     */
    private StructureCursor defaultCursor = new StructureCursor();

    public UnificationContextImpl() {
        this.structureTypes = new StructureTypes(STRUCTURE_TYPE_ID_COUNT);
    }

    @Override
    public StructureTypes getStructureTypes() {
        return structureTypes;
    }

    /**
     * Update an entry in {@link #states}, making sure to record the change
     * in the undo history.
     *
     * @param variableId the variable
     * @param offset the offset of the state entry
     * @param oldState the known old state
     * @param state the new state
     */
    private void updateState(int variableId, int offset, int oldState, int state) {
        int index = variableId * STATE_SIZE + offset;
        assert states[index] == oldState;
        recordOldState(index, oldState);
        states[index] = state;
    }

    private int getState(int variableId, int offset) {
        int index = variableId * STATE_SIZE + offset;
        return states[index];
    }

    /**
     * Update an entry in {@link #valuesAndFunctions}, making sure to record the change
     * in the undo history.
     *
     * @param variableId the variable
     * @param valueOrFunction the object to set
     */
    private void updateValueOrFunction(int variableId, Object valueOrFunction) {
        assert valuesAndFunctions[variableId] == null;
        recordNullValue(variableId);
        valuesAndFunctions[variableId] = valueOrFunction;
    }

    /**
     * Return the state of the canonical equivalent of the given variable.
     */
    private int getCanonicalVariableId(int variableId) {
        return getState(variableId, CANONICAL_ID_OFFSET_IN_STATE);
    }

    private int getMiscForCanonicalVariable(int variableId) {
        return getState(variableId, MISC_OFFSET_IN_STATE);
    }

    private int getLengthOrIndexForCanonicalVariable(int variableId) {
        return getState(variableId, MISC_OFFSET_IN_STATE) & LENGTH_OR_INDEX_MASK_IN_STATE;
    }

    private boolean isComputableForCanonicalVariable(int variableId) {
        return (getState(variableId, MISC_OFFSET_IN_STATE) & COMPUTABLE_MASK_IN_STATE) != 0;
    }

    private boolean isBoundForCanonicalVariable(int variableId) {
        return (getState(variableId, MISC_OFFSET_IN_STATE) & BOUND_MASK_IN_STATE) != 0;
    }

    private int getTypeForCanonicalVariable(int variableId) {
        return getState(variableId, TYPE_ID_OFFSET_IN_STATE);
    }

    private int getNextIdForCanonicalVariable(int variableId) {
        return getState(variableId, NEXT_ID_OFFSET_IN_STATE);
    }

    private void setNextVariableId(int variableId, int oldNextVariableId, int nextVariableId) {
        updateState(variableId, NEXT_ID_OFFSET_IN_STATE, oldNextVariableId, nextVariableId);
    }

    /**
     * Check that all variables mentioned in the argument list are variables of this unification context.
     */
    private void checkVariablesAreLocal(List<?> objects) {
        for (Object object : objects) {
            checkVariableIsLocal(object);
        }
    }

    /**
     * Check that the given object belongs to this unification context, if it is a variable.
     */
    private void checkVariableIsLocal(Object object) {
        if (object instanceof Variable) {
            if (!(object instanceof VariableImpl)) {
                throw new IllegalArgumentException("Variable with wrong implementation detected");
            }
            if (((VariableImpl) object).getUnificationContext() != this) {
                throw new IllegalArgumentException("Variable from other context detected");
            }
        }
    }

    private void ensureCapacity(int request) {
        int total = size + request;
        if (total > maxSize) {
            if (total >= MAX_VARIABLE_ID) {
                throw new IllegalStateException("unification too complex");
            }
            int newMaxSize = Math.min(MAX_VARIABLE_ID, Math.max(2 * maxSize, total));

            int[] newStates = new int[STATE_SIZE * newMaxSize];
            System.arraycopy(states, 0, newStates, 0, STATE_SIZE * size);
            states = newStates;

            Object[] newValuesAndFunctions = new Object[newMaxSize];
            System.arraycopy(valuesAndFunctions, 0, newValuesAndFunctions, 0, size);
            valuesAndFunctions = newValuesAndFunctions;

            maxSize = newMaxSize;
        }
    }

    /**
     * Allocate the given number of contiguous variable ids and return the smallest variable id.
     * @param count the number of ids to allocated
     * @return the smallest allocated variable id
     */
    private int newVariableId(int count) {
        ensureCapacity(count);

        int oldSize = size;
        recordSize(oldSize);
        size = oldSize + count;
        return oldSize;
    }

    private void ensureHistoryCapacity(int request) {
        int total = historySize + request;
        if (total > historyMaxSize) {
            int newHistoryMaxSize = historyMaxSize * 2;
            int[] newHistory = new int[newHistoryMaxSize];
            System.arraycopy(history, 0, newHistory, 0, historySize);
            history = newHistory;
            historyMaxSize = newHistoryMaxSize;
        }
    }

    private void recordSize(int size) {
        ensureHistoryCapacity(1);
        history[historySize++] = (RECORD_SIZE << 24) | size;
    }

    private void recordNullValue(int variableId) {
        ensureHistoryCapacity(1);
        history[historySize++] = (RECORD_NULL_VARIABLE << 24) | variableId;
    }

    private void recordOldState(int index, int oldState) {
        ensureHistoryCapacity(3);
        history[historySize++] = oldState;
        history[historySize++] = (RECORD_OLD_STATE << 24) | index;
    }

    @Override
    public void onRollback(Runnable runnable) {
        ensureHistoryCapacity(1);
        history[historySize++] = RECORD_ON_ROLLBACK << 24;
        historyData.add(runnable);
    }

    private class SnapshotImpl implements Snapshot {
        private final int historyPosition;

        SnapshotImpl(int historyPosition) {
            this.historyPosition = historyPosition;
        }

        @Override
        public void restore() {
            while (historySize > historyPosition) {
                int operationAndVariable = history[--historySize];
                int operation = (operationAndVariable >> 24) & 0xff;
                switch (operation) {
                    case RECORD_SIZE: {
                        size = operationAndVariable & 0x00ffffff;
                        break;
                    }
                    case RECORD_NULL_VARIABLE: {
                        int variableId = operationAndVariable & 0x00ffffff;
                        valuesAndFunctions[variableId] = null;
                        break;
                    }
                    case RECORD_OLD_STATE: {
                        int index = operationAndVariable & 0x00ffffff;
                        int oldState = history[--historySize];
                        states[index] = oldState;
                        break;
                    }
                    case RECORD_ON_ROLLBACK: {
                        Runnable runnable = (Runnable) historyData.remove(historyData.size() - 1);
                        try {
                            runnable.run();
                        } catch (RuntimeException e) {
                            // TODO log?
                        }
                        break;
                    }
                    default:
                        throw new IllegalStateException();
                }
            }
        }

    }

    @Override
    public Snapshot snapshot() {
        return new SnapshotImpl(historySize);
    }

    private void registerOccurrenceCheck(int variableId) {
        needsOccurrenceCheck.add(variableId);
    }

    private boolean occurrenceCheck() {
        try {
            while (!needsOccurrenceCheck.isEmpty()) {
                int variableId = needsOccurrenceCheck.getLast();
                needsOccurrenceCheck.removeLast();

                // Only check the canonical equivalents. If they don't contain cycles, no variable does.
                int canonicalVariableId = getCanonicalVariableId(variableId);
                occurrenceCheckStack.add(canonicalVariableId);
                if (!occurrenceCheckInternal()) {
                    return false;
                }
            }
        } finally {
            needsOccurrenceCheck.clear();
            occurrenceCheckStack.clear();
            occurrenceCheckComplete.clear();
            occurrenceCheckInProgress.clear();
        }
        return true;
    }

    private boolean occurrenceCheckInternal() {
        while (!occurrenceCheckStack.isEmpty()) {
            int variableId = occurrenceCheckStack.getLast();
            if (occurrenceCheckInProgress.get(variableId)) {
                // We arrive at the variable again after processing all transitive elements.
                // There is no cycle.
                occurrenceCheckStack.removeLast();
                occurrenceCheckComplete.set(variableId);
            } else if (occurrenceCheckComplete.get(variableId)) {
                // The variable was already checked while processing a sibling. No cycles were found.
                occurrenceCheckStack.removeLast();
            } else {
                if (occurrenceCheckVariable(variableId)) return false;
            }
        }
        return true;
    }

    private boolean occurrenceCheckVariable(int variableId) {
        if (isComputableForCanonicalVariable(variableId)) {
            // No cycle is possible, because all paths lead to bound values eventually.
            occurrenceCheckComplete.set(variableId);
        } else {
            int typeId = getTypeForCanonicalVariable(variableId);
            boolean isComplex = typeId != SIMPLE_TYPE && typeId != ELEMENT_TYPE;
            if (isComplex) {
                occurrenceCheckInProgress.set(variableId);
                // Process all transitive elements before continuing with this variable.
                int length = getLengthOrIndexForCanonicalVariable(variableId);
                for (int i = 0; i < length; i++) {
                    int elementVariablesId = variableId + 1 + i;
                    int canonicalElementVariableId = getCanonicalVariableId(elementVariablesId);
                    if (occurrenceCheckInProgress.get(canonicalElementVariableId)) {
                        // A cycle was found.
                        return true;
                    }
                    occurrenceCheckStack.add(canonicalElementVariableId);
                }
            } else {
                // No nested variables.
                occurrenceCheckComplete.set(variableId);
            }
        }
        return false;
    }


    @Override
    public Variable variable() {
        ensureCapacity(1);

        int variableId = newVariableId(1);
        int baseIndex = STATE_SIZE * variableId;
        states[baseIndex + MISC_OFFSET_IN_STATE] = 0;
        states[baseIndex + TYPE_ID_OFFSET_IN_STATE] = SIMPLE_TYPE;
        states[baseIndex + NEXT_ID_OFFSET_IN_STATE] = variableId;
        states[baseIndex + CANONICAL_ID_OFFSET_IN_STATE] = variableId;
        return new VariableImpl(this, variableId);
    }

    @Override
    public Variable structure(Class<?> clazz, Object... objects) {
        return structure(clazz, List.of(objects));
    }

    @Override
    public Variable structure(Class<?> clazz, List<?> objects) {
        StructureType type = structureTypes.getType(clazz);
        if (!type.checkLength(objects.size())) {
            throw new IllegalArgumentException("Structures of type " + clazz.getCanonicalName() +
                    " do not support " +objects.size() + " components");
        }

        int typeId = type.getId();

        return insertComplexVariable(typeId, null, objects);
    }

    @Override
    public Variable computation(Function<List<?>, ?> fun, Object... objects) {
        return computation(fun, List.of(objects));
    }

    @Override
    public Variable computation(Function<List<?>, ?> fun, List<?> objects) {
        return insertComplexVariable(COMPUTATION_TYPE, fun, objects);
    }

    private VariableImpl insertComplexVariable(int typeId, Object valueOrFunction, List<?> objects) {
        // Check objects early to avoid having a partially updated internal state.
        checkVariablesAreLocal(objects);
        int variableId = newVariableId(objects.size() + 1);
        int length = objects.size();

        boolean bound = typeId != COMPUTATION_TYPE;
        boolean computable = true;
        for (int i = 0; i < length; i++) {
            Object o = objects.get(i);
            int elementVariableId = variableId + 1 + i;

            if (o instanceof VariableImpl variable) {
                // The element variable assumes the state of the other variable.
                int canonicalVariableId = getCanonicalVariableId(variable.getVariableId());
                int nextId = getNextIdForCanonicalVariable(canonicalVariableId);
                setNextVariableId(canonicalVariableId, nextId, elementVariableId);
                boolean elementBound = isBoundForCanonicalVariable(canonicalVariableId);
                boolean elementComputable = isComputable(canonicalVariableId);

                int baseIndex = STATE_SIZE * elementVariableId;
                states[baseIndex + MISC_OFFSET_IN_STATE] = i | (elementBound ? BOUND_MASK_IN_STATE : 0) | (elementComputable ? COMPUTABLE_MASK_IN_STATE : 0);
                states[baseIndex + TYPE_ID_OFFSET_IN_STATE] = ELEMENT_TYPE;
                states[baseIndex + NEXT_ID_OFFSET_IN_STATE] = nextId;
                states[baseIndex + CANONICAL_ID_OFFSET_IN_STATE] = canonicalVariableId;

                bound &= elementBound;
                computable &= elementComputable;
            } else {
                // The element variable is fully bound immediately.
                int baseIndex = STATE_SIZE * elementVariableId;
                states[baseIndex + MISC_OFFSET_IN_STATE] = i | BOUND_MASK_IN_STATE | COMPUTABLE_MASK_IN_STATE;
                states[baseIndex + TYPE_ID_OFFSET_IN_STATE] = ELEMENT_TYPE;
                states[baseIndex + NEXT_ID_OFFSET_IN_STATE] = elementVariableId;
                states[baseIndex + CANONICAL_ID_OFFSET_IN_STATE] = elementVariableId;
                updateValueOrFunction(elementVariableId, o);
            }
        }

        int baseIndex = STATE_SIZE * variableId;
        states[baseIndex + MISC_OFFSET_IN_STATE] = length | (bound ? BOUND_MASK_IN_STATE : 0) | (computable ? COMPUTABLE_MASK_IN_STATE : 0);
        states[baseIndex + TYPE_ID_OFFSET_IN_STATE] = typeId;
        states[baseIndex + NEXT_ID_OFFSET_IN_STATE] = variableId;
        states[baseIndex + CANONICAL_ID_OFFSET_IN_STATE] = variableId;
        updateValueOrFunction(variableId, valueOrFunction);
        return new VariableImpl(this, variableId);
    }

    @Override
    public boolean unify(Object o1, Object o2) {
        checkVariableIsLocal(o1);
        checkVariableIsLocal(o2);

        if (o1 instanceof VariableImpl v1) {
            if (o2 instanceof VariableImpl v2) {
                return unifyVariablesAtomically(v1.getVariableId(), v2.getVariableId());
            } else {
                return unifyVariableAndObjectAtomically(v1.getVariableId(), o2);
            }
        } else if (o2 instanceof VariableImpl v2) {
            return unifyVariableAndObjectAtomically(v2.getVariableId(), o1);
        } else {
            return Objects.equals(o1, o2);
        }
    }

    private boolean unifyVariableAndObjectAtomically(int variableId, Object o) {
        Snapshot snapshot = snapshot();
        boolean result = unifyVariableAndObject(variableId, o);
        if (result) {
            result = occurrenceCheck();
        }
        if (!result) {
            snapshot.restore();
        }
        return result;
    }

    private boolean unifyVariableAndObject(int variableId, Object o) {
        int canonicalVariableId = getCanonicalVariableId(variableId);
        boolean bound = isBoundForCanonicalVariable(canonicalVariableId);
        boolean computable = isComputableForCanonicalVariable(canonicalVariableId);
        if (computable) {
            if (!bound) {
                // A variable that is computable and not bound contains a reference to a computation.
                // Such a variable is never unifiable with an exact value.
                return false;
            }
            Object value = getValue(canonicalVariableId);
            return Objects.equals(value, o);
        }
        int typeId = getTypeForCanonicalVariable(canonicalVariableId);
        if (typeId == SIMPLE_TYPE || typeId == ELEMENT_TYPE) {
            return assignVariable(canonicalVariableId, o);
        }
        if (typeId == COMPUTATION_TYPE) {
            return false;
        }
        // A structure.
        if (o == null) {
            return false;
        }
        StructureType type = structureTypes.getType(typeId);
        if (!type.canHandle(o.getClass())) {
            return false;
        }
        int length = getLengthOrIndexForCanonicalVariable(canonicalVariableId);
        if (length != type.length(o)) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            boolean unifiable = unifyVariableAndObject(canonicalVariableId + 1 + i, type.get(o, i));
            if (!unifiable) {
                return false;
            }
        }
        return true;
    }

    private boolean assignVariable(int canonicalVariableId, Object o) {
        updateValueOrFunction(canonicalVariableId, o);

        toMarkBound.add(canonicalVariableId);
        if (!processToMarkBound()) {
            return false;
        }
        processToMarkComputable();
        return true;
    }

    private boolean processToMarkBound() {
        while (!toMarkBound.isEmpty()) {
            int canonicalVariableId = toMarkBound.getLast();
            int oldMisc = getMiscForCanonicalVariable(canonicalVariableId);
            toMarkBound.removeLast();
            if ((oldMisc & COMPUTABLE_MASK_IN_STATE) != 0) {
                // This equivalence class has already been processed.
                break;
            }
            updateState(canonicalVariableId, MISC_OFFSET_IN_STATE, oldMisc, oldMisc | BOUND_MASK_IN_STATE | COMPUTABLE_MASK_IN_STATE);
            boolean valueIsKnown = false;
            Object value = null;

            if (!processToMarkBoundAllEquivalents(canonicalVariableId, valueIsKnown, value)) {
                return false;
            }
        }
        return true;
    }

    /**
     * For all equivalent element variables, check whether their aggregates accept the
     * value and whether they become bound or computable.
     */
    private boolean processToMarkBoundAllEquivalents(int canonicalVariableId, boolean valueIsKnown, Object value) {
        int current = canonicalVariableId;
        do {
            if (getTypeForCanonicalVariable(current) == ELEMENT_TYPE) {
                int index = getLengthOrIndexForCanonicalVariable(current);

                int aggregateVariableId = current - 1 - index;
                int canonicalAggregateVariableId = getCanonicalVariableId(aggregateVariableId);
                int aggregateVariableTypeId = getTypeForCanonicalVariable(canonicalAggregateVariableId);
                boolean isStructure = aggregateVariableTypeId != COMPUTATION_TYPE;
                if (isStructure) {
                    StructureType aggregateVariableType = structureTypes.getType(aggregateVariableTypeId);
                    if (!valueIsKnown) {
                        valueIsKnown = true;
                        value = getValue(canonicalVariableId);
                    }
                    if (!aggregateVariableType.checkElement(index, value)) {
                        return false;
                    }
                }
                int length = getLengthOrIndexForCanonicalVariable(canonicalAggregateVariableId);
                boolean isAggregateBound = isStructure;
                boolean isAggregateComputable = true;
                for (int i = 0; i < length; i++) {
                    int elementVariableId = canonicalAggregateVariableId + 1 + i;
                    int canonicalElementVariableId = getCanonicalVariableId(elementVariableId);
                    int elementMisc = getMiscForCanonicalVariable(canonicalElementVariableId);
                    isAggregateComputable &= (elementMisc & COMPUTABLE_MASK_IN_STATE) != 0;
                    isAggregateBound &= (elementMisc & BOUND_MASK_IN_STATE) != 0;
                }
                if (isAggregateBound) {
                    toMarkBound.add(canonicalAggregateVariableId);
                } else if (isAggregateComputable) {
                    toMarkComputable.add(canonicalAggregateVariableId);
                }
            }
            current = getNextIdForCanonicalVariable(current);
        } while (current != canonicalVariableId);
        return true;
    }

    private void processToMarkComputable() {
        while (!toMarkComputable.isEmpty()) {
            int canonicalVariableId = toMarkComputable.getLast();
            toMarkComputable.removeLast();
            int misc = getMiscForCanonicalVariable(canonicalVariableId);
            if ((misc & COMPUTABLE_MASK_IN_STATE) != 0) {
                // This equivalence class has already been processed.
                break;
            }
            updateState(canonicalVariableId, MISC_OFFSET_IN_STATE, misc, misc | COMPUTABLE_MASK_IN_STATE);

            processToMarkComputableAllEquivalents(canonicalVariableId);
        }
    }

    /**
     * For all equivalent element variables, check whether they become computable.
     */
    private void processToMarkComputableAllEquivalents(int canonicalVariableId) {
        int current = canonicalVariableId;
        do {
            if (getTypeForCanonicalVariable(current) == ELEMENT_TYPE) {
                int index = getLengthOrIndexForCanonicalVariable(current);
                int aggregateVariableId = current - 1 - index;
                int canonicalAggregateVariableId = getCanonicalVariableId(aggregateVariableId);
                int length = getLengthOrIndexForCanonicalVariable(canonicalAggregateVariableId);
                if (isAggregateComputable(length, canonicalAggregateVariableId)) {
                    toMarkComputable.add(canonicalAggregateVariableId);
                }
            }
            current = getNextIdForCanonicalVariable(current);
        } while (current != canonicalVariableId);
    }

    private boolean isAggregateComputable(int length, int canonicalAggregateVariableId) {
        for (int i = 0; i < length; i++) {
            int elementVariableId = canonicalAggregateVariableId + 1 + i;
            int canonicalElementVariableId = getCanonicalVariableId(elementVariableId);
            if (!isComputableForCanonicalVariable(canonicalElementVariableId)) {
                return false;
            }
        }
        return true;
    }

    private boolean unifyVariablesAtomically(int variableId1, int variableId2) {
        Snapshot snapshot = snapshot();
        boolean result = unifyVariables(variableId1, variableId2);
        if (result) {
            result = occurrenceCheck();
        }
        if (!result) {
            snapshot.restore();
        }
        return result;
    }

    private boolean unifyVariables(int variableId1, int variableId2) {
        int canonicalVariableId1 = getCanonicalVariableId(variableId1);
        int canonicalVariableId2 = getCanonicalVariableId(variableId2);
        if (canonicalVariableId1 == canonicalVariableId2) {
            return true;
        }
        return unifyCanonicalVariables(canonicalVariableId1, canonicalVariableId2);
    }

    private boolean unifyCanonicalVariables(int canonicalVariableId1, int canonicalVariableId2) {
        int misc1 = getMiscForCanonicalVariable(canonicalVariableId1);
        if ((misc1 & BOUND_MASK_IN_STATE) != 0) {
            return unifyVariableAndObject(canonicalVariableId2, getValue(canonicalVariableId1));
        }
        int misc2 = getMiscForCanonicalVariable(canonicalVariableId2);
        if ((misc2 & BOUND_MASK_IN_STATE) != 0) {
            return unifyVariableAndObject(canonicalVariableId1, getValue(canonicalVariableId2));
        }

        int typeId1 = getTypeForCanonicalVariable(canonicalVariableId1);
        int typeId2 = getTypeForCanonicalVariable(canonicalVariableId2);
        boolean isComplex1 = typeId1 != SIMPLE_TYPE && typeId1 != ELEMENT_TYPE;
        boolean isComplex2 = typeId2 != SIMPLE_TYPE && typeId2 != ELEMENT_TYPE;

        if (isComplex1) {
            if (isComplex2) {
                if (!unifyComplexVariables(canonicalVariableId1, canonicalVariableId2, typeId1, typeId2, misc1, misc2)) {
                    return false;
                }
            } else {
                assumeCanonicalVariableId(canonicalVariableId2, canonicalVariableId1);
                registerOccurrenceCheck(canonicalVariableId1);
            }
        } else {
            assumeCanonicalVariableId(canonicalVariableId1, canonicalVariableId2);
            if (isComplex2) {
                registerOccurrenceCheck(canonicalVariableId2);
            }
        }
        return true;
    }

    private boolean unifyComplexVariables(int canonicalVariableId1, int canonicalVariableId2, int typeId1, int typeId2, int misc1, int misc2) {
        if (typeId1 != typeId2) {
            // Two different complex types cannot be unified.
            return false;
        }
        if (typeId1 == COMPUTATION_TYPE) {
            // Two different computations cannot be unified.
            return false;
        }
        // Two structures of the same type.
        int length1 = misc1 & LENGTH_OR_INDEX_MASK_IN_STATE;
        int length2 = misc2 & LENGTH_OR_INDEX_MASK_IN_STATE;
        if (length1 != length2) {
            // Two structures of different length cannot be unified.
            return false;
        }

        if (unificationInProgress.get(canonicalVariableId1) || unificationInProgress.get(canonicalVariableId2)) {
            return false;
        }
        assumeCanonicalVariableId(canonicalVariableId2, canonicalVariableId1);
        unificationInProgress.set(canonicalVariableId1);
        try {
            for (int i = 0; i < length1; i++) {
                boolean elementsUnifiable = unifyVariables(canonicalVariableId1 + 1 + i, canonicalVariableId2 + 1 + i);
                if (!elementsUnifiable) {
                    return false;
                }
            }
        } finally {
            unificationInProgress.clear(canonicalVariableId1);
        }
        registerOccurrenceCheck(canonicalVariableId1);
        return true;
    }

    /**
     * Make all equivalents of a variable assume a given a canonical variable.
     *
     * @param variableId the variable to modify
     * @param canonicalVariableId the canonical variable that the variable will defer to
     */
    private void assumeCanonicalVariableId(int variableId, int canonicalVariableId) {
        int oldCanonicalVariableId = getCanonicalVariableId(variableId);
        int current = variableId;
        do {
            updateState(current, CANONICAL_ID_OFFSET_IN_STATE, oldCanonicalVariableId, canonicalVariableId);
            current = getNextIdForCanonicalVariable(current);
        } while (current != variableId);
        int nextId1 = getNextIdForCanonicalVariable(variableId);
        int nextId2 = getNextIdForCanonicalVariable(canonicalVariableId);
        setNextVariableId(variableId, nextId1, nextId2);
        setNextVariableId(canonicalVariableId, nextId2, nextId1);
    }


    @Override
    public Computer getComputer() {
        int[] clonedStates = new int[STATE_SIZE * size];
        System.arraycopy(states, 0, clonedStates, 0, STATE_SIZE * size);
        Object[] clonedValuesAndFunctions = new Object[size];
        System.arraycopy(valuesAndFunctions, 0, clonedValuesAndFunctions, 0, size);
        return new ComputerImpl(clonedStates, clonedValuesAndFunctions, structureTypes);
    }

    boolean isBound(int variableId) {
        int canonicalVariableId = getCanonicalVariableId(variableId);
        return isBoundForCanonicalVariable(canonicalVariableId);
    }

    boolean isComputable(int variableId) {
        int canonicalVariableId = getCanonicalVariableId(variableId);
        return isComputableForCanonicalVariable(canonicalVariableId);
    }

    Object getValue(int variableId) {
        int canonicalVariableId = getCanonicalVariableId(variableId);
        int misc = getMiscForCanonicalVariable(canonicalVariableId);
        if ((misc & BOUND_MASK_IN_STATE) == 0) {
            throw new IllegalStateException("variable is not bound");
        }
        int typeId = getTypeForCanonicalVariable(canonicalVariableId);
        if (typeId == SIMPLE_TYPE || typeId == ELEMENT_TYPE) {
            return valuesAndFunctions[canonicalVariableId];
        } else {
            int length = misc & LENGTH_OR_INDEX_MASK_IN_STATE;
            StructureType type = structureTypes.getType(typeId);
            List<Object> elements = new ArrayList<>();
            for (int i = 0; i < length; i++) {
                Object element = getValue(canonicalVariableId + 1 + i);
                elements.add(element);
            }
            return type.build(elements);
        }
    }

    public void guide(StructureVisitor visitor, int variableId, boolean passValue) {
        StructureCursor cursor;
        boolean usingDefaultCursor;
        if (defaultCursor == null) {
            cursor = new StructureCursor();
            usingDefaultCursor = false;
        } else {
            cursor = defaultCursor;
            defaultCursor = null;
            usingDefaultCursor = true;
        }
        try {
            guide(cursor, visitor, variableId, passValue);
        } finally {
            if (usingDefaultCursor) {
                defaultCursor = cursor;
            }
        }
    }

    private void guide(StructureCursor cursor, StructureVisitor visitor, int variableId, boolean passValue) {
        int canonicalVariableId = getCanonicalVariableId(variableId);
        int misc = getMiscForCanonicalVariable(canonicalVariableId);
        if ((misc & BOUND_MASK_IN_STATE) != 0) {
            if (passValue) {
                Object value = getValue(variableId);
                long valueHash = StructureGuide.longHash(structureTypes, value);
                visitor.visit(cursor, value, valueHash);
            } else {
                visitor.visit(cursor, null, longHash(variableId));
            }
        } else {
            int typeId = getTypeForCanonicalVariable(canonicalVariableId);
            if (typeId >= 0 && typeId < STRUCTURE_TYPE_ID_COUNT) {
                int length = misc & LENGTH_OR_INDEX_MASK_IN_STATE;
                for (int i = 0; i < length; i++) {
                    cursor.enter(typeId, length, i);
                    try {
                        guide(cursor, visitor, canonicalVariableId + 1 + i, passValue);
                    } finally {
                        cursor.leave();
                    }
                }
            }
        }
    }

    /**
     * Compute the long hash of a bound variable.
     *
     * @param variableId the id of the variable
     */
    private long longHash(int variableId) {
        int canonicalVariableId = getCanonicalVariableId(variableId);
        int typeId = getTypeForCanonicalVariable(canonicalVariableId);
        if (typeId >= 0 && typeId < STRUCTURE_TYPE_ID_COUNT) {
            int length = getLengthOrIndexForCanonicalVariable(canonicalVariableId);
            long hash = LongHashUtil.baseStructureHash(typeId, length);
            for (int i = 0; i < length; i++) {
                long elementHash = longHash(canonicalVariableId + 1 + i);
                hash = LongHashUtil.mergeElementHash(hash, elementHash);
            }
            return hash;
        } else {
            return Objects.hashCode(valuesAndFunctions[canonicalVariableId]);
        }
    }

    // For tests.
    public void dump() {
        System.out.println("V  C N T I/L bound computable");
        for (int i = 0; i < this.size; i++) {
            int typeId = getTypeForCanonicalVariable(i);
            String prefix = typeId == ELEMENT_TYPE ? " " : "";
            String infix = typeId == ELEMENT_TYPE ? "" : " ";
            System.out.printf("%s%d%s %d %d %d %d %s %s%n", prefix, i, infix, getCanonicalVariableId(i), getNextIdForCanonicalVariable(i),
                    typeId, getLengthOrIndexForCanonicalVariable(i), isBoundForCanonicalVariable(i), isComputableForCanonicalVariable(i));
        }
        System.out.println();
    }
}
