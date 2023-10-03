package de.renew.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LongMathTest {
    @Test
    void testPow() {
        long expectedResult = 1;
        for (int i = 0; i < 512; i++) {
            assertEquals(expectedResult, LongMath.pow(31, i));
            expectedResult = expectedResult * 31;
        }
    }
}