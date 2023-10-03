package de.renew.engine.structure;

public class ObjectArrayStructureTypeFactory implements StructureTypeFactory {
    @Override
    public boolean canHandle(Class<?> clazz) {
        return clazz.isArray() && !clazz.getComponentType().isPrimitive();
    }

    @Override
    public StructureType createStructureType(Class<?> clazz) {
        return new ObjectArrayStructureType(clazz.getComponentType());
    }
}
