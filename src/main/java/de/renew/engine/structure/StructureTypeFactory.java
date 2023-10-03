package de.renew.engine.structure;

public interface StructureTypeFactory {
    /**
     * Return whether this type can handle structure of the given class.
     *
     * @param clazz the class of the materialized structure
     * @return whether the class can be handled
     */
    boolean canHandle(Class<?> clazz);

    /**
     * Create a structure type for the given class.
     * @param clazz the class of the materialized structure
     * @return a structure type
     */
    StructureType createStructureType(Class<?> clazz);
}
