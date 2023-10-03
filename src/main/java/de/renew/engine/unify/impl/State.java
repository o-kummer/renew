package de.renew.engine.unify.impl;

/*
Bit layout of long state record:

size                  16                      16                       16          1          1              14
pos                   63..48                  47..32                   31..16      15         14             13..0

simple variable       canonical equivalent    next equivalent          0xffff      bound      computable
structure variable    canonical equivalent    next equivalent          type        bound      computable     length
element variable      canonical equivalent    next equivalent          0xfffe      bound      computable     index
computation           canonical equivalent    next equivalent          0xfffd                 computable     length

bound=1 implies computable=1
 */
class State {
    /**
     * Static code only.
     */
    private State() {}

    public static final int SIMPLE_TYPE = 0xffff;
    public static final int ELEMENT_TYPE = 0xfffe;
    public static final int COMPUTATION_TYPE = 0xfffd;
    public static final int STRUCTURE_TYPE_ID_COUNT = 0xfffd;

    public static final int TYPE_ID_OFFSET_IN_STATE = 16;
    public static final int NEXT_ID_OFFSET_IN_STATE = 32;
    public static final int CANONICAL_ID_OFFSET_IN_STATE = 48;
    public static final long TYPE_ID_MASK_IN_STATE = 0x00000000ffff0000L;
    public static final long NEXT_ID_MASK_IN_STATE = 0x0000ffff00000000L;
    public static final long CANONICAL_ID_MASK_IN_STATE = 0xffff000000000000L;
    public static final long BOUND_MASK_IN_STATE = 0x0000000000008000L;
    public static final long COMPUTABLE_MASK_IN_STATE = 0x0000000000004000L;
    public static final long LENGTH_OR_INDEX_MASK_IN_STATE = 0x3fffL;
    public static final int MAX_VARIABLE_ID = 0x10000;

    static long state(int canonicalEquivalent,
                      int nextEquivalent,
                      int type,
                      boolean bound,
                      boolean computable,
                      int lengthOrIndex) {
        return ((long) canonicalEquivalent << CANONICAL_ID_OFFSET_IN_STATE) |
                ((long) nextEquivalent << NEXT_ID_OFFSET_IN_STATE) & NEXT_ID_MASK_IN_STATE |
                ((long) type << TYPE_ID_OFFSET_IN_STATE) & TYPE_ID_MASK_IN_STATE |
                (bound ? BOUND_MASK_IN_STATE : 0) |
                (computable ? COMPUTABLE_MASK_IN_STATE : 0) |
                lengthOrIndex & LENGTH_OR_INDEX_MASK_IN_STATE;
    }

    static int stateGetCanonicalVariableId(long state) {
        return ((int) (state >> CANONICAL_ID_OFFSET_IN_STATE)) & 0xffff;
    }

    static int stateGetNextVariableId(long state) {
        return ((int) (state >> NEXT_ID_OFFSET_IN_STATE)) & 0xffff;
    }

    static int stateGetTypeId(long state) {
        return ((int) (state >> TYPE_ID_OFFSET_IN_STATE)) & 0xffff;
    }

    static int stateGetLengthOrIndex(long state) {
        return ((int) (state & LENGTH_OR_INDEX_MASK_IN_STATE)) & 0xffff;
    }

    static boolean stateIsElement(long state) {
        return stateGetTypeId(state) == ELEMENT_TYPE;
    }

    static boolean stateIsBound(long state) {
        return (state & BOUND_MASK_IN_STATE) == BOUND_MASK_IN_STATE;
    }

    static boolean stateIsComputable(long state) {
        return (state & COMPUTABLE_MASK_IN_STATE) == COMPUTABLE_MASK_IN_STATE;
    }

    public static long settingCanonicalId(long state, int canonicalVariableId) {
        return state & ~CANONICAL_ID_MASK_IN_STATE | ((long)canonicalVariableId << CANONICAL_ID_OFFSET_IN_STATE);
    }

    static long settingNextId(long oldState, int nextVariableId) {
        return oldState & ~NEXT_ID_MASK_IN_STATE | ((long)nextVariableId << NEXT_ID_OFFSET_IN_STATE) & NEXT_ID_MASK_IN_STATE;
    }
}
