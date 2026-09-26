package com.example.splice.splice;

import com.example.splice.domain.Models.Strand;

import java.util.List;
import java.util.Map;

/**
 * 供求解与展示使用的有向剪接图视图。节点按转录顺序 5'-&gt;3' 排列，
 * 边方向 = 供体外显子 -&gt; 受体外显子。坐标仍为基因组半开坐标。
 */
public record SpliceGraph(
        String geneId,
        String symbol,
        Strand effectiveStrand,
        boolean strandFlipped,
        String contig,
        int spanStart,
        int spanEnd,
        List<Node> nodes,
        List<Edge> edges,
        List<CandidatePath> paths,
        List<SampleView> samples,
        Map<String, List<Cell>> cellsByEdge) {

    public SpliceGraph {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        paths = List.copyOf(paths);
        samples = List.copyOf(samples);
        cellsByEdge = Map.copyOf(cellsByEdge);
    }

    public record Node(String exonId, int rank, String contig, int start, int end) {
        public int length() {
            return end - start;
        }
    }

    /** splice junction 边；genomicStart/genomicEnd 是升序半开内含子区间。 */
    public record Edge(String junctionId, String donorExonId, String acceptorExonId,
                       int donorRank, int acceptorRank,
                       String contig, int genomicStart, int genomicEnd,
                       double mappability) {
    }

    public record CandidatePath(String id, List<Integer> nodeRanks,
                                List<String> edgeIds, List<String> exonIds) {
        public CandidatePath {
            nodeRanks = List.copyOf(nodeRanks);
            edgeIds = List.copyOf(edgeIds);
            exonIds = List.copyOf(exonIds);
        }
    }

    public record SampleView(String id, String name, String conditionGroup) {
    }

    /**
     * 计数单元：measured=false 表示该样本未采集（count 必须为 null）；
     * measured=true 且 count=0 表示真实零计数。
     */
    public record Cell(String sampleId, String conditionGroup,
                       boolean measured, Integer count, String reason) {
    }

    public Edge edge(String junctionId) {
        return edges.stream().filter(e -> e.junctionId().equals(junctionId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no edge " + junctionId));
    }
}
