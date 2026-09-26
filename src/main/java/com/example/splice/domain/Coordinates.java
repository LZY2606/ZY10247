package com.example.splice.domain;

/**
 * 基因组坐标工具：统一半开坐标 [start,end)，start&lt;end，沿基因组正方向。
 *
 * <p>负链转录方向 5'-&gt;3' 与基因组坐标方向相反。将链从 + 更正为 -（或反向）
 * 时，必须在同一跨度内做“镜像反射”：
 * <pre>
 *     c' = spanStart + spanEnd - c
 * </pre>
 * 半开区间 [a,b) 反射后为 [spanStart+spanEnd-b, spanStart+spanEnd-a)，
 * 仍然升序且长度不变。仅把 exon 数组倒序只会得到错误语义的坐标。
 */
public final class Coordinates {
    private Coordinates() {
    }

    /** 单点坐标在跨度 [spanStart,spanEnd) 内的镜像。 */
    public static int mirror(int coord, int spanStart, int spanEnd) {
        if (coord < spanStart || coord > spanEnd) {
            throw new IllegalArgumentException(
                    "coord " + coord + " outside span [" + spanStart + "," + spanEnd + ")");
        }
        return spanStart + spanEnd - coord;
    }

    /** 半开区间 [start,end) 的镜像，返回升序的半开区间端点 {newStart,newEnd}。 */
    public static int[] mirrorInterval(int start, int end, int spanStart, int spanEnd) {
        if (end <= start) {
            throw new IllegalArgumentException("not a half-open ascending interval");
        }
        int newStart = mirror(end, spanStart, spanEnd);
        int newEnd = mirror(start, spanStart, spanEnd);
        if (newEnd <= newStart) {
            throw new IllegalStateException("mirrored interval not ascending");
        }
        return new int[]{newStart, newEnd};
    }
}
