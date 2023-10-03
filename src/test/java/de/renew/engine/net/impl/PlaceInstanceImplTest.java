package de.renew.engine.net.impl;

import de.renew.engine.net.SimpleWeight;
import de.renew.engine.structure.StructureTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class PlaceInstanceImplTest {
    private StructureTypes structureTypes;
    private PlaceInstanceImpl<SimpleWeight> testling;

    @BeforeEach
    void initTestling() {
        structureTypes = new StructureTypes();
        testling = new PlaceInstanceImpl<>(SimpleWeight.ZERO, SimpleIdSource.INSTANCE, structureTypes);
    }

    @Test
    void index() {
        assertEquals(new SimpleWeight(0), testling.get("a"));
        testling.update(SimpleWeight.updater(1), "a");
        assertEquals(new SimpleWeight(1), testling.get("a"));
        testling.update(SimpleWeight.updater(1), "a");
        assertEquals(new SimpleWeight(2), testling.get("a"));
        testling.update(SimpleWeight.updater(-1), "a");
        assertEquals(new SimpleWeight(1), testling.get("a"));
        testling.update(SimpleWeight.updater(-1), "a");
        assertEquals(new SimpleWeight(0), testling.get("a"));
    }
}