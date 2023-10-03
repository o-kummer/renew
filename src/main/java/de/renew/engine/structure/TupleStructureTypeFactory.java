package de.renew.engine.structure;

public class TupleStructureTypeFactory implements StructureTypeFactory {
    @Override
    public boolean canHandle(Class<?> clazz) {
        return Tuple.class.isAssignableFrom(clazz);
    }

    @Override
    public StructureType createStructureType(Class<?> clazz) {
        return TupleStructureType.INSTANCE;
    }
}
