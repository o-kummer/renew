package de.renew.engine.structure;

@SuppressWarnings("java:S2160")
public class TupleImpl1 extends AbstractTuple {
    private final Object element;

    TupleImpl1(Object element) {
        this.element = element;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public Object get(int pos) {
        if (pos != 0) {
            throw new IndexOutOfBoundsException();
        }
        return element;
    }
}
