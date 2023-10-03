package de.renew.util;

public final class UnsynchronizedIntStack {
    private static final int CURRENT_EMPTY = -1;

    private int[] stack = new int[8];
    // This is the size minus 1.
    private int current = -1;

    public boolean isEmpty() {
        return current == -1;
    }

    public int getLast() {
        return stack[current];
    }

    public void add(int element) {
        current++;
        if (current >= stack.length) {
            int[] newStack = new int[stack.length * 2];
            System.arraycopy(stack, 0, newStack, 0, stack.length);
            stack = newStack;
        }
        stack[current] = element;
    }

    public void removeLast() {
        current--;
    }

    public void clear() {
        current = CURRENT_EMPTY;
    }
}
