package com.splice.graph;

import java.util.Map;

/**
 * 路径集对条件分组的覆盖。
 * sampleFullyExplained：该样本所有正计数边是否都被同一条候选路径整体包含（min-edge 口径）。
 * groupCoverage = 组内被完整解释的已采集且有正计数样本比例；组内无正计数样本时为 1.0。
 */
public record Coverage(Map<String, Boolean> sampleFullyExplained, Map<String, Double> groupCoverage) {
}
