package de.renew.engine.net;

import java.util.Objects;
import java.util.function.Function;

/**
 * A weight that simply counts tokens without assigning timestamps.
 */
public class SimpleWeight implements Weight {
    private int weight;

    public static final SimpleWeight ZERO = new SimpleWeight(0);

    public static Function<SimpleWeight, SimpleWeight> updater(int delta) {
        return weight -> new SimpleWeight(weight.weight + delta);
    }

    public SimpleWeight(int weight) {
        this.weight = weight;
    }

    @Override
    public boolean isZero() {
        return weight == 0;
    }

    public int getWeight() {
        return weight;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimpleWeight that = (SimpleWeight) o;
        return weight == that.weight;
    }

    @Override
    public int hashCode() {
        return Objects.hash(weight);
    }
}
