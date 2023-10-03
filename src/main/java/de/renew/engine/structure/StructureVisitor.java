package de.renew.engine.structure;

public interface StructureVisitor {
    /**
     * Visit a nested element that might be deeply nested in a hierarchy of structs.
     * If the cursor indicates a depth of 0, the given element is the top-level object.
     * Otherwise, the cursor indicates the position in the hierarchy of struct at which
     * the element is located. The element may itself be a struct. The given hash
     * is the value that {@link StructureGuide#longHash(StructureTypes, Object)} would
     * return when passed the same structure types that were used when updating the
     * structure cursor.
     *
     * @param cursor  the struct cursor
     * @param element the element
     * @param hash    the long hash
     */
    void visit(StructureCursor cursor, Object element, long hash);
}
