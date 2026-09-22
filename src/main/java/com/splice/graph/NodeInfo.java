package com.splice.graph;

/**
 * 图上的 exon 节点。ord 为生效链上的转录顺序（1 = 5' 端）；originalOrd 为 fixture 声明顺序。
 * tStart/tEnd 为生效链上的半开转录坐标；gen* 为基因组半开坐标。
 */
public record NodeInfo(int ord, int originalOrd, String name,
                       int genStart, int genEnd, int tStart, int tEnd) {
    public int length() {
        return genEnd - genStart;
    }
}
