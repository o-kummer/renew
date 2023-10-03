package de.renew.engine.unify;

public interface Computer {
    /**
     * Compute a variable binding. This will execute computations, if any computation
     * were collected by the unification context creating the computer.
     *
     * @return a variable binding
     */
    Binding compute();
}
