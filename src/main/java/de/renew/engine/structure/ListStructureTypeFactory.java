package de.renew.engine.structure;

import java.util.List;

public class ListStructureTypeFactory implements StructureTypeFactory {
    @Override
    public boolean canHandle(Class<?> clazz) {
        return List.class.isAssignableFrom(clazz);
    }

    @Override
    public StructureType createStructureType(Class<?> clazz) {
        return ListStructureType.INSTANCE;
    }
}
