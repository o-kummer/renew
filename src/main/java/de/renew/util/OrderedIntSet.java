package de.renew.util;

public class OrderedIntSet {
    private int size;
    private int[] values = new int[1];
    private boolean[] contains = new boolean[1];

    public boolean add(int value) {
        if (contains.length > value) {
            if (contains[value]) {
                return false;
            }
        } else {
            int newContainsLength = Math.max(value + 1, 2 * contains.length);
            boolean[] newContains = new boolean[newContainsLength];
            System.arraycopy(contains, 0 , newContains, 0 , contains.length);
            contains = newContains;
        }
        contains[value] = true;

        if (size == values.length) {
            int newValuesLength = 2 * values.length;
            int[] newValues = new int[newValuesLength];
            System.arraycopy(values, 0 , newValues, 0 , values.length);
            values = newValues;
        }
        values[size++] = value;
        return true;
    }

    public int size() {
        return size;
    }

    public boolean contains(int value) {
        return contains.length > value && contains[value];
    }

    public int get(int index) {
        return values[index];
    }

    public int pop() {
        if (size == 0) {
            throw new IllegalStateException("already empty");
        }
        int result = values[--size];
        contains[result] = false;
        return result;
    }

    public void clear() {
        for (int i = 0; i < size; i++) {
            contains[values[i]] = false;
        }
        size = 0;
    }
}
