package de.renew.engine.unify.impl;

import de.renew.engine.unify.Binding;
import de.renew.engine.unify.Variable;

public class BindingImpl implements Binding {
    private final Object[] values;

    public BindingImpl(Object[] values) {
        this.values = values;
    }

    @Override
    public Object getValue(Variable variable) {
        if (variable instanceof VariableImpl variableImpl) {
            return values[variableImpl.getVariableId()];
        } else {
            throw new IllegalArgumentException("Variable with wrong implementation detected");
        }
    }
}
