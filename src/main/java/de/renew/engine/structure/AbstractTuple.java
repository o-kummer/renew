package de.renew.engine.structure;

import java.util.Objects;

abstract class AbstractTuple implements Tuple {
    @Override
    public int hashCode() {
        int result = 734596783;
        for (int i = 0; i < size(); i++) {
            Object object = get(i);
            int objectHashCode = object == null ? 0 : object.hashCode();
            result = result * 31 + objectHashCode;
        }
        return result;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Tuple that)) {
            return false;
        }
        if (size() != that.size()) {
            return false;
        }
        for (int i = 0; i < size(); i++) {
            if (!Objects.equals(get(i), that.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("(");
        for (int i = 0; i < size(); i++) {
            if (i > 0) {
                builder.append(" ,");
            }
            builder.append(get(i));
        }
        builder.append(')');
        return builder.toString();
    }
}
