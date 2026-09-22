package com.splice.graph;

import java.util.List;

/** 一条从 source 到 sink 的简单路径，edges 按转录顺序排列。 */
public record CandidatePath(String key, List<Integer> nodeOrds, List<String> edgeIds) {
}
