package com.splice.graph;

import com.splice.domain.Coordinates;
import com.splice.domain.Exon;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CoordinatesTest {

    @Test
    void halfOpenIntervalsPreserveLength() {
        assertArrayEquals(new int[]{0, 100},
                Coordinates.toTranscript('+', 1000, 1900, 1000, 1100));
        assertEquals(100, Coordinates.toTranscript('+', 1000, 1900, 1000, 1100)[1]
                - Coordinates.toTranscript('+', 1000, 1900, 1000, 1100)[0]);
    }

    @Test
    void reverseStrandMirrorsCoordinatesNotArrayReversal() {
        // 区域 [5000,6000)，反向链 5' 端 exon 在基因组高坐标
        // 5' 端 ER1 在基因组高坐标，镜像后转录坐标小（区域起始有 100nt 间隙）
        assertArrayEquals(new int[]{100, 200},
                Coordinates.toTranscript('-', 5000, 6000, 5800, 5900));
        assertArrayEquals(new int[]{900, 1000},
                Coordinates.toTranscript('-', 5000, 6000, 5000, 5100));
        // 长度保持
        int[] t = Coordinates.toTranscript('-', 5000, 6000, 5200, 5300);
        assertEquals(100, t[1] - t[0]);
        assertEquals(700, t[0]);
        assertEquals(800, t[1]);
    }

    @Test
    void reverseIntronEndpointsMustSwap() {
        // 转录顺序 u（5'，基因组高坐标）-> v（3'，低坐标）
        Exon u = new Exon(1, "ER1", 5800, 5900);
        Exon v = new Exon(2, "ER2", 5500, 5600);
        int[] intron = Coordinates.intronGenomic('-', u, v);
        // 内含子半开区间为 [v.end, u.start) = [5600,5800)，而非 [5900,5500)
        assertArrayEquals(new int[]{5600, 5800}, intron);

        Exon fu = new Exon(1, "E1", 1000, 1100);
        Exon fv = new Exon(2, "E2", 1200, 1300);
        assertArrayEquals(new int[]{1100, 1200}, Coordinates.intronGenomic('+', fu, fv));
    }
}
