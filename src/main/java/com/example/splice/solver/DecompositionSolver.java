package com.example.splice.solver;

import com.example.splice.splice.SpliceGraph;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 稀疏非负路径分解器。
 *
 * <p>在 DAG 候选路径上枚举最小支撑（support）路径集；对每个子集解
 * A_S x = observed（精确有理数，要求唯一且非负）。保留该最小支撑下
 * <b>所有</b>可行解 —— 不同路径集只要重建边流量完全相同即为并列分解，
 * 不用字典序挑一个并声称唯一。
 */
@Service
public class DecompositionSolver {

    public List<SolverModels.ScopeResult> solve(SpliceGraph graph,
                                                Set<String> excludedEdges,
                                                Map<String, Frac> lockMap) {
        List<SolverModels.ScopeResult> out = new ArrayList<>();
        Set<String> groups = new LinkedHashSet<>();
        for (SpliceGraph.SampleView s : graph.samples()) {
            groups.add(s.conditionGroup());
        }
        out.add(solveScope(graph, excludedEdges, lockMap, "POOLED", null));
        for (String g : groups) {
            out.add(solveScope(graph, excludedEdges, lockMap, g, g));
        }
        return out;
    }

    private SolverModels.ScopeResult solveScope(SpliceGraph graph,
                                                Set<String> excludedEdges,
                                                Map<String, Frac> lockMap,
                                                String scope, String group) {
        List<SpliceGraph.Edge> edges = graph.edges();
        List<SpliceGraph.CandidatePath> paths = graph.paths();
        int n = paths.size();

        // 1) 汇总观测边流量（null 单元=未采集，跳过；0 计入）
        Map<String, SolverModels.AggregateEdge> agg = new LinkedHashMap<>();
        for (SpliceGraph.Edge e : edges) {
            long sum = 0;
            int measured = 0;
            int total = 0;
            for (SpliceGraph.Cell c : graph.cellsByEdge().get(e.junctionId())) {
                if (group != null && !c.conditionGroup().equals(group)) {
                    continue;
                }
                total++;
                if (c.measured()) {
                    measured++;
                    sum += c.count();
                }
            }
            agg.put(e.junctionId(), new SolverModels.AggregateEdge(
                    e.junctionId(), measured == 0 ? null : sum, measured, total));
        }

        // 2) 参与方程的边：已观测（含 0）且未被排除
        List<SpliceGraph.Edge> activeEdges = new ArrayList<>();
        for (SpliceGraph.Edge e : edges) {
            SolverModels.AggregateEdge a = agg.get(e.junctionId());
            if (a.observed() != null && !excludedEdges.contains(e.junctionId())) {
                activeEdges.add(e);
            }
        }

        // 3) 路径-边关联矩阵 + 锁定量（按路径下标对齐）
        int m = activeEdges.size();
        Map<String, Integer> edgeRow = new HashMap<>();
        for (int i = 0; i < activeEdges.size(); i++) {
            edgeRow.put(activeEdges.get(i).junctionId(), i);
        }
        Frac[] lockedAmount = new Frac[n];
        for (int j = 0; j < n; j++) {
            lockedAmount[j] = lockMap.get(paths.get(j).id());
            if (lockedAmount[j] != null && lockedAmount[j].isNegative()) {
                throw new IllegalArgumentException(
                        "locked amount must be non-negative: " + paths.get(j).id());
            }
        }
        Frac[][] incidence = new Frac[m][n];
        Frac[] rhsAdjusted = new Frac[m];
        for (int i = 0; i < m; i++) {
            SpliceGraph.Edge e = activeEdges.get(i);
            rhsAdjusted[i] = Frac.of(agg.get(e.junctionId()).observed());
            for (int j = 0; j < n; j++) {
                incidence[i][j] = paths.get(j).edgeIds().contains(e.junctionId())
                        ? Frac.ONE : Frac.ZERO;
                if (incidence[i][j].isOne() && lockedAmount[j] != null) {
                    rhsAdjusted[i] = rhsAdjusted[i].subtract(lockedAmount[j]);
                }
            }
        }

        List<Integer> freeVars = new ArrayList<>();
        for (int j = 0; j < n; j++) {
            if (lockedAmount[j] == null) {
                freeVars.add(j);
            }
        }
        Frac[][] af = new Frac[m][freeVars.size()];
        for (int i = 0; i < m; i++) {
            for (int k = 0; k < freeVars.size(); k++) {
                af[i][k] = incidence[i][freeVars.get(k)];
            }
        }
        boolean anyNegativeRhs = false;
        for (Frac v : rhsAdjusted) {
            if (v.isNegative()) {
                anyNegativeRhs = true;
            }
        }

        // 4) 按支撑规模枚举最稀疏唯一非负解
        List<Frac[]> tiedSolutions = new ArrayList<>();
        if (!anyNegativeRhs) {
            enumerateSparsest(af, rhsAdjusted, freeVars, n, lockedAmount, tiedSolutions);
        }
        boolean infeasible = anyNegativeRhs || tiedSolutions.isEmpty();

        // 5) 每条边重建流量与残余（排除边仍给出预测，标记 excluded）
        Frac[] representative = infeasible ? null : tiedSolutions.get(0);
        List<SolverModels.EdgeReconstruction> edgeResults = new ArrayList<>();
        Frac totalObs = Frac.ZERO;
        Frac totalRes = Frac.ZERO;
        for (SpliceGraph.Edge e : edges) {
            SolverModels.AggregateEdge a = agg.get(e.junctionId());
            Frac recon = Frac.ZERO;
            if (!infeasible) {
                for (int j = 0; j < n; j++) {
                    if (paths.get(j).edgeIds().contains(e.junctionId())) {
                        recon = recon.add(representative[j]);
                    }
                }
            }
            boolean excluded = excludedEdges.contains(e.junctionId());
            Long obs = a.observed();
            Frac residual = obs == null ? Frac.ZERO : Frac.of(obs).subtract(recon);
            if (obs != null && !excluded) {
                totalObs = totalObs.add(Frac.of(obs));
                totalRes = totalRes.add(abs(residual));
            }
            edgeResults.add(new SolverModels.EdgeReconstruction(
                    e.junctionId(), obs, a.measuredCells(), a.totalCells(), excluded,
                    e.mappability(), recon.toExactString(), recon.toDouble(),
                    residual.toExactString(), Math.abs(residual.toDouble())));
        }
        Double coverage = totalObs.isZero() ? null
                : Frac.ONE.subtract(totalRes.divide(totalObs)).toDouble();

        List<SolverModels.SparseSolution> solutions = new ArrayList<>();
        if (!infeasible) {
            for (int s = 0; s < tiedSolutions.size(); s++) {
                Frac[] x = tiedSolutions.get(s);
                List<SolverModels.PathWeight> weights = new ArrayList<>();
                for (int j = 0; j < n; j++) {
                    if (!x[j].isZero()) {
                        weights.add(new SolverModels.PathWeight(
                                paths.get(j).id(), x[j].toExactString(), x[j].toDouble()));
                    }
                }
                solutions.add(new SolverModels.SparseSolution(
                        s, weights, lockedAmountAny(lockedAmount)));
            }
        }
        String fingerprint = infeasible ? null : edgeFingerprint(paths, edges, tiedSolutions.get(0));

        return new SolverModels.ScopeResult(scope, solutions, tiedSolutions.size() >= 2,
                fingerprint, edgeResults, coverage,
                totalRes.toExactString(), totalRes.toDouble(), infeasible);
    }

