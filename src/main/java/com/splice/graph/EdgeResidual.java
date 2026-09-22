package com.splice.graph;

import java.util.Map;

/**
 * 每条边的重建结果：observed=聚合观测（仅已采集样本），reconstructed=路径流之和，
 * error=|obs-rec|（排除边 rec=0，误差=obs），excluded=true 时表示该边未参与重建。
 */
public record EdgeResidual(String edgeId, int fromOrd, int toOrd, int observed, int reconstructed,
                           int error, boolean excluded, boolean lowMappability,
                           Map<String, Integer> perSample) {
}
