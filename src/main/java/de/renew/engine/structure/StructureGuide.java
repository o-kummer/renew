package de.renew.engine.structure;

import java.util.Objects;

/**
 * A structure guide guides a visitor around an object that might be a nested structure.
 */
public class StructureGuide {
    private final StructureTypes structureTypes;
    private final StructureCursor cursor = new StructureCursor();

    public StructureGuide(StructureTypes structureTypes) {
        this.structureTypes = structureTypes;
    }

    public void guide(StructureVisitor visitor, Object object) {
        guide(structureTypes, cursor, visitor, object);
    }

    /**
     * Guide the visitor and return the object's long hash.
     *
     * @param structureTypes the types
     * @param cursor         the cursor
     * @param visitor        the visitor
     * @param object         the object to visit
     * @return the hash
     */
    public static long guide(StructureTypes structureTypes, StructureCursor cursor, StructureVisitor visitor, Object object) {
        StructureType type = null;
        if (object != null) {
            Class<?> clazz = object.getClass();
            type = structureTypes.getType(clazz);
        }
        long hash;
        if (type != null) {
            int typeId = type.getId();
            int length = type.length(object);

            hash = LongHashUtil.baseStructureHash(typeId, length);
            for (int pos = 0; pos < length; pos++) {
                cursor.enter(typeId, length, pos);
                try {
                    Object element = type.get(object, pos);
                    long elementHash = guide(structureTypes, cursor, visitor, element);
                    hash = LongHashUtil.mergeElementHash(hash, elementHash);
                } finally {
                    cursor.leave();
                }
            }
        } else {
            hash = Objects.hashCode(object);
        }
        if (visitor != null ) {
            visitor.visit(cursor, object, hash);
        }
        return hash;
    }

    /**
     * Return the object's long hash.
     *
     * @param structureTypes the types
     * @param object         the object to visit
     * @return the hash
     */
    public static long longHash(StructureTypes structureTypes, Object object) {
        StructureType type = null;
        if (object != null) {
            Class<?> clazz = object.getClass();
            type = structureTypes.getType(clazz);
        }
        long hash;
        if (type != null) {
            int typeId = type.getId();
            int length = type.length(object);

            hash = LongHashUtil.baseStructureHash(typeId, length);
            for (int pos = 0; pos < length; pos++) {
                Object element = type.get(object, pos);
                long elementHash = longHash(structureTypes, element);
                hash = LongHashUtil.mergeElementHash(hash, elementHash);
            }
        } else {
            hash = Objects.hashCode(object);
        }
        return hash;
    }
}
