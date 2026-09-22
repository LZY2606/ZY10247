package com.splice.graph;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 剪接路径求解器。
 *
 * 候选：DAG 中由“参与重建的边”（未被排除）构成的所有 source->sink 简单路径。
 * 目标：最小化 sum_e |observed_e - reconstructed_e|（L1 重建误差）；
 *       误差相同则路径总拷贝数最少（稀疏分解）；再以确定性键序决胜。
 *
 * 当多个路径集产生完全相同的边流量（flowSignature 相同）时全部保留，
 * 不允许仅按字典序选一个并声称唯一——这是“非唯一分解”的核心。
 */
@Component
public class PathSolver {

    private static final int MAX_SOLUTIONS = 12;

    public List<Solution> solve(GraphView graph, List<List<Integer>> lockedNodeOrds) {
        List<EdgeInfo> edges = graph.edges();
        int edgeCount = edges.size();
        Map<String, Integer> edgeIndex = new LinkedHashMap<>();
        for (int i = 0; i < edgeCount; i++) {
            edgeIndex.put(edges.get(i).id(), i);
        }

        List<EdgeInfo> active = edges.stream().filter(e -> !e.excluded()).toList();
        List<CandidatePath> paths = enumeratePaths(graph, active);
        // 先校验锁定路径（可能使用了被排除边），再决定是否为空图
        for (List<Integer> nodes : lockedNodeOrds) {
            if (findPathByNodes(paths, nodes) < 0) {
                throw new IllegalArgumentException("锁定路径不存在或使用了被排除边：" + nodes);
            }
        }
        if (paths.isEmpty()) {
            return List.of(emptySolution(edges, graph));
        }

        int[][] matrix = new int[paths.size()][edgeCount];
        for (int p = 0; p < paths.size(); p++) {
            for (String eid : paths.get(p).edgeIds()) {
                matrix[p][edgeIndex.get(eid)] = 1;
            }
        }

        int[] demand = new int[edgeCount];
        for (int e = 0; e < edgeCount; e++) {
            demand[e] = edges.get(e).excluded() ? 0 : edges.get(e).demand();
        }
        int maxDemand = maxPositive(demand);

        // 锁定路径作为固定基数（各 1 份），已验证只用未排除边
        int[] fixed = new int[paths.size()];
        for (List<Integer> nodes : lockedNodeOrds) {
            fixed[findPathByNodes(paths, nodes)] += 1;
        }

        Search search = new Search(paths, matrix, demand, maxDemand, fixed);
        List<int[]> best = new ArrayList<>();
        best.add(search.greedyIncumbent());
        search.dfs(best, 0);
        return search.materialize(edges, graph);
    }

    // ---- 路径枚举 ----

    private List<CandidatePath> enumeratePaths(GraphView graph, List<EdgeInfo> active) {
        int n = graph.nodes().size();
        List<List<Integer>> adj = new ArrayList<>();
        Map<Long, String> edgeKey = new LinkedHashMap<>();
        for (int i = 0; i <= n; i++) {
            adj.add(new ArrayList<>());
        }
        for (EdgeInfo e : active) {
            adj.get(e.fromOrd()).add(e.toOrd());
            edgeKey.put(pairKey(e.fromOrd(), e.toOrd()), e.id());
        }
        adj.forEach(l -> l.sort(Integer::compareTo));
        List<CandidatePath> result = new ArrayList<>();
        dfsPaths(1, n, adj, edgeKey, new ArrayList<>(List.of(1)), new ArrayList<>(), result);
        return result;
    }

    private void dfsPaths(int node, int sink, List<List<Integer>> adj, Map<Long, String> edgeKey,
                          List<Integer> nodeStack, List<String> edgeStack, List<CandidatePath> out) {
        if (node == sink) {
            out.add(new CandidatePath(pathKey(nodeStack), new ArrayList<>(nodeStack),
                    new ArrayList<>(edgeStack)));
            return;
        }
        for (int next : adj.get(node)) {
            if (!nodeStack.contains(next)) {
                nodeStack.add(next);
                edgeStack.add(edgeKey.get(pairKey(node, next)));
                dfsPaths(next, sink, adj, edgeKey, nodeStack, edgeStack, out);
                edgeStack.remove(edgeStack.size() - 1);
                nodeStack.remove(nodeStack.size() - 1);
            }
        }
    }

    private long pairKey(int from, int to) {
        return ((long) from << 32) ^ to;
    }

    private String pathKey(List<Integer> nodes) {
        return nodes.stream().map(String::valueOf).reduce((a, b) -> a + "->" + b).orElse("");
    }

    private int findPathByNodes(List<CandidatePath> paths, List<Integer> nodes) {
        for (int i = 0; i < paths.size(); i++) {
            if (paths.get(i).nodeOrds().equals(nodes)) {
                return i;
            }
        }
        return -1;
    }

    private int maxPositive(int[] demand) {
        int m = 0;
        for (int v : demand) {
            m = Math.max(m, v);
        }
        return m;
    }

    private Solution emptySolution(List<EdgeInfo> edges, GraphView graph) {
        List<EdgeResidual> residuals = new ArrayList<>();
        int l1 = 0;
        for (EdgeInfo e : edges) {
            int err = e.excluded() ? e.demand() : e.demand();
            l1 += err;
            residuals.add(new EdgeResidual(e.id(), e.fromOrd(), e.toOrd(), e.demand(), 0, err,
                    e.excluded(), e.lowMappability(), e.perSample()));
        }
        PathAssignment assignment = new PathAssignment(Map.of());
        return new Solution(assignment, 0, l1, true, "", residuals,
                Map.of(), CoverageFactory.coverage(assignment, List.of(), edges, graph));
    }
}
