package de.renew.engine.structure;

import java.util.List;

@SuppressWarnings("java:S6548")
public class TupleStructureType extends AbstractStructureType {
    static final TupleStructureType INSTANCE = new TupleStructureType();

    private TupleStructureType() {}

    @Override
    public boolean canHandle(Class<?> clazz) {
        return Tuple.class.isAssignableFrom(clazz);
    }

    @Override
    public boolean checkLength(int size) {
        return true;
    }

    @Override
    public boolean checkElement(int pos, Object element) {
        return true;
    }

    @Override
    public int length(Object structure) {
        return ((Tuple)structure).size();
    }

    @Override
    public Object get(Object structure, int pos) {
        return ((Tuple)structure).get(pos);
    }

    @Override
    public Tuple build(List<?> elements) {
        return Tuple.of(elements.toArray());
    }
}
