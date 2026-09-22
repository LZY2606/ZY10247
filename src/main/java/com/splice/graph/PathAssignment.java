package com.splice.graph;

import java.util.Map;

/** 一个路径集解：pathKey -> 拷贝数。 */
public record PathAssignment(Map<String, Integer> copies) {
    public int totalCopies() {
        return copies.values().stream().mapToInt(Integer::intValue).sum();
    }
}
