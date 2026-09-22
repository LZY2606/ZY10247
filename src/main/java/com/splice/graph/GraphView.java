package com.splice.graph;

import com.splice.domain.Locus;
import com.splice.domain.SampleInfo;

import java.util.List;

public record GraphView(Locus locus, char declaredStrand, char effectiveStrand,
                        List<NodeInfo> nodes, List<EdgeInfo> edges, List<SampleInfo> samples) {
    public NodeInfo node(int ord) {
        return nodes.get(ord - 1);
    }

    public EdgeInfo edge(String id) {
        return edges.stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
    }
}
