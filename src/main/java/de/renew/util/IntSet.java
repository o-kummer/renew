package de.renew.util;

public class IntSet {
    private boolean[] contains = new boolean[1];

    public void set(int value) {
        if (contains.length <= value) {
            int newContainsLength = Math.max(value + 1, 2 * contains.length);
            boolean[] newContains = new boolean[newContainsLength];
            System.arraycopy(contains, 0 , newContains, 0 , contains.length);
            contains = newContains;
        }
        contains[value] = true;
    }

    public void clear(int value) {
        if (contains.length > value && contains[value]) {
            contains[value] = false;
        }
    }

    public boolean get(int value) {
        return contains.length > value && contains[value];
    }

    public void clear() {
        contains = new boolean[1];
    }
}
