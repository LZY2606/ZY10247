package com.splice.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分支定界搜索：路径拷贝数为非负整数。
 * 评分键 (l1Error, totalCopies, 字典序路径拷贝向量) 逐级比较；
 * 收集全部全局最优且互不相同的路径集（边流量相同也视为不同分解）。
 */
final class Search {
    private static final int MAX_TIED = 64;

    private final List<CandidatePath> paths;
    private final int[][] matrix;
    private final int[] demand;
    private final int maxDemand;
    private final int[] fixed;
    private final int edgeCount;
    private final int pathCount;

    private int[] bestScore;
    private final List<int[]> best = new ArrayList<>();

    Search(List<CandidatePath> paths, int[][] matrix, int[] demand, int maxDemand, int[] fixed) {
        this.paths = paths;
        this.matrix = matrix;
        this.demand = demand;
        this.maxDemand = maxDemand;
        this.fixed = fixed;
        this.edgeCount = demand.length;
        this.pathCount = paths.size();
    }

    int[] greedyIncumbent() {
        int[] x = fixed.clone();
        int[] rec = reconstruct(x);
        // 反复选择覆盖未满足正需求最多的路径
        while (true) {
            int bestP = -1;
            int bestGain = 0;
            for (int p = 0; p < pathCount; p++) {
                int gain = 0;
                for (int e = 0; e < edgeCount; e++) {
                    if (matrix[p][e] == 1 && rec[e] < demand[e]) {
                        gain++;
                    }
                }
                if (gain > bestGain) {
                    bestGain = gain;
                    bestP = p;
                }
            }
            if (bestP < 0) {
                break;
            }
            x[bestP]++;
            for (int e = 0; e < edgeCount; e++) {
                rec[e] += matrix[bestP][e];
            }
        }
        return x;
    }

    void dfs(List<int[]> initialBest, int unused) {
        best.clear();
        int[] incumbent = initialBest.get(0);
        best.add(incumbent.clone());
        bestScore = score(incumbent);

        int[] x = fixed.clone();
        int[] rec = reconstruct(x);
        search(0, x, rec);
    }

    private void search(int idx, int[] x, int[] rec) {
        if (idx == pathCount) {
            evaluate(x);
            return;
        }
        int upper = 0;
        for (int e = 0; e < edgeCount; e++) {
            if (matrix[idx][e] == 1) {
                upper = Math.max(upper, demand[e] - rec[e]);
            }
        }
        upper = Math.max(0, Math.min(upper, maxDemand));

        // 有效下界：超量部分（rec>demand）在非负流下不可逆，必然计入最终误差；
        // 欠量部分未来加路径可能补平，下界贡献 0。
        int l1Lower = 0;
        for (int e = 0; e < edgeCount; e++) {
            l1Lower += Math.max(0, rec[e] - demand[e]);
        }
        if (l1Lower > bestScore[0]) {
            return;
        }

        for (int k = 0; k <= upper; k++) {
            if (k > 0) {
                x[idx] += 1;
                for (int e = 0; e < edgeCount; e++) {
                    rec[e] += matrix[idx][e];
                }
            }
            search(idx + 1, x, rec);
        }
        // 回溯：循环共加入 upper 份
        x[idx] -= upper;
        for (int e = 0; e < edgeCount; e++) {
            rec[e] -= upper * matrix[idx][e];
        }
    }

    private void evaluate(int[] x) {
        int[] sc = score(x);
        int cmp = compareScore(sc, bestScore);
        if (cmp < 0) {
            bestScore = sc;
            best.clear();
            best.add(x.clone());
        } else if (cmp == 0 && best.size() < MAX_TIED) {
            for (int[] existing : best) {
                if (java.util.Arrays.equals(existing, x)) {
                    return;
                }
            }
            best.add(x.clone());
        }
    }

    private int[] reconstruct(int[] x) {
        int[] rec = new int[edgeCount];
        for (int p = 0; p < pathCount; p++) {
            for (int e = 0; e < edgeCount; e++) {
                rec[e] += matrix[p][e] * x[p];
            }
        }
        return rec;
    }

    private int[] score(int[] x) {
        int l1 = 0;
        int copies = 0;
        for (int p = 0; p < pathCount; p++) {
            copies += x[p];
        }
        for (int e = 0; e < edgeCount; e++) {
            int rec = 0;
            for (int p = 0; p < pathCount; p++) {
                rec += matrix[p][e] * x[p];
            }
            l1 += Math.abs(demand[e] - rec);
        }
        return new int[]{l1, copies};
    }

    private int compareScore(int[] a, int[] b) {
        if (a[0] != b[0]) {
            return Integer.compare(a[0], b[0]);
        }
        if (a[1] != b[1]) {
            return Integer.compare(a[1], b[1]);
        }
        return 0;
    }

    List<Solution> materialize(List<EdgeInfo> edges, GraphView graph) {
        best.sort((a, b) -> {
            int c = compareScore(score(a), score(b));
            if (c != 0) {
                return c;
            }
            for (int i = 0; i < a.length; i++) {
                int d = Integer.compare(a[i], b[i]);
                if (d != 0) {
                    return d;
                }
            }
            return 0;
        });

        List<Solution> out = new ArrayList<>();
        for (int[] x : best) {
            Map<String, Integer> copies = new LinkedHashMap<>();
            int totalCopies = 0;
            for (int p = 0; p < pathCount; p++) {
                if (x[p] > 0) {
                    copies.put(paths.get(p).key(), x[p]);
                    totalCopies += x[p];
                }
            }
            PathAssignment assignment = new PathAssignment(copies);
            int[] rec = reconstruct(x);
            List<EdgeResidual> residuals = new ArrayList<>();
            StringBuilder sig = new StringBuilder();
            int l1 = 0;
            for (int e = 0; e < edgeCount; e++) {
                EdgeInfo edge = edges.get(e);
                int observed = edge.demand();
                int reconstructed = edge.excluded() ? 0 : rec[e];
                int error = Math.abs(observed - reconstructed);
                l1 += error;
                residuals.add(new EdgeResidual(edge.id(), edge.fromOrd(), edge.toOrd(), observed,
                        reconstructed, error, edge.excluded(), edge.lowMappability(), edge.perSample()));
                if (!sig.isEmpty()) {
                    sig.append('-');
                }
                sig.append(edge.excluded() ? 'x' : (char) ('0' + Math.min(9, rec[e])));
            }
            Coverage coverage = CoverageFactory.coverage(assignment, paths, edges, graph);
            Map<String, Map<String, Integer>> groupCopies =
                    CoverageFactory.pathGroupCopies(assignment, paths, edges, graph);
            Map<String, Integer> flat = new LinkedHashMap<>();
            groupCopies.forEach((pathKey, groups) ->
                    groups.forEach((g, v) -> flat.put(pathKey + "@" + g, v)));
            out.add(new Solution(assignment, totalCopies, l1, true, sig.toString(), residuals,
                    flat, coverage));
        }
        return out;
    }
}
