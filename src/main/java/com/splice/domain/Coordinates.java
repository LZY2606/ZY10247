package com.splice.domain;

/**
 * 半开坐标语义与反向链转换。
 *
 * 所有基因组区间统一使用半开区间 [start,end)，长度为 end-start。
 * 反向链不能只把 exon 数组倒序：必须在坐标上做镜像映射，再表达转录顺序。
 *
 * 对区域 [R0,R1)，反向链的转录位置 t（区域起点起、5'->3' 方向）与基因组位置 g：
 *   t = R1 - g （长度为 1 时仍为 R1-g）
 * 半开区间映射为 [R1-end, R1-start)，长度不变。
 */
public final class Coordinates {
    private Coordinates() {
    }

    /** 半开基因组区间 [start,end) 在给定链上的转录顺序坐标区间（半开）。 */
    public static int[] toTranscript(char strand, int regionStart, int regionEnd, int start, int end) {
        if (strand == '+') {
            return new int[]{start - regionStart, end - regionStart};
        }
        return new int[]{regionEnd - end, regionEnd - start};
    }

    /** 单个基因组坐标到转录位置。 */
    public static int positionToTranscript(char strand, int regionStart, int regionEnd, int genomic) {
        return strand == '+' ? genomic - regionStart : regionEnd - genomic;
    }

    /**
     * 两条相邻 exon（转录顺序 u 在 v 之前）之间的内含子基因组半开区间。
     * 反向链时，前一个 exon 在基因组上坐标更大，必须交换端点，而不是直接取 u.end..v.start。
     */
    public static int[] intronGenomic(char strand, Exon u, Exon v) {
        int s;
        int e;
        if (strand == '+') {
            s = u.end();
            e = v.start();
        } else {
            s = v.end();
            e = u.start();
        }
        return new int[]{Math.min(s, e), Math.max(s, e)};
    }

    /** 转录顺序是否与基因组坐标递增一致（仅正链）。 */
    public static boolean genomicAscending(char strand) {
        return strand == '+';
    }
}
