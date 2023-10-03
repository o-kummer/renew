package de.renew.engine.structure;

import java.lang.reflect.Modifier;

public class RecordStructureTypeFactory implements StructureTypeFactory {
    @Override
    public boolean canHandle(Class<?> clazz) {
        return clazz.isRecord() && Modifier.isPublic(clazz.getModifiers());
    }

    @Override
    public StructureType createStructureType(Class<?> clazz) {
        return new RecordStructureType(clazz);
    }
}
