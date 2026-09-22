package com.splice.graph;

import java.util.List;
import java.util.Map;

/**
 * 一个稀疏路径集解。optimal=true 表示 L1 误差与稀疏度均达到全局最优。
 * flowSignature 为每条边重建流量（按边顺序，'-' 连接），相同签名的解共享同一组边流量，
 * 属于不可唯一分解的并列路径集。
 */
public record Solution(PathAssignment assignment, int pathCount, int l1Error, boolean optimal,
                       String flowSignature, List<EdgeResidual> residuals,
                       Map<String, Integer> pathGroupCopies, Coverage coverage) {
}
