package de.renew.engine.structure;

@SuppressWarnings("java:S2160")
class TupleImpl3 extends AbstractTuple {
    private final Object element0;
    private final Object element1;
    private final Object element2;

    TupleImpl3(Object element0, Object element1, Object element2) {
        this.element0 = element0;
        this.element1 = element1;
        this.element2 = element2;
    }

    @Override
    public int size() {
        return 3;
    }

    @Override
    public Object get(int pos) {
        return switch (pos) {
            case 0 -> element0;
            case 1 -> element1;
            case 2 -> element2;
            default -> throw new IndexOutOfBoundsException();
        };
    }
}
