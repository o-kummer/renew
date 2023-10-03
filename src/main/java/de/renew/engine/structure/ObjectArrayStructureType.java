package de.renew.engine.structure;

import java.lang.reflect.Array;
import java.util.List;

public class ObjectArrayStructureType extends AbstractStructureType {
    private final Class<?> componentType;

    public ObjectArrayStructureType(Class<?> componentType) {
        this.componentType = componentType;
    }

    @Override
    public boolean canHandle(Class<?> clazz) {
        return clazz.isArray() && clazz.getComponentType().equals(componentType);
    }

    @Override
    public boolean checkLength(int size) {
        return true;
    }

    @Override
    public boolean checkElement(int pos, Object element) {
        return element == null || componentType.isAssignableFrom(element.getClass());
    }

    @Override
    public int length(Object structure) {
        return ((Object[])structure).length;
    }

    @Override
    public Object get(Object structure, int pos) {
        return ((Object[])structure)[pos];
    }

    @Override
    public Object build(List<?> elements) {
        return elements.toArray((Object[]) Array.newInstance(componentType, elements.size()));
    }
}
