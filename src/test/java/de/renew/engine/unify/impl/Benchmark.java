package de.renew.engine.unify.impl;

import de.renew.engine.structure.Tuple;
import de.renew.engine.unify.Snapshot;
import de.renew.engine.unify.Variable;
import org.junit.jupiter.api.Assertions;

// For reference:
// Renew: 1.80µs
// attempt 1: 1.51µs
// 2024-07-30, full-featured: 0.63µs
// 2024-08-17, refactored: 0.68µs
public class Benchmark {

    public static final long WARMUP_TIME_MS = 2000L;
    public static final long MEASUREMENT_TIME_MS = 10000L;

    public static void main(String[] args) {
        UnificationContextImpl unificationContext = new UnificationContextImpl();
        long startWarmup = System.currentTimeMillis();
        while (System.currentTimeMillis() < startWarmup + WARMUP_TIME_MS) {
            benchmarkedCode(unificationContext);
        }
        long startMeasurement = System.currentTimeMillis();
        long counter = 0;
        do {
            benchmarkedCode(unificationContext);
            counter++;
        } while (System.currentTimeMillis() < startMeasurement + MEASUREMENT_TIME_MS);
        long endMeasurement = System.currentTimeMillis();
        System.out.println(((endMeasurement - startMeasurement) *  1000.0 / counter) + "µs");
    }

    private static void benchmarkedCode(UnificationContextImpl unificationContext) {
        Snapshot snapshot = unificationContext.snapshot();
        Variable v1 = unificationContext.variable();
        Variable v2 = unificationContext.variable();
        Variable v3 = unificationContext.variable();
        Variable structureVariable1 = unificationContext.structure(Tuple.class, v1);
        Variable structureVariable2 = unificationContext.structure(Tuple.class, structureVariable1, v2, "c");
        Variable structureVariable3 = unificationContext.structure(Tuple.class, v3, v2, "c");
        Assertions.assertFalse(v1.isBound());
        Assertions.assertFalse(structureVariable1.isBound());
        Assertions.assertFalse(structureVariable2.isBound());
        unificationContext.unify(structureVariable2, structureVariable3);
        unificationContext.unify(v2, "a");
        snapshot.restore();
    }
}
