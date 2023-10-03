package de.renew.engine.structure;

import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

public class RecordStructureType extends AbstractStructureType {
    private final Class<?> clazz;
    private final Class<?>[] types;
    private final Method[] accessors;
    private final Constructor<?> canonicalConstructor;

    public RecordStructureType(Class<?> clazz) {
        this.clazz = clazz;
        RecordComponent[] recordComponents = clazz.getRecordComponents();
        types = Arrays.stream(recordComponents)
                .map(RecordComponent::getType)
                .toArray(Class<?>[]::new);
        accessors = Arrays.stream(recordComponents)
                .map(RecordComponent::getAccessor)
                .toArray(Method[]::new);
        try {
            canonicalConstructor = clazz.getDeclaredConstructor(types);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("record does not define canonical constructor, strange", e);
        }
    }

    @Override
    public boolean canHandle(Class<?> clazz) {
        return clazz.equals(this.clazz);
    }

    @Override
    public boolean checkLength(int size) {
        return size == types.length;
    }

    @Override
    public boolean checkElement(int pos, Object element) {
        Class<?> type = types[pos];
        if (type.isPrimitive()) {
            if (element == null) {
                return false;
            }
            type = MethodType.methodType(type).wrap().returnType();
        } else {
            if (element == null) {
                return true;
            }
        }
        return type.isAssignableFrom(element.getClass());
    }

    @Override
    public int length(Object structure) {
        return types.length;
    }

    @Override
    public Object get(Object structure, int pos) {
        try {
            return accessors[pos].invoke(structure);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Object build(List<?> elements) {
        try {
            return canonicalConstructor.newInstance(elements.toArray(Object[]::new));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("could not instantiate record", e);
        }
    }
}
