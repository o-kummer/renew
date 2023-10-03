package de.renew.util;

public final class UnsynchronizedStack<T> {
    private Object[] stack = new Object[8];
    // This is the size minus 1.
    private int current = -1;

    public boolean isEmpty() {
        return current == -1;
    }

    public T getLast() {
        //noinspection unchecked
        return (T) stack[current];
    }

    public void add(T element) {
        current++;
        if (current >= stack.length) {
            Object[] newStack = new Object[stack.length * 2];
            System.arraycopy(stack, 0, newStack, 0, stack.length);
            stack = newStack;
        }
        stack[current] = element;
    }

    public void removeLast() {
        // Help the garbage collector.
        stack[current--] = null;
    }
}
