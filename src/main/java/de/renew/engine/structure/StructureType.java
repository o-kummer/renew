package de.renew.engine.structure;

import java.util.List;

public interface StructureType {
    /**
     * Return whether this type can handle structure of the given class.
     *
     * @param clazz the class of the materialized structure
     * @return whether the class can be handled
     */
    boolean canHandle(Class<?> clazz);

    /**
     * Whether a structure with this length can be defined.
     * @param size the size
     * @return whether the length is acceptable
     */
    boolean checkLength(int size);

    /**
     * Return whether the given element is appropriate for the given element position.
     * @param pos the position
     * @param element the element
     * @return whether the element is appropriate
     */
    boolean checkElement(int pos, Object element);

    /**
     * Set the type id.
     * @param id the id
     */
    void setId(int id);

    /**
     * Return the type id.
     * @return the id
     */
    int getId();

    /**
     * Return the number of elements of the structure.
     *
     * @param structure the structure
     * @return the number of elements
     */
    int length(Object structure);

    /**
     * Return the element at the given position of the given structure.
     * @param structure the structure
     * @param pos the position
     * @return the element
     */
    Object get(Object structure, int pos);

    /**
     * Create a structure from the given elements.
     * @param elements the elements
     * @return the newly created structure
     */
    Object build(List<?> elements);
}
