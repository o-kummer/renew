package de.renew.engine.unify.impl;

import de.renew.engine.structure.StructureVisitor;
import de.renew.engine.unify.OnBind;
import de.renew.engine.unify.UnificationContext;
import de.renew.engine.unify.Variable;

public final class VariableImpl implements Variable {
    private final UnificationContextImpl unificationContext;
    private final int variableId;

    public VariableImpl(UnificationContextImpl unificationContext, int variableId) {
        this.unificationContext = unificationContext;
        this.variableId = variableId;
    }

    UnificationContext getUnificationContext() {
        return unificationContext;
    }

    int getVariableId() {
        return variableId;
    }

    @Override
    public boolean isBound() {
        return unificationContext.isBound(variableId);
    }

    @Override
    public boolean isComputable() {
        return unificationContext.isComputable(variableId);
    }

    @Override
    public Object getValue() {
        return unificationContext.getValue(variableId);
    }

    @Override
    public void guide(StructureVisitor visitor, boolean passValue) {
        unificationContext.guide(visitor, variableId, passValue);
    }

    @Override
    public void onBind(OnBind callback) {

    }
}
