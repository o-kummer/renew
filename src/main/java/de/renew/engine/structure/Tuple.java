package de.renew.engine.structure;

public interface Tuple {
    int size();

    Object get(int pos);

    static Tuple of(Object ... elements) {
        return switch (elements.length) {
            case 0 -> new TupleImpl0();
            case 1 -> new TupleImpl1(elements[0]);
            case 2 -> new TupleImpl2(elements[0], elements[1]);
            case 3 -> new TupleImpl3(elements[0], elements[1], elements[2]);
            default -> new TupleImpl(elements);
        };
    }
}
