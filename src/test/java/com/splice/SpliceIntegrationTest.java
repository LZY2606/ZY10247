package com.splice;

import com.splice.domain.Locus;
import com.splice.domain.SampleInfo;
import com.splice.graph.GraphBuilder;
import com.splice.graph.GraphView;
import com.splice.graph.PathSolver;
import com.splice.graph.Solution;
import com.splice.repo.FixtureLoader;
import com.splice.repo.SpliceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpliceIntegrationTest {

    @Autowired SpliceRepository repository;
    @Autowired FixtureLoader fixtureLoader;
    @Autowired GraphBuilder graphBuilder;
    @Autowired PathSolver pathSolver;
    @LocalServerPort int port;

    @BeforeEach
    void reseed() {
        repository.reseed(fixtureLoader.load());
    }

    private GraphView view(String locusId, char strand, Set<String> excluded) {
        Locus locus = repository.findLocus(locusId);
        List<SampleInfo> samples = repository.findSamples();
        return graphBuilder.build(locus, samples, repository.findCountMatrix(locusId),
                strand, excluded);
    }

    @Test
    void zeroCountIsStoredButUncollectedSampleHasNoCounts() {
        Map<String, Map<String, Integer>> m = repository.findCountMatrix("GENE-D");
        // 显式零：行存在且为 0
        assertEquals(0, m.get("j24").get("S1"));
        assertEquals(0, m.get("j24").get("S2"));
        assertEquals(0, m.get("j24").get("S3"));
        // 未采集样本 S4 不写任何计数键
        for (Map<String, Integer> perJunction : m.values()) {
            assertFalse(perJunction.containsKey("S4"));
        }
        SampleInfo s4 = repository.findSamples().stream().filter(s -> s.id().equals("S4"))
                .findFirst().orElseThrow();
        assertFalse(s4.collected());

        // 图层面：已采集无行 -> 0；未采集 -> null
        GraphView g = view("GENE-D", '+', Set.of());
        var j24 = g.edge("j24");
        assertEquals(0, j24.perSample().get("S3"));
        assertNull(j24.perSample().get("S4"));
    }

    @Test
    void diamondKeepsTwoEquivalentSparseDecompositions() {
        GraphView g = view("GENE-D", '+', Set.of());
        List<Solution> solutions = pathSolver.solve(g, List.of());

        assertEquals(2, solutions.size(), "必须保留两组并列分解，不能按字典序只选一个");
        for (Solution s : solutions) {
            assertEquals(0, s.l1Error());
            assertEquals(2, s.pathCount(), "两组都是 2 条转录本的稀疏解");
            assertTrue(s.optimal());
        }
        // 边流量签名相同 = 两组路径集重建出同样的边流量
        assertEquals(solutions.get(0).flowSignature(), solutions.get(1).flowSignature());

        Set<Set<String>> pathSets = new java.util.HashSet<>();
        solutions.forEach(s -> pathSets.add(s.assignment().copies().keySet()));
        assertTrue(pathSets.contains(Set.of("1->2->3->4->5", "1->3->5")));
        assertTrue(pathSets.contains(Set.of("1->3->4->5", "1->2->3->5")));

        // 每条边重建误差为 0
        solutions.get(0).residuals().forEach(r -> assertEquals(0, r.error()));

        // 分组覆盖：{B,D} 完整解释两个 control 样本；{C,E} 不能解释任一 control
        Solution bd = solutions.stream()
                .filter(s -> s.assignment().copies().containsKey("1->2->3->4->5")).findFirst().orElseThrow();
        Solution ce = solutions.stream()
                .filter(s -> s.assignment().copies().containsKey("1->3->4->5")).findFirst().orElseThrow();
        assertEquals(1.0, bd.coverage().groupCoverage().get("control"));
        assertEquals(0.0, ce.coverage().groupCoverage().get("control"));
    }

    @Test
    void reverseLocusGraphUsesMirroredCoordinates() {
        GraphView g = view("GENE-R", '-', Set.of());
        assertEquals('-', g.effectiveStrand());
        assertEquals(1, g.nodes().get(0).ord());
        assertEquals("ER1", g.nodes().get(0).name());
        assertEquals(100, g.nodes().get(0).tStart());
        assertEquals(200, g.nodes().get(0).tEnd());
        assertEquals(900, g.nodes().get(3).tStart());

        var k1 = g.edge("k1r");
        assertEquals(1, k1.fromOrd());
        assertEquals(2, k1.toOrd());
        // 反向链内含子端点必须交换
        assertEquals(5600, k1.intronGenStart());
        assertEquals(5800, k1.intronGenEnd());

        // 唯一简单路径；k1r/k3r 各 4 -> 4 份，但会穿过显式零边 k2r，零边残差 4
        List<Solution> solutions = pathSolver.solve(g, List.of());
        assertEquals(1, solutions.size());
        assertEquals(4, solutions.get(0).l1Error());
        assertEquals(4, solutions.get(0).pathCount());
        assertTrue(solutions.get(0).assignment().copies().containsKey("1->2->3->4"));
        var zeroEdge = solutions.get(0).residuals().stream()
                .filter(r -> r.edgeId().equals("k2r")).findFirst().orElseThrow();
        assertEquals(0, zeroEdge.observed());
        assertEquals(4, zeroEdge.reconstructed());
        assertEquals(4, zeroEdge.error());
    }

    @Test
    void strandCorrectionRebuildsEdgesAndCoordinates() {
        // 把反向位点“更正”为正链：ord 翻转、坐标不再镜像
        GraphView g = view("GENE-R", '+', Set.of());
        assertEquals('-', g.declaredStrand());
        assertEquals('+', g.effectiveStrand());
        // 声明 ord 4（ER4，基因组 5000-5100）变成转录 ord 1
        assertEquals(1, g.nodes().get(0).ord());
        assertEquals(4, g.nodes().get(0).originalOrd());
        assertEquals(0, g.nodes().get(0).tStart());
        var k1 = g.edge("k1r");
        // k1r 声明 1->2，翻转后方向为 2->1 的反向，即新边 3->4（由 ord 交换保证上游->下游）
        assertEquals(3, k1.fromOrd());
        assertEquals(4, k1.toOrd());
    }

    @Test
    void excludingLowMappabilityEdgeProducesResiduals() {
        GraphView g = view("GENE-R", '-', Set.of("k2r"));
        List<Solution> solutions = pathSolver.solve(g, List.of());
        // 排除 k2r 后无完整路径：k1r/k3r 正计数变残余(8)，k2r 已排除 rec=0 obs=0 误差 0
        assertEquals(1, solutions.size());
        assertEquals(8, solutions.get(0).l1Error());
        var k2 = solutions.get(0).residuals().stream().filter(r -> r.edgeId().equals("k2r"))
                .findFirst().orElseThrow();
        assertTrue(k2.excluded());
        assertEquals(0, k2.reconstructed());
        assertEquals(0, k2.error()); // 显式零且已排除：无误差
    }

    @Test
    void lockingPathPicksSpecificDecomposition() {
        GraphView g = view("GENE-D", '+', Set.of());
        // 锁定 C=1->3->4->5：会固定 C，另一份流量只能由 E 补齐
        List<Solution> solutions = pathSolver.solve(g, List.of(List.of(1, 3, 4, 5)));
        assertEquals(1, solutions.size());
        assertTrue(solutions.get(0).assignment().copies().containsKey("1->3->4->5"));
        assertTrue(solutions.get(0).assignment().copies().containsKey("1->2->3->5"));
        assertEquals(0, solutions.get(0).l1Error());
    }

    @Test
    void lockingExcludedPathIsRejected() {
        GraphView g = view("GENE-R", '-', Set.of("k2r"));
        assertThrows(IllegalArgumentException.class,
                () -> pathSolver.solve(g, List.of(List.of(1, 2, 3, 4))));
    }

    @Test
    void httpEndpointShowsTitleAndSolveRoundTrip() throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        String html = client.send(java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create("http://127.0.0.1:" + port + "/"))
                        .GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString()).body();
        assertTrue(html.contains("剪接路径裁决台"));

        String body = """
                {"locusId":"GENE-D","strand":null,"exclude":[],"lockPaths":[],"persist":true}
                """;
        java.net.http.HttpResponse<String> resp = client.send(
                java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create("http://127.0.0.1:" + port + "/api/solve"))
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("1->2->3->4->5"));
        long firstRun = repository.listRuns("GENE-D").get(0).get("id") instanceof Number n
                ? n.longValue() : -1L;
        assertTrue(firstRun > 0);

        // 第二个运行（排除边）后做差分
        String body2 = """
                {"locusId":"GENE-R","strand":"-","exclude":["k2r"],"lockPaths":[],"persist":true}
                """;
        java.net.http.HttpResponse<String> resp2 = client.send(
                java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create("http://127.0.0.1:" + port + "/api/solve"))
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body2)).build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp2.statusCode());
        long secondRun = ((Number) repository.listRuns("GENE-R").get(0).get("id")).longValue();

        java.net.http.HttpResponse<String> diff = client.send(
                java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create("http://127.0.0.1:" + port
                                + "/api/runs/" + firstRun + "/diff/" + secondRun))
                        .GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, diff.statusCode());
        assertTrue(diff.body().contains("\"excludedChanged\":true"));
    }

    @Test
    void clearAndReimportReproducesFixture() {
        javax.sql.DataSource ds = repository.dataSource();
        org.springframework.jdbc.core.JdbcTemplate jt = new org.springframework.jdbc.core.JdbcTemplate(ds);
        jt.update("DELETE FROM junction_count");
        jt.update("DELETE FROM junction");
        jt.update("DELETE FROM exon");
        jt.update("DELETE FROM sample");
        jt.update("DELETE FROM locus");
        assertEquals(0, repository.findLoci().size());

        // 与启动播种相同的重放路径
        repository.reseed(fixtureLoader.load());
        assertEquals(2, repository.findLoci().size());
        assertNotNull(repository.findLocus("GENE-D"));
        assertNotNull(repository.findLocus("GENE-R"));
        assertEquals(4, repository.findSamples().size());
        // 零计数与未采集语义依旧成立
        var matrix = repository.findCountMatrix("GENE-D");
        assertEquals(0, matrix.get("j24").get("S1"));
        for (var perJunction : matrix.values()) {
            assertFalse(perJunction.containsKey("S4"));
        }
    }
}
