package de.renew.engine.structure;

@SuppressWarnings("java:S2160")
class TupleImpl extends AbstractTuple {
    private final Object[] elements;

    TupleImpl(Object[] elements) {
        this.elements = elements;
    }

    @Override
    public int size() {
        return elements.length;
    }

    @Override
    public Object get(int pos) {
        if (pos < 0 || pos >= elements.length) {
            throw new IndexOutOfBoundsException();
        }
        return elements[pos];
    }
}
