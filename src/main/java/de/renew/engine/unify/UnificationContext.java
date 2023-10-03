package de.renew.engine.unify;

import de.renew.engine.structure.StructureTypes;

import java.util.List;
import java.util.function.Function;

public interface UnificationContext {
    StructureTypes getStructureTypes();

    Snapshot snapshot();

    /**
     * If this unification context is reset to an earlier snapshot, call the given object.
     *
     * @param runnable the runnable to call
     */
    void onRollback(Runnable runnable);

    Variable variable();
    Variable structure(Class<?> clazz, Object... objects);
    Variable structure(Class<?> clazz, List<?> objects);
    Variable computation(Function<List<?>, ?> fun, Object... variables);
    Variable computation(Function<List<?>, ?> fun, List<?> variables);

    /**
     * Unify the given objects or variables, returning true, if the objects can be
     * unified. If false is returned, all changes to the unification context have
     * been undone.
     *
     * @param o1 the first object or variable
     * @param o2 the second object or variable
     *
     * @return true, if the arguments were unified
     */
    boolean unify(Object o1, Object o2);

    Computer getComputer();
}
