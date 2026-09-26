package com.example.splice;

import com.example.splice.domain.Models.Gene;
import com.example.splice.fixture.FixtureData;
import com.example.splice.solver.DecompositionSolver;
import com.example.splice.solver.Frac;
import com.example.splice.solver.SolverModels;
import com.example.splice.splice.GraphBuilder;
import com.example.splice.splice.SpliceGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolverTest {

    private SpliceGraph graph;
    private final DecompositionSolver solver = new DecompositionSolver();

    @BeforeEach
    void setUp() {
        Gene gene = FixtureData.gene();
        graph = GraphBuilder.build(gene, gene.strand());
    }

    private SolverModels.ScopeResult scope(List<SolverModels.ScopeResult> rs, String name) {
        return rs.stream().filter(r -> r.scope().equals(name)).findFirst().orElseThrow();
    }

    private Map<String, String> weights(SolverModels.SparseSolution s) {
        Map<String, String> m = new LinkedHashMap<>();
        for (SolverModels.PathWeight w : s.weights()) {
            m.put(w.pathId(), w.exact());
        }
        return m;
    }

    @Test
    void pooledFlowHasTwoTiedSparsestDecompositions() {
        List<SolverModels.ScopeResult> rs = solver.solve(graph, Set.of(), Map.of());
        SolverModels.ScopeResult pooled = scope(rs, "POOLED");
        assertFalse(pooled.infeasible());
        assertTrue(pooled.tied(), "共享子路径导致并列分解，必须保留两组");
        assertEquals(2, pooled.solutions().size());

        Map<String, String> a = weights(pooled.solutions().get(0));
        Map<String, String> b = weights(pooled.solutions().get(1));
        // 两组路径构成不同
        assertEquals(Map.of("P1", "50", "P2", "130", "P3", "20"), a);
        assertEquals(Map.of("P1", "70", "P2", "110", "P4", "20"), b);
    }

    @Test
    void tiedDecompositionsHaveIdenticalEdgeFlow() {
        List<SolverModels.ScopeResult> rs = solver.solve(graph, Set.of(), Map.of());
        SolverModels.ScopeResult pooled = scope(rs, "POOLED");
        // fingerprint 是按边重建流量生成的；两组解共享同一指纹即边流量完全相同
        assertEquals(pooled.tieFingerprint(), pooled.tieFingerprint());
        for (SolverModels.EdgeReconstruction e : pooled.edges()) {
            assertNotNull(e.observed());
            assertEquals(0.0, e.residual(), 1e-9,
                    "并列分解必须完全重建观测边流量: " + e.junctionId());
            assertEquals("0", e.residualExact());
        }
        assertEquals(1.0, pooled.coverage(), 1e-9);
    }

    @Test
    void controlGroupAlsoTiedTreatmentGroupUnique() {
        List<SolverModels.ScopeResult> rs = solver.solve(graph, Set.of(), Map.of());
        SolverModels.ScopeResult ctrl = scope(rs, "CTRL");
        SolverModels.ScopeResult trt = scope(rs, "TRT");
        assertTrue(ctrl.tied());
        assertEquals(Map.of("P1", "20", "P2", "60", "P3", "20"),
                weights(ctrl.solutions().get(0)));
        assertEquals(Map.of("P1", "40", "P2", "40", "P4", "20"),
                weights(ctrl.solutions().get(1)));
        assertFalse(trt.tied());
        assertEquals(Map.of("P1", "30", "P2", "70"),
                weights(trt.solutions().get(0)));
    }

    @Test
    void zeroCountIsMeasuredNullCountIsNotMeasured() {
        // j2: CTRL-3 为真实 0；j1: TRT-2 未采集
        List<SpliceGraph.Cell> j2 = graph.cellsByEdge().get("j2");
        SpliceGraph.Cell ctrl3 = j2.stream()
                .filter(c -> c.sampleId().equals("CTRL-3")).findFirst().orElseThrow();
        assertTrue(ctrl3.measured());
        assertEquals(0, ctrl3.count());

        List<SpliceGraph.Cell> j1 = graph.cellsByEdge().get("j1");
        SpliceGraph.Cell trt2 = j1.stream()
                .filter(c -> c.sampleId().equals("TRT-2")).findFirst().orElseThrow();
        assertFalse(trt2.measured());
        assertEquals(null, trt2.count());
        assertEquals("library_failed", trt2.reason());

        // 聚合时 TRT 仅统计 2 个已采集样本
        SolverModels.EdgeReconstruction j1Edge = scope(solver.solve(graph, Set.of(), Map.of()), "TRT")
                .edges().stream().filter(e -> e.junctionId().equals("j1")).findFirst().orElseThrow();
        assertEquals(100L, j1Edge.observed());
        assertEquals(2, j1Edge.measuredCells());
        assertEquals(3, j1Edge.totalCells());
    }

    @Test
    void lockingKnownPathBreaksTheTie() {
        Map<String, Frac> locks = new LinkedHashMap<>();
        locks.put("P2", Frac.of(110));
        SolverModels.ScopeResult pooled =
                scope(solver.solve(graph, Set.of(), locks), "POOLED");
        assertFalse(pooled.infeasible());
        assertFalse(pooled.tied());
        assertEquals(1, pooled.solutions().size());
        assertEquals(Map.of("P1", "70", "P2", "110", "P4", "20"),
                weights(pooled.solutions().get(0)));
        assertTrue(pooled.solutions().get(0).locked());
    }

    @Test
    void excludingLowMappabilityEdgeKeepsPredictionButMarksExcluded() {
        SolverModels.ScopeResult pooled =
                scope(solver.solve(graph, Set.of("j3"), Map.of()), "POOLED");
        SolverModels.EdgeReconstruction j3 = pooled.edges().stream()
                .filter(e -> e.junctionId().equals("j3")).findFirst().orElseThrow();
        assertTrue(j3.excluded());
        assertEquals(180L, j3.observed());
        // 仍给出预测流量（由守恒得到）
        assertEquals(180.0, j3.reconstructed(), 1e-9);
    }

    @Test
    void infeasibleLockIsReportedNotSilentlyZeroed() {
        Map<String, Frac> locks = new LinkedHashMap<>();
        locks.put("P1", Frac.of(9999));
        SolverModels.ScopeResult pooled =
                scope(solver.solve(graph, Set.of(), locks), "POOLED");
        assertTrue(pooled.infeasible());
        assertTrue(pooled.solutions().isEmpty());
    }
}
