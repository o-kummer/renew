package de.renew.engine.structure;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("java:S6548")
public class ListStructureType extends AbstractStructureType {
    static final ListStructureType INSTANCE = new ListStructureType();

    private ListStructureType() {}

    @Override
    public boolean canHandle(Class<?> clazz) {
        return List.class.isAssignableFrom(clazz);
    }

    @Override
    public boolean checkLength(int size) {
        return size == 2;
    }

    @Override
    public boolean checkElement(int pos, Object element) {
        return pos == 0 || element instanceof List<?>;
    }

    @Override
    public int length(Object structure) {
        return ((List<?>)structure).size();
    }

    @Override
    public Object get(Object structure, int pos) {
        List<?> list = (List<?>) structure;
        if (pos == 0) {
            return list.get(0);
        } else {
            // TODO inefficient
            return list.subList(1, list.size());
        }
    }

    @Override
    public List<?> build(List<?> elements) {
        // TODO inefficient
        ArrayList<Object> result = new ArrayList<>();
        result.add(elements.get(0));
        result.addAll((List<?>)elements.get(1));
        return result;

    }
}
