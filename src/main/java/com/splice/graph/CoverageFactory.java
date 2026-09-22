package com.splice.graph;

import com.splice.domain.SampleInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 覆盖度（min-edge 口径）：
 * 某样本被“完整解释”当且仅当存在一条被选用（拷贝数>0）的路径，
 * 其包含该样本全部正计数 junction 边（该路径上最弱的边也支持该样本）。
 */
final class CoverageFactory {
    private CoverageFactory() {
    }

    static Coverage coverage(PathAssignment assignment, List<CandidatePath> allPaths,
                             List<EdgeInfo> edges, GraphView graph) {
        Map<String, CandidatePath> byKey = new LinkedHashMap<>();
        allPaths.forEach(p -> byKey.put(p.key(), p));

        Map<String, Boolean> sampleExplained = new LinkedHashMap<>();
        for (SampleInfo s : graph.samples()) {
            if (!s.collected()) {
                continue;
            }
            List<String> positiveEdges = new java.util.ArrayList<>();
            for (EdgeInfo e : edges) {
                Integer v = e.perSample().get(s.id());
                if (v != null && v > 0) {
                    positiveEdges.add(e.id());
                }
            }
            if (positiveEdges.isEmpty()) {
                sampleExplained.put(s.id(), Boolean.TRUE);
                continue;
            }
            boolean explained = false;
            for (String pathKey : assignment.copies().keySet()) {
                CandidatePath p = byKey.get(pathKey);
                if (p != null && assignment.copies().get(pathKey) > 0
                        && coversAll(p, positiveEdges)) {
                    explained = true;
                    break;
                }
            }
            sampleExplained.put(s.id(), explained);
        }

        Map<String, double[]> acc = new LinkedHashMap<>();
        Map<String, Integer> denom = new LinkedHashMap<>();
        for (SampleInfo s : graph.samples()) {
            if (!s.collected()) {
                continue;
            }
            boolean hasPositive = false;
            for (EdgeInfo e : edges) {
                Integer v = e.perSample().get(s.id());
                if (v != null && v > 0) {
                    hasPositive = true;
                    break;
                }
            }
            if (!hasPositive) {
                continue;
            }
            double[] a = acc.computeIfAbsent(s.group(), k -> new double[1]);
            a[0] += sampleExplained.getOrDefault(s.id(), false) ? 1 : 0;
            denom.merge(s.group(), 1, Integer::sum);
        }
        Map<String, Double> groupCoverage = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> en : acc.entrySet()) {
            groupCoverage.put(en.getKey(), en.getValue()[0] / denom.get(en.getKey()));
        }
        return new Coverage(sampleExplained, groupCoverage);
    }

    /** 每条被选路径在每个条件组上的“可支持拷贝上限”：路径上最弱边的组聚合计数。 */
    static Map<String, Map<String, Integer>> pathGroupCopies(PathAssignment assignment,
                                                             List<CandidatePath> allPaths,
                                                             List<EdgeInfo> edges,
                                                             GraphView graph) {
        Map<String, EdgeInfo> edgeById = new LinkedHashMap<>();
        edges.forEach(e -> edgeById.put(e.id(), e));
        Map<String, String> sampleGroup = new LinkedHashMap<>();
        graph.samples().forEach(s -> sampleGroup.put(s.id(), s.group()));

        Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> en : assignment.copies().entrySet()) {
            if (en.getValue() <= 0) {
                continue;
            }
            CandidatePath p = allPaths.stream().filter(x -> x.key().equals(en.getKey())).findFirst()
                    .orElseThrow();
            Map<String, Integer> groupMin = new LinkedHashMap<>();
            for (SampleInfo s : graph.samples()) {
                if (!s.collected()) {
                    continue;
                }
                int min = Integer.MAX_VALUE;
                for (String eid : p.edgeIds()) {
                    Integer v = edgeById.get(eid).perSample().get(s.id());
                    min = Math.min(min, v == null ? 0 : v);
                }
                groupMin.merge(sampleGroup.get(s.id()), min, Math::min);
            }
            out.put(p.key(), groupMin);
        }
        return out;
    }

    private static boolean coversAll(CandidatePath p, List<String> positiveEdges) {
        return p.edgeIds().containsAll(positiveEdges);
    }
}
