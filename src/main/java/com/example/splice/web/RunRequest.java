package com.example.splice.web;

import java.util.List;
import java.util.Map;

/** 一次裁决运行的入参。amount 接受整数或 "a/b" 精确分数。 */
public record RunRequest(String strand,
                         List<String> excludeEdges,
                         Double minMappability,
                         Map<String, String> locks,
                         String note) {
}
