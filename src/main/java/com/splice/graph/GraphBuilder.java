package com.splice.graph;

import com.splice.domain.Coordinates;
import com.splice.domain.Exon;
import com.splice.domain.Junction;
import com.splice.domain.Locus;
import com.splice.domain.SampleInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从 locus 构建剪接图。
 *
 * 关键语义：
 * 1) 全部区间为半开 [start,end)。
 * 2) 反向链更正后必须做坐标镜像（见 {@link Coordinates#toTranscript}），
 *    且 junction 端点按生效链转录顺序交换，而不是仅倒序 exon 数组。
 * 3) junction 计数行 cnt=0 = 已观测的零；无行（或样本 collected=false）= 未采集。
 */
@Component
public class GraphBuilder {

    public GraphView build(Locus locus, List<SampleInfo> samples,
                           Map<String, Map<String, Integer>> countMatrix,
                           char effectiveStrand, Set<String> excludedIds) {
        List<Exon> sorted = new ArrayList<>(locus.exons());
        sorted.sort(Comparator.comparingInt(Exon::ord)); // 声明链转录顺序

        // 生效链上 exon 的重映射：声明 ord -> 生效 ord
        List<Exon> ordered = new ArrayList<>(sorted);
        if (effectiveStrand != locus.strand()) {
            // 声明顺序整体翻转：声明 ord=k 对应生效 ord=N+1-k
            ordered.sort(Comparator.comparingInt(Exon::ord).reversed());
        }

        List<NodeInfo> nodes = new ArrayList<>();
        for (int effectiveOrd = 1; effectiveOrd <= ordered.size(); effectiveOrd++) {
            Exon e = ordered.get(effectiveOrd - 1);
            int[] t = Coordinates.toTranscript(effectiveStrand, locus.regionStart(), locus.regionEnd(),
                    e.start(), e.end());
            nodes.add(new NodeInfo(effectiveOrd, e.ord(), e.name(), e.start(), e.end(), t[0], t[1]));
        }

        Map<Integer, Integer> declaredToEffective = new LinkedHashMap<>();
        for (NodeInfo n : nodes) {
            declaredToEffective.put(n.originalOrd(), n.ord());
        }

        List<EdgeInfo> edges = new ArrayList<>();
        for (Junction j : locus.junctions()) {
            int fromEffective = declaredToEffective.get(j.fromOrd());
            int toEffective = declaredToEffective.get(j.toOrd());
            // 任何链更正都保证边从转录上游指向下游
            int from = Math.min(fromEffective, toEffective);
            int to = Math.max(fromEffective, toEffective);

            NodeInfo u = nodes.get(from - 1);
            NodeInfo v = nodes.get(to - 1);
            Exon eu = locus.exon(u.originalOrd());
            Exon ev = locus.exon(v.originalOrd());
            int[] intronGen = Coordinates.intronGenomic(effectiveStrand, eu, ev);
            int[] intronT = Coordinates.toTranscript(effectiveStrand, locus.regionStart(), locus.regionEnd(),
                    intronGen[0], intronGen[1]);

            boolean excluded = excludedIds.contains(j.id());
            Map<String, Integer> perSample = buildPerSample(j.id(), samples, countMatrix);
            int demand = perSample.values().stream().mapToInt(v2 -> v2 == null ? 0 : v2).sum();

            edges.add(new EdgeInfo(j.id(), from, to, intronGen[0], intronGen[1], intronT[0], intronT[1],
                    j.lowMappability(), excluded, demand, perSample, j.note()));
        }
        edges.sort(Comparator.comparingInt(EdgeInfo::fromOrd).thenComparingInt(EdgeInfo::toOrd)
                .thenComparing(EdgeInfo::id));
        return new GraphView(locus, locus.strand(), effectiveStrand, nodes, edges, samples);
    }

    private Map<String, Integer> buildPerSample(String junctionId, List<SampleInfo> samples,
                                                Map<String, Map<String, Integer>> countMatrix) {
        Map<String, Integer> raw = countMatrix.getOrDefault(junctionId, Map.of());
        Map<String, Integer> perSample = new LinkedHashMap<>();
        for (SampleInfo s : samples) {
            if (!s.collected()) {
                perSample.put(s.id(), null); // 未采集样本：显式 null
            } else {
                perSample.put(s.id(), raw.getOrDefault(s.id(), 0)); // 有样本无行 = 已观测零
            }
        }
        return perSample;
    }
}