    private static boolean lockedAmountAny(Frac[] lockedAmount) {
        for (Frac v : lockedAmount) {
            if (v != null) {
                return true;
            }
        }
        return false;
    }

    private static void enumerateSparsest(Frac[][] a, Frac[] b, List<Integer> freeVars,
                                          int totalVars, Frac[] lockedAmount,
                                          List<Frac[]> sink) {
        int nf = freeVars.size();
        if (nf == 0) {
            for (Frac v : b) {
                if (!v.isZero()) {
                    return;
                }
            }
            sink.add(buildFull(new int[0], new Frac[0], freeVars, totalVars, lockedAmount));
            return;
        }
        for (int size = 1; size <= nf; size++) {
            for (int[] subset : combinations(nf, size)) {
                Frac[][] subA = new Frac[a.length][size];
                for (int i = 0; i < a.length; i++) {
                    for (int k = 0; k < size; k++) {
                        subA[i][k] = a[i][subset[k]];
                    }
                }
                Frac[] sol = RationalSystem.uniqueSolve(subA, b);
                if (sol == null) {
                    continue;
                }
                boolean nonNeg = true;
                for (Frac v : sol) {
                    if (v.isNegative()) {
                        nonNeg = false;
                        break;
                    }
                }
                if (!nonNeg) {
                    continue;
                }
                Frac[] full = buildFull(subset, sol, freeVars, totalVars, lockedAmount);
                if (!alreadySeen(sink, full)) {
                    sink.add(full);
                }
            }
            if (!sink.isEmpty()) {
                return; // 仅保留最小支撑层级的全部并列解
            }
        }
    }

    private static Frac[] buildFull(int[] subset, Frac[] sol, List<Integer> freeVars,
                                    int totalVars, Frac[] lockedAmount) {
        Frac[] full = new Frac[totalVars];
        for (int j = 0; j < totalVars; j++) {
            full[j] = lockedAmount[j] != null ? lockedAmount[j] : Frac.ZERO;
        }
        for (int k = 0; k < subset.length; k++) {
            full[freeVars.get(subset[k])] = sol[k];
        }
        return full;
    }

    private static boolean alreadySeen(List<Frac[]> sink, Frac[] candidate) {
        for (Frac[] x : sink) {
            boolean same = true;
            for (int j = 0; j < x.length; j++) {
                if (!x[j].equals(candidate[j])) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return true;
            }
        }
        return false;
    }

    private static Frac abs(Frac v) {
        return v.isNegative() ? v.negate() : v;
    }

    private String edgeFingerprint(List<SpliceGraph.CandidatePath> paths,
                                   List<SpliceGraph.Edge> edges, Frac[] x) {
        List<String> parts = new ArrayList<>();
        for (SpliceGraph.Edge e : edges) {
            Frac flow = Frac.ZERO;
            for (int j = 0; j < paths.size(); j++) {
                if (paths.get(j).edgeIds().contains(e.junctionId())) {
                    flow = flow.add(x[j]);
                }
            }
            parts.add(e.junctionId() + "=" + flow.toExactString());
        }
        return String.join(",", parts);
    }

    /** 生成 {0,..,n-1} 中取 k 的全部组合。 */
    static List<int[]> combinations(int n, int k) {
        List<int[]> result = new ArrayList<>();
        int[] combo = new int[k];
        for (int i = 0; i < k; i++) {
            combo[i] = i;
        }
        while (true) {
            result.add(combo.clone());
            int i = k - 1;
            while (i >= 0 && combo[i] == n - k + i) {
                i--;
            }
            if (i < 0) {
                break;
            }
            combo[i]++;
            for (int j = i + 1; j < k; j++) {
                combo[j] = combo[j - 1] + 1;
            }
        }
        return result;
    }
}
