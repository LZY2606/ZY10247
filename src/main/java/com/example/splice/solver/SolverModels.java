package com.example.splice.solver;

import java.util.List;

/** 求解器输出模型（精确流量以 exact 字符串保留，另附 double 便于展示）。 */
public final class SolverModels {
    private SolverModels() {
    }

    public record PathWeight(String pathId, String exact, double amount) {
    }

    /**
     * 单条边的重建结果。
     *
     * @param observed 汇总观测值；null 表示该 scope 下全部单元均未采集
     * @param measuredCells 参与汇总的已采集单元数（0 计为已采集）
     * @param excluded 是否被“低可比对性排除”
     */
    public record EdgeReconstruction(String junctionId,
                                     Long observed,
                                     Integer measuredCells,
                                     Integer totalCells,
                                     boolean excluded,
                                     double mappability,
                                     String reconstructedExact,
                                     double reconstructed,
                                     String residualExact,
                                     double residual) {
    }

    public record SparseSolution(int index, List<PathWeight> weights, boolean locked) {
    }

    /**
     * 一个聚合范围（POOLED 或某个条件分组）下的求解结果。
     *
     * @param tied true 表示存在 &gt;=2 组路径构成不同但边流量完全相同的稀疏分解
     */
    public record ScopeResult(String scope,
                              List<SparseSolution> solutions,
                              boolean tied,
                              String tieFingerprint,
                              List<EdgeReconstruction> edges,
                              Double coverage,
                              String totalResidualExact,
                              double totalResidual,
                              boolean infeasible) {
    }

    public record AggregateEdge(String junctionId, Long observed,
                                int measuredCells, int totalCells) {
    }
}
