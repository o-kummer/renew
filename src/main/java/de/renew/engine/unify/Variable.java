package de.renew.engine.unify;

import de.renew.engine.structure.StructureVisitor;

public interface Variable {
    /**
     * Return true, if the value of this variable is known and can be retrieved
     * using {@link #getValue()}.
     *
     * @return true, if the value of this variable is known
     */
    boolean isBound();

    /**
     * Return true, if the value of this variable can be retrieved from
     * a {@link Computer}.
     */
    boolean isComputable();

    /**
     * Return the value of this variable, if it is {@link #isBound() bound}. Throw an
     * {@link IllegalStateException}, if the variable is not bound.
     *
     * @return the value of this variable
     * @throws IllegalStateException if the variable is not bound
     */
    Object getValue();

    /**
     * Guide the visitor to all bound substructures.
     *
     * @param visitor the visitor
     * @param passValue whether to pass a value to the visitor
     */
    void guide(StructureVisitor visitor, boolean passValue);

    /**
     * Execute the given callback once this variable becomes bound.
     * Execute the callback immediately, if the variable is already bound.
     *
     * @param callback the callback
     */
    void onBind(OnBind callback);
}
