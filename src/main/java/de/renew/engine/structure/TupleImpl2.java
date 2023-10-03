package de.renew.engine.structure;

@SuppressWarnings("java:S2160")
class TupleImpl2 extends AbstractTuple {
    private final Object element0;
    private final Object element1;

    TupleImpl2(Object element0, Object element1) {
        this.element0 = element0;
        this.element1 = element1;
    }

    @Override
    public int size() {
        return 2;
    }

    @Override
    public Object get(int pos) {
        return switch (pos) {
            case 0 -> element0;
            case 1 -> element1;
            default -> throw new IndexOutOfBoundsException();
        };
    }
}
