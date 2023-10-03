package de.renew.util;

public class LongMath {
    public static long pow(long base, long exp) {
        if (exp < 0) {
            throw new IllegalArgumentException("exp must be positive, but is " + exp);
        }
        long result = 1;
        while (exp > 0) {
            if (exp % 2 == 1) {
                result = result * base;
            }
            exp = exp / 2;
            base = base * base;
        }
        return result;
    }
}
