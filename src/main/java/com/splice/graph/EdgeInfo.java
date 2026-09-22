package com.splice.graph;

import java.util.Map;

/**
 * splice junction 边。fromOrd/toOrd 为生效链转录顺序。
 * perSample 值：0 = 已观测计数为零；null = 未采集（无计数行或样本未采集）。
 */
public record EdgeInfo(String id, int fromOrd, int toOrd,
                       int intronGenStart, int intronGenEnd,
                       int intronTStart, int intronTEnd,
                       boolean lowMappability, boolean excluded,
                       int demand, Map<String, Integer> perSample, String note) {
}
