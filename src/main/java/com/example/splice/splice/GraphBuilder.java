package com.example.splice.splice;

import com.example.splice.domain.Coordinates;
import com.example.splice.domain.Models.Exon;
import com.example.splice.domain.Models.Gene;
import com.example.splice.domain.Models.Junction;
import com.example.splice.domain.Models.JunctionCount;
import com.example.splice.domain.Models.Sample;
import com.example.splice.domain.Models.Strand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 {@link Gene} fixture 构建有向剪接图，并支持链方向更正。
 *
 * <p>链翻转时：exon/内含子区间做跨度内镜像，且供体/受体转录语义互换；
 * 不会仅做数组倒序。
 */
public final class GraphBuilder {
    private GraphBuilder() {
    }

    public static SpliceGraph build(Gene gene, Strand strandOverride) {
        Strand effective = strandOverride != null ? strandOverride : gene.strand();
        boolean flipped = effective != gene.strand();
        int n = gene.exons().size();

        List<SpliceGraph.Node> nodes = new ArrayList<>();
        for (Exon e : gene.exons()) {
            int rank = flipped ? (n + 1 - e.rank()) : e.rank();
            int start = e.start();
            int end = e.end();
            if (flipped) {
                int[] m = Coordinates.mirrorInterval(start, end, gene.spanStart(), gene.spanEnd());
                start = m[0];
                end = m[1];
            }
            nodes.add(new SpliceGraph.Node(e.id(), rank, e.contig(), start, end));
        }
        nodes.sort(Comparator.comparingInt(SpliceGraph.Node::rank));

        List<SpliceGraph.Edge> edges = new ArrayList<>();
        for (Junction j : gene.junctions()) {
            String donorId = j.donorExonId();
            String acceptorId = j.acceptorExonId();
            int gStart = j.genomicStart();
            int gEnd = j.genomicEnd();            if (flipped) {
                // 镜像后转录供体/受体互换
                String tmpId = donorId;
                donorId = acceptorId;
                acceptorId = tmpId;
                int[] m = Coordinates.mirrorInterval(j.genomicStart(), j.genomicEnd(),
                        gene.spanStart(), gene.spanEnd());
                gStart = m[0];
                gEnd = m[1];
            }
            int donorRank = rankOf(donorId, nodes);
            int acceptorRank = rankOf(acceptorId, nodes);
            edges.add(new SpliceGraph.Edge(j.id(), donorId, acceptorId,
                    donorRank, acceptorRank, j.contig(), gStart, gEnd, j.mappability()));
        }
        edges.sort(Comparator.comparingInt(SpliceGraph.Edge::donorRank)
                .thenComparingInt(SpliceGraph.Edge::acceptorRank));

        List<SpliceGraph.CandidatePath> paths = enumeratePaths(nodes, edges);

        List<SpliceGraph.SampleView> samples = gene.samples().stream()
                .map(s -> new SpliceGraph.SampleView(s.id(), s.name(), s.conditionGroup()))
                .toList();

        Map<String, List<SpliceGraph.Cell>> cells = new LinkedHashMap<>();
        Map<String, JunctionCount> countByKey = new HashMap<>();
        for (JunctionCount c : gene.counts()) {
            countByKey.put(c.junctionId() + "|" + c.sampleId(), c);
        }
        for (Junction j : gene.junctions()) {
            List<SpliceGraph.Cell> row = new ArrayList<>();
            for (Sample s : gene.samples()) {
                JunctionCount c = countByKey.get(j.id() + "|" + s.id());
                if (c == null) {
                    // fixture 中没有行 = 该样本未采集该 junction（与 0 不同）
                    row.add(new SpliceGraph.Cell(s.id(), s.conditionGroup(),
                            false, null, "not_measured"));
                } else {
                    row.add(new SpliceGraph.Cell(s.id(), s.conditionGroup(),
                            c.measured(), c.count(), c.reason()));
                }
            }
            cells.put(j.id(), row);
        }

        return new SpliceGraph(gene.id(), gene.symbol(), effective, flipped,
                gene.contig(), gene.spanStart(), gene.spanEnd(),
                nodes, edges, paths, samples, cells);
    }

    private static int rankOf(String exonId, List<SpliceGraph.Node> nodes) {
        return nodes.stream().filter(nd -> nd.exonId().equals(exonId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("missing exon " + exonId))
                .rank();
    }

    /** DAG DFS：枚举从 rank=1 到 rank=n 的全部外显子级路径（受支持的候选转录本）。 */
    static List<SpliceGraph.CandidatePath> enumeratePaths(List<SpliceGraph.Node> nodes,
                                                          List<SpliceGraph.Edge> edges) {
        int firstRank = nodes.stream().mapToInt(SpliceGraph.Node::rank).min().orElseThrow();
        int lastRank = nodes.stream().mapToInt(SpliceGraph.Node::rank).max().orElseThrow();
        Map<Integer, String> exonByRank = new HashMap<>();
        for (SpliceGraph.Node nd : nodes) {
            exonByRank.put(nd.rank(), nd.exonId());
        }
        Map<Integer, List<SpliceGraph.Edge>> out = new LinkedHashMap<>();
        for (SpliceGraph.Edge e : edges) {
            out.computeIfAbsent(e.donorRank(), k -> new ArrayList<>()).add(e);
        }

        List<SpliceGraph.CandidatePath> result = new ArrayList<>();
        dfs(firstRank, lastRank, out, exonByRank,
                new ArrayList<>(List.of(firstRank)),
                new ArrayList<>(), new ArrayList<>(), result);
        return result;
    }

    private static void dfs(int rank, int lastRank,
                            Map<Integer, List<SpliceGraph.Edge>> out,
                            Map<Integer, String> exonByRank,
                            List<Integer> ranks, List<String> edgeIds, List<String> exonIds,
                            List<SpliceGraph.CandidatePath> sink) {
        if (rank == lastRank) {
            List<String> exons = new ArrayList<>(exonIds);
            exons.add(exonByRank.get(rank));
            String id = "P" + (sink.size() + 1);
            sink.add(new SpliceGraph.CandidatePath(id, List.copyOf(ranks),
                    List.copyOf(edgeIds), List.copyOf(exons)));
            return;
        }
        List<SpliceGraph.Edge> outs = out.getOrDefault(rank, List.of());
        for (SpliceGraph.Edge e : outs) {
            ranks.add(e.acceptorRank());
            edgeIds.add(e.junctionId());
            exonIds.add(exonByRank.get(rank));
            dfs(e.acceptorRank(), lastRank, out, exonByRank, ranks, edgeIds, exonIds, sink);
            ranks.remove(ranks.size() - 1);
            edgeIds.remove(edgeIds.size() - 1);
            exonIds.remove(exonIds.size() - 1);
        }
    }
}
