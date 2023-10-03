package de.renew.engine.net;

import de.renew.engine.structure.StructureCursor;

import java.util.List;
import java.util.function.Function;

public interface PlaceInstance<W extends Weight> {
    /**
     * Get the current weight of the given token.
     *
     * @param token the token
     * @return the weight
     */
    W get(Object token);

    /**
     * Apply the given transformer to the weight of the given token.
     *
     * @param transformer the transformer
     * @param token       the weight
     */
    void update(Function<W, W> transformer, Object token);

    /**
     * Get a list of candidate tokens in this place instance that likely have
     * the given element with the given token at the position indicated by the cursor.
     * This method may return tokens that do not actually meet the requirement,
     * but it tries to do this rarely.
     *
     * @param cursor the cursor
     * @param element the element
     * @param elementHash the hash
     * @return a list of candidate tokens
     */
    List<Object> getCandidates(StructureCursor cursor, Object element, long elementHash);
}
