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

    private final StructureTypes structureTypes;

    private int maxSize = 256;
    private int size = 0;

    /**
     * Variable states encode according to {@link State}.
     * <p>
     * Only update elements of this array in these situations:
     * <ul>
     *     <li>from {@link #updateState}, making sure that the change can be undone;</li>
     *     <li>when the variable was just allocated and is known not to contain relevant data;</li>
     *     <li>when undoing a change.</li>
     * </ul>
     */
    private long[] states = new long[maxSize];

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
       - 0x02000000 + variable index, high32, low32 -> old state given by the two integers
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
        this.structureTypes = new StructureTypes(State.STRUCTURE_TYPE_ID_COUNT);
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
     * @param oldState the known old state
     * @param state the new state
     */
    private void updateState(int variableId, long oldState, long state) {
        assert states[variableId] == oldState;
        recordOldState(variableId, oldState);
        states[variableId] = state;
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
    private long getState(int variableId) {
        long state = states[variableId];
        int canonicalVariableId = State.stateGetCanonicalVariableId(state);
        if (canonicalVariableId == variableId) {
            return state;
        }
        return states[canonicalVariableId];
    }

    private void setNextVariableId(int variableId, long oldState, int nextVariableId) {
        updateState(variableId, oldState, State.settingNextId(oldState, nextVariableId));
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
            if (total >= State.MAX_VARIABLE_ID) {
                throw new IllegalStateException("unification too complex");
            }
            int newMaxSize = Math.min(State.MAX_VARIABLE_ID, Math.max(2 * maxSize, total));

            long[] newStates = new long[newMaxSize];
            System.arraycopy(states, 0, newStates, 0, size);
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

    private void recordOldState(int variableId, long oldState) {
        ensureHistoryCapacity(3);
        history[historySize++] = (int) oldState;
        history[historySize++] = (int) (oldState >> 32);
        history[historySize++] = (RECORD_OLD_STATE << 24) | variableId;
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
                        int variableId = operationAndVariable & 0x00ffffff;
                        readAndRestoreState(variableId);
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

        private void readAndRestoreState(int variableId) {
            long oldState = ((long) history[--historySize]) << 32 & 0xffffffff00000000L |
                    history[--historySize] & 0x00000000ffffffffL;
            states[variableId] = oldState;
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
                long state = states[variableId];
                int canonicalVariableId = State.stateGetCanonicalVariableId(state);
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
        long state = states[variableId];
        if (State.stateIsComputable(state)) {
            // No cycle is possible, because all paths lead to bound values eventually.
            occurrenceCheckComplete.set(variableId);
        } else {
            int typeId = State.stateGetTypeId(state);
            boolean isComplex = typeId != State.SIMPLE_TYPE && typeId != State.ELEMENT_TYPE;
            if (isComplex) {
                occurrenceCheckInProgress.set(variableId);
                // Process all transitive elements before continuing with this variable.
                int length = State.stateGetLengthOrIndex(state);
                for (int i = 0; i < length; i++) {
                    int elementVariablesId = variableId + 1 + i;
                    long elementState = states[elementVariablesId];
                    int canonicalElementVariableId = State.stateGetCanonicalVariableId(elementState);
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
        states[variableId] = State.state(variableId, variableId, -1, false, false, 0);
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
        return insertComplexVariable(State.COMPUTATION_TYPE, fun, objects);
    }

    private VariableImpl insertComplexVariable(int typeId, Object valueOrFunction, List<?> objects) {
        // Check objects early to avoid having a partially updated internal state.
        checkVariablesAreLocal(objects);
        int variableId = newVariableId(objects.size() + 1);
        int length = objects.size();

        boolean bound = typeId != State.COMPUTATION_TYPE;
        boolean computable = true;
        for (int i = 0; i < length; i++) {
            Object o = objects.get(i);
            int elementVariableId = variableId + 1 + i;

            if (o instanceof VariableImpl variable) {
                // The element variable assumes the state of the other variable.
                long canonicalVariableState = getState(variable.getVariableId());
                int canonicalVariableId = State.stateGetCanonicalVariableId(canonicalVariableState);
                setNextVariableId(canonicalVariableId, canonicalVariableState, elementVariableId);
                boolean elementBound = State.stateIsBound(canonicalVariableState);
                boolean elementComputable = State.stateIsComputable(canonicalVariableState);
                states[elementVariableId] = State.state(canonicalVariableId,
                        State.stateGetNextVariableId(canonicalVariableState),
                        State.ELEMENT_TYPE,
                        elementBound,
                        elementComputable,
                        i);
                bound &= elementBound;
                computable &= elementComputable;
            } else {
                // The element variable is fully bound immediately.
                states[elementVariableId] = State.state(elementVariableId,
                        elementVariableId,
                        State.ELEMENT_TYPE,
                        true,
                        true,
                        i);
                updateValueOrFunction(elementVariableId, o);
            }
        }

        states[variableId] = State.state(variableId, variableId, typeId, bound, computable, length);
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
        long state = getState(variableId);
        int canonicalVariableId = State.stateGetCanonicalVariableId(state);
        if (State.stateIsComputable(state)) {
            if (!State.stateIsBound(state)) {
                // A variable that is computable and not bound contains a reference to a computation.
                // Such a variable is never unifiable with an exact value.
                return false;
            }
            Object value = getValue(canonicalVariableId);
            return Objects.equals(value, o);
        }
        int typeId = State.stateGetTypeId(state);
        if (typeId == State.SIMPLE_TYPE || typeId == State.ELEMENT_TYPE) {
            return assignVariable(canonicalVariableId, o);
        }
        if (typeId == State.COMPUTATION_TYPE) {
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
        int length = State.stateGetLengthOrIndex(state);
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
            toMarkBound.removeLast();
            long canonicalState = states[canonicalVariableId];
            if (State.stateIsComputable(canonicalState)) {
                // This equivalence class has already been processed.
                break;
            }
            updateState(canonicalVariableId, canonicalState, canonicalState | State.BOUND_MASK_IN_STATE | State.COMPUTABLE_MASK_IN_STATE);
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
            long state = states[current];
            if (State.stateIsElement(state)) {
                int index = State.stateGetLengthOrIndex(state);

                int aggregateVariableId = current - 1 - index;
                long aggregateVariableState = getState(aggregateVariableId);
                int canonicalAggregateVariableId = State.stateGetCanonicalVariableId(aggregateVariableState);
                int aggregateVariableTypeId = State.stateGetTypeId(aggregateVariableState);
                boolean isStructure = State.COMPUTATION_TYPE != aggregateVariableTypeId;
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
                int length = State.stateGetLengthOrIndex(aggregateVariableState);
                boolean isAggregateBound = isStructure;
                boolean isAggregateComputable = true;
                for (int i = 0; i < length; i++) {
                    int elementVariableId = canonicalAggregateVariableId + 1 + i;
                    long elementState = getState(elementVariableId);
                    isAggregateComputable &= State.stateIsComputable(elementState);
                    isAggregateBound &= State.stateIsBound(elementState);
                }
                if (isAggregateBound) {
                    toMarkBound.add(canonicalAggregateVariableId);
                } else if (isAggregateComputable) {
                    toMarkComputable.add(canonicalAggregateVariableId);
                }
            }
            current = State.stateGetNextVariableId(state);
        } while (current != canonicalVariableId);
        return true;
    }

    private void processToMarkComputable() {
        while (!toMarkComputable.isEmpty()) {
            int canonicalVariableId = toMarkComputable.getLast();
            toMarkComputable.removeLast();
            long canonicalState = states[canonicalVariableId];
            if (State.stateIsComputable(canonicalState)) {
                // This equivalence class has already been processed.
                break;
            }
            updateState(canonicalVariableId, canonicalState, canonicalState | State.COMPUTABLE_MASK_IN_STATE);

            processToMarkComputableAllEquivalents(canonicalVariableId);
        }
    }

    /**
     * For all equivalent element variables, check whether they become computable.
     */
    private void processToMarkComputableAllEquivalents(int canonicalVariableId) {
        int current = canonicalVariableId;
        do {
            long state = states[current];
            if (State.stateIsElement(state)) {
                int index = State.stateGetLengthOrIndex(state);
                int aggregateVariableId = current - 1 - index;
                long aggregateVariableState = getState(aggregateVariableId);
                int canonicalAggregateVariableId = State.stateGetCanonicalVariableId(aggregateVariableState);
                int length = State.stateGetLengthOrIndex(aggregateVariableState);
                if (isAggregateComputable(length, canonicalAggregateVariableId)) {
                    toMarkComputable.add(canonicalAggregateVariableId);
                }
            }
            current = State.stateGetNextVariableId(state);
        } while (current != canonicalVariableId);
    }

    private boolean isAggregateComputable(int length, int canonicalAggregateVariableId) {
        boolean isAggregateComputable = true;
        for (int i = 0; i < length; i++) {
            int elementVariableId = canonicalAggregateVariableId + 1 + i;
            long elementState = getState(elementVariableId);
            isAggregateComputable &= State.stateIsComputable(elementState);
        }
        return isAggregateComputable;
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
        int canonicalVariableId1 = State.stateGetCanonicalVariableId(states[variableId1]);
        int canonicalVariableId2 = State.stateGetCanonicalVariableId(states[variableId2]);
        if (canonicalVariableId1 == canonicalVariableId2) {
            return true;
        }
        return unifyCanonicalVariables(canonicalVariableId1, canonicalVariableId2);
    }

    private boolean unifyCanonicalVariables(int canonicalVariableId1, int canonicalVariableId2) {
        long state1 = states[canonicalVariableId1];
        if (State.stateIsBound(state1)) {
            return unifyVariableAndObject(canonicalVariableId2, getValue(canonicalVariableId1));
        }
        long state2 = states[canonicalVariableId2];
        if (State.stateIsBound(state2)) {
            return unifyVariableAndObject(canonicalVariableId1, getValue(canonicalVariableId2));
        }

        int typeId1 = State.stateGetTypeId(state1);
        int typeId2 = State.stateGetTypeId(state2);
        boolean isComplex1 = typeId1 != State.SIMPLE_TYPE && typeId1 != State.ELEMENT_TYPE;
        boolean isComplex2 = typeId2 != State.SIMPLE_TYPE && typeId2 != State.ELEMENT_TYPE;

        if (isComplex1) {
            if (isComplex2) {
                if (!unifyComplexVariables(canonicalVariableId1, canonicalVariableId2, typeId1, typeId2, state1, state2)) {
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

    private boolean unifyComplexVariables(int canonicalVariableId1, int canonicalVariableId2, int typeId1, int typeId2, long state1, long state2) {
        if (typeId1 != typeId2) {
            // Two different complex types cannot be unified.
            return false;
        }
        if (typeId1 == State.COMPUTATION_TYPE) {
            // Two different computations cannot be unified.
            return false;
        }
        // Two structures of the same type.
        int length1 = State.stateGetLengthOrIndex(state1);
        int length2 = State.stateGetLengthOrIndex(state2);
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
        int current = variableId;
        do {
            long state = states[current];
            updateState(current, state, State.settingCanonicalId(state, canonicalVariableId));
            current = State.stateGetNextVariableId(state);
        } while (current != variableId);
        long state = states[variableId];
        long replacementState = states[canonicalVariableId];
        setNextVariableId(variableId, state, State.stateGetNextVariableId(replacementState));
        setNextVariableId(canonicalVariableId, replacementState, State.stateGetNextVariableId(state));
    }


    @Override
    public Computer getComputer() {
        long[] clonedStates = new long[size];
        System.arraycopy(states, 0, clonedStates, 0, size);
        Object[] clonedValuesAndFunctions = new Object[size];
        System.arraycopy(valuesAndFunctions, 0, clonedValuesAndFunctions, 0, size);
        return new ComputerImpl(clonedStates, clonedValuesAndFunctions, structureTypes);
    }

    boolean isBound(int variableId) {
        int canonicalVariableId = State.stateGetCanonicalVariableId(states[variableId]);
        return State.stateIsBound(states[canonicalVariableId]);
    }

    boolean isComputable(int variableId) {
        int canonicalVariableId = State.stateGetCanonicalVariableId(states[variableId]);
        return State.stateIsComputable(states[canonicalVariableId]);
    }

    Object getValue(int variableId) {
        int canonicalVariableId = State.stateGetCanonicalVariableId(states[variableId]);
        long state = states[canonicalVariableId];
        if (!State.stateIsBound(state)) {
            throw new IllegalStateException("variable is not bound");
        }
        int typeId = State.stateGetTypeId(state);
        if (typeId == State.SIMPLE_TYPE || typeId == State.ELEMENT_TYPE) {
            return valuesAndFunctions[canonicalVariableId];
        } else {
            int length = State.stateGetLengthOrIndex(state);
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
        int canonicalVariableId = State.stateGetCanonicalVariableId(states[variableId]);
        long state = states[canonicalVariableId];
        if (State.stateIsBound(state)) {
            if (passValue) {
                Object value = getValue(variableId);
                long valueHash = StructureGuide.longHash(structureTypes, value);
                visitor.visit(cursor, value, valueHash);
            } else {
                visitor.visit(cursor, null, longHash(variableId));
            }
        } else {
            int typeId = State.stateGetTypeId(state);
            if (typeId < State.STRUCTURE_TYPE_ID_COUNT) {
                int length = State.stateGetLengthOrIndex(state);
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
        int canonicalVariableId = State.stateGetCanonicalVariableId(states[variableId]);
        long state = states[canonicalVariableId];
        int typeId = State.stateGetTypeId(state);
        if (typeId < State.STRUCTURE_TYPE_ID_COUNT) {
            int length = State.stateGetLengthOrIndex(state);
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
            long state = states[i];
            int typeId = State.stateGetTypeId(state);
            String prefix = typeId == State.ELEMENT_TYPE ? " " : "";
            String infix = typeId == State.ELEMENT_TYPE ? "" : " ";
            System.out.printf("%s%d%s %d %d %d %d %s %s%n", prefix, i, infix, State.stateGetCanonicalVariableId(state), State.stateGetNextVariableId(state),
                    typeId, State.stateGetLengthOrIndex(state), State.stateIsBound(state), State.stateIsComputable(state));
        }
        System.out.println();
    }
}
