package de.renew.engine.structure;

@SuppressWarnings("java:S2160")
class TupleImpl0 extends AbstractTuple {
    @Override
    public int size() {
        return 0;
    }

    @Override
    public Object get(int pos) {
        throw new IndexOutOfBoundsException();
    }
}
