package de.renew.engine.structure;

/**
 * Hash bases for exponentiation based bashing that have been chosen in a way that
 * each has a period of 2<sup>62</sup> and no low-exponent power (below 100_000_000)
 * of one factor is identical to a low-exponent power of another exponent.
 */
public class LongHashUtil {
    /**
     * A large factor for computing hashes based on repeated multiplications.
     * This factor has a period of 2<sup>62</sup>.
     */
    static final long HASH_FACTOR1 = 6315235862077790579L;

    /**
     * A large factor for computing hashes based on repeated multiplications.
     * This factor has a period of 2<sup>62</sup>.
     */
    static final long HASH_FACTOR2 = 3808136859810368475L;

    /**
     * A large factor for computing hashes based on repeated multiplications.
     * This factor has a period of 2<sup>62</sup>.
     */
    static final long HASH_FACTOR3 = 4861452787659711035L;

    /**
     * Compute the base hash for a structure with the given type id and the given length.
     * The resulting value must be processed by {@link #mergeElementHash(long, long)} for
     * each element of the structure.
     *
     * @param typeId the id of the structure type
     * @param length the structure length
     * @return the base hash
     */
    public static long baseStructureHash(int typeId, int length) {
        return length + typeId * HASH_FACTOR2;
    }

    /**
     * Merge the hash value of an element into the long hash of a structure.
     *
     * @param hash the hash for the structure up to, but excluding the current element
     * @param elementHash the hash for the element
     * @return the hash value for the structure up to and including the current element
     */
    public static long mergeElementHash(long hash, long elementHash) {
        return hash * HASH_FACTOR2 + elementHash;
    }
}
