package com.example.splice;

import com.example.splice.domain.Models.Gene;
import com.example.splice.domain.Models.Junction;
import com.example.splice.domain.Models.Strand;
import com.example.splice.fixture.FixtureData;
import com.example.splice.splice.GraphBuilder;
import com.example.splice.splice.SpliceGraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixtureGraphTest {

    private final Gene gene = FixtureData.gene();

    @Test
    void fixtureIsMinusStrandWithSerialDoubleDiamond() {
        assertEquals(Strand.MINUS, gene.strand());
        assertEquals(7, gene.exons().size());
        assertEquals(8, gene.junctions().size());
    }

    @Test
    void storedIntronIntervalsAreAscendingHalfOpen() {
        for (Junction j : gene.junctions()) {
            assertTrue(j.genomicStart() < j.genomicEnd(),
                    j.id() + " must store ascending intron interval");
            assertEquals(Strand.MINUS, j.strand());
        }
    }

    @Test
    void minusStrandDonorIsTheHighCoordinateEnd() {
        Junction j3 = gene.junctions().stream().filter(j -> j.id().equals("j3")).findFirst().orElseThrow();
        // j3 = E2->E4, 内含子 [2600,3600)；负链供体在高端 3600，受体在低端 2600
        assertEquals(3600, j3.donorBoundary());
        assertEquals(2600, j3.acceptorBoundary());
    }

    @Test
    void defaultGraphOrdersExonsByTranscriptionDirection() {
        SpliceGraph g = GraphBuilder.build(gene, gene.strand());
        assertFalse(g.strandFlipped());
        assertEquals(List.of("E1", "E2", "E3", "E4", "E5", "E6", "E7"),
                g.nodes().stream().map(SpliceGraph.Node::exonId).toList());
        // 四条全长候选转录本
        assertEquals(List.of("P1", "P2", "P3", "P4"),
                g.paths().stream().map(SpliceGraph.CandidatePath::id).toList());
    }

    @Test
    void flippingStrandMirrorsCoordinatesAndSwapsDonorAcceptor() {
        SpliceGraph g = GraphBuilder.build(gene, Strand.PLUS);
        assertTrue(g.strandFlipped());
        // 翻转后转录顺序变 E7..E1
        assertEquals(List.of("E7", "E6", "E5", "E4", "E3", "E2", "E1"),
                g.nodes().stream().map(SpliceGraph.Node::exonId).toList());
        // 原 E1 [4400,4900) 镜像 -> [200,700)，且变为最后一个转录节点（rank 7）
        SpliceGraph.Node e1 = g.nodes().stream()
                .filter(n -> n.exonId().equals("E1")).findFirst().orElseThrow();
        assertEquals(7, e1.rank());
        assertEquals(200, e1.start());
        assertEquals(700, e1.end());
        // j3 原 E2->E4（存储），翻转后方向应为 E4->E2，区间镜像 [1500,2500)
        SpliceGraph.Edge j3 = g.edges().stream()
                .filter(e -> e.junctionId().equals("j3")).findFirst().orElseThrow();
        assertEquals("E4", j3.donorExonId());
        assertEquals("E2", j3.acceptorExonId());
        assertEquals(1500, j3.genomicStart());
        assertEquals(2500, j3.genomicEnd());
        assertTrue(j3.donorRank() < j3.acceptorRank());
    }

    @Test
    void flippingIsNotArrayReversalCoordinatesAreConverted() {
        SpliceGraph g = GraphBuilder.build(gene, Strand.PLUS);
        // 纯倒序数组会让 E1 仍保持 [4400,4900)；正确实现必须已镜像
        SpliceGraph.Node e1 = g.nodes().stream()
                .filter(n -> n.exonId().equals("E1")).findFirst().orElseThrow();
        assertFalse(e1.start() == 4400 && e1.end() == 4900,
                "仅倒序数组而不转换坐标是错误的");
    }
}
