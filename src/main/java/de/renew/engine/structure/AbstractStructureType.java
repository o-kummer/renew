package de.renew.engine.structure;

public abstract class AbstractStructureType implements StructureType {
    private int id;

    @Override
    public void setId(int id) {
        this.id = id;
    }

    @Override
    public int getId() {
        return id;
    }
}
