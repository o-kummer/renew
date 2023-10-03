package de.renew.engine.structure;

/**
 * A structure cursor points to a specific location in a nested hierarchy of structs.
 * It can provide a long hash value for the given position.
 */
public class StructureCursor {

    private static final int INITIAL_MAX_DEPTH = 8;

    private static final int SLOTS_PER_STRUCTURE = 3;
    public static final int LENGTH_OFFSET = 1;
    public static final int POSITION_OFFSET = 2;

    private int depth = 0;
    private long hash = 0L;
    private long[] hashes = new long[INITIAL_MAX_DEPTH + 1];
    private int[] typesAndPositions = new int[INITIAL_MAX_DEPTH * SLOTS_PER_STRUCTURE];

    public int getDepth() {
        return depth;
    }

    public void enter(StructureType type, int length, int pos) {
        enter(type.getId(), length, pos);
    }

    public void enter(int typeId, int length, int pos) {
        int oldDepth = this.depth;
        if (oldDepth + 1 == hashes.length) {
            long[] newHashes = new long[oldDepth * 2 + 1];
            System.arraycopy(hashes, 0, newHashes, 0, hashes.length);
            hashes = newHashes;

            int[] newTypesAndPositions = new int[oldDepth * 2 * SLOTS_PER_STRUCTURE];
            System.arraycopy(typesAndPositions, 0, newTypesAndPositions, 0, typesAndPositions.length);
            typesAndPositions = newTypesAndPositions;
        }

        long newHash = this.hash;
        typesAndPositions[SLOTS_PER_STRUCTURE * oldDepth] = typeId;
        // All parameters are expected to be small non-negative values.
        // Make sure to avoid an unchanged hash if all are 0.
        newHash = (newHash + 1 + typeId) * LongHashUtil.HASH_FACTOR1;
        typesAndPositions[SLOTS_PER_STRUCTURE * oldDepth + LENGTH_OFFSET] = length;
        newHash = (newHash + length) * LongHashUtil.HASH_FACTOR1;
        typesAndPositions[SLOTS_PER_STRUCTURE * oldDepth + POSITION_OFFSET] = pos;
        newHash = (newHash + pos) * LongHashUtil.HASH_FACTOR1;
        this.hash = newHash;
        hashes[oldDepth + 1] = newHash;
        this.depth = oldDepth + 1;
    }

    public void leave() {
        int newDepth = this.depth - 1;
        hash = hashes[newDepth];
        this.depth = newDepth;
    }

    public int getStructureTypeId(int depth) {
        return typesAndPositions[depth * SLOTS_PER_STRUCTURE];
    }

    public int getLength(int depth) {
        return typesAndPositions[depth * SLOTS_PER_STRUCTURE + LENGTH_OFFSET];
    }

    public int getPosition(int depth) {
        return typesAndPositions[depth * SLOTS_PER_STRUCTURE + POSITION_OFFSET];
    }

    /**
     * The hash for the current position in the structure. By adding the hash and
     * the hash value of the object stored at this position, a good hash for the pair
     * of position and object can be generated.
     *
     * @return the hash
     */
    public long getPositionHash() {
        return hash;
    }
}
