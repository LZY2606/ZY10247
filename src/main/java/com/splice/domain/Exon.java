package com.splice.domain;

/**
 * 基因组半开区间 [start,end) 上的 exon。ord 为 fixture 声明链上的转录顺序（5'->3'，从 1 开始）。
 */
public record Exon(int ord, String name, int start, int end) {
    public int length() {
        return end - start;
    }
}
