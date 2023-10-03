package de.renew.engine.structure;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StructureTypes {
    private final int maxSize;
    private final List<StructureTypeFactory> factories = new ArrayList<>();
    private final Map<Class<?>, StructureType> classToType = new ConcurrentHashMap<>();
    private final List<StructureType> types = new ArrayList<>();
    private final Set<StructureType> typeSet = new HashSet<>();

    public StructureTypes() {
        this(Integer.MAX_VALUE);
    }

    public StructureTypes(int maxSize) {
        this.maxSize = maxSize;
        registerFactory(new TupleStructureTypeFactory());
        registerFactory(new ListStructureTypeFactory());
        registerFactory(new ObjectArrayStructureTypeFactory());
        registerFactory(new RecordStructureTypeFactory());
    }

    public void registerFactory(StructureTypeFactory factory) {
        factories.add(factory);
    }

    /**
     * Return the structure type for an object with the given class or null,
     * if the clazz cannot be interpreted as a structure.
     *
     * @param clazz the class
     * @return the structure type or null, if not a structure class
     */
    public StructureType getType(Class<?> clazz) {
        return classToType.computeIfAbsent(clazz, this::findStructureType);
    }

    public StructureType getType(int id) {
        return types.get(id);
    }

    private StructureType findStructureType(Class<?> clazz) {
        for (StructureTypeFactory factory : factories) {
            if (factory.canHandle(clazz)) {
                StructureType structureType = factory.createStructureType(clazz);
                synchronized (this) {
                    if (typeSet.contains(structureType)) {
                        return structureType;
                    }
                    int id = types.size();
                    if (id == maxSize) {
                        throw new IllegalStateException("too many structure types");
                    }
                    structureType.setId(id);
                    types.add(structureType);
                    typeSet.add(structureType);
                }
                return structureType;
            }
        }
        return null;
    }

}
