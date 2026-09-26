package com.example.splice;

import com.example.splice.domain.Coordinates;
import com.example.splice.domain.Models.GenomicInterval;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoordinatesTest {

    @Test
    void halfOpenIntervalsMustBeAscending() {
        GenomicInterval iv = new GenomicInterval("chr1", 100, 200);
        assertEquals(100, iv.length());
        assertThrows(IllegalArgumentException.class,
                () -> new GenomicInterval("chr1", 200, 100));
    }

    @Test
    void mirrorPointReflectsWithinSpan() {
        // 跨度 [200,4900)：c' = 200 + 4900 - c
        assertEquals(4900, Coordinates.mirror(200, 200, 4900));
        assertEquals(200, Coordinates.mirror(4900, 200, 4900));
        assertEquals(2700, Coordinates.mirror(2400, 200, 4900));
    }

    @Test
    void mirrorIntervalStaysAscendingHalfOpenAndSameLength() {
        int[] m = Coordinates.mirrorInterval(2900, 3400, 200, 4900);
        assertEquals(1700, m[0]);
        assertEquals(2200, m[1]);
        assertEquals(500, m[1] - m[0]); // 长度不变
    }

    @Test
    void mirrorIsAnInvolution() {
        int[] m1 = Coordinates.mirrorInterval(700, 1200, 200, 4900);
        int[] m2 = Coordinates.mirrorInterval(m1[0], m1[1], 200, 4900);
        assertEquals(700, m2[0]);
        assertEquals(1200, m2[1]);
    }
}
