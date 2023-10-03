package de.renew.engine.net.impl;

import java.util.concurrent.atomic.AtomicLong;

public enum SimpleIdSource implements IdSource {
    INSTANCE;

    private AtomicLong nextId = new AtomicLong();

    @Override
    public long newId() {
        return nextId.getAndIncrement();
    }
}
