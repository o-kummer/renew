package de.renew.engine.unify;

public interface OnBind {
    void bound(Variable variable, UnificationContext unificationContext);
}
