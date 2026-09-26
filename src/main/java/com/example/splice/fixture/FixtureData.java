package com.example.splice.fixture;

import com.example.splice.domain.Models.Exon;
import com.example.splice.domain.Models.Gene;
import com.example.splice.domain.Models.Junction;
import com.example.splice.domain.Models.JunctionCount;
import com.example.splice.domain.Models.Sample;
import com.example.splice.domain.Models.Strand;

import java.util.ArrayList;
import java.util.List;

/**
 * 固定交付 fixture（只读）。
 *
 * <p>基因 DIAMOND1 位于负链，转录 5'-&gt;3' 与基因组坐标反向；exon 的 rank
 * 即转录顺序。junction 内含子区间一律以升序半开坐标 [start,end) 存储，
 * 供体/受体语义由链方向决定（见 {@code Models.Junction}）。
 *
 * <p>拓扑为“串行双菱形”：
 * <pre>
 *   E1 -> [ E2 / E3 ] -> E4 -> [ E5 / E6 ] -> E7
 * </pre>
 * 存在 4 条全长候选路径 P1..P4；中段连接边的联合流量不可直接观测，
 * 因此在总流量下有两组边流量完全相同、但路径构成不同的稀疏分解。
 *
 * <p>样本口径：CTRL-1..3 为对照组，TRT-1..3 为处理组；TRT-2 文库失败，
 * 全部 junction 未采集（null + reason=library_failed），与真实 0 计数严格区分。
 */
public final class FixtureData {

    public static final String GENE_ID = "DIAMOND1";

    private FixtureData() {
    }

    public static Gene gene() {
        Strand strand = Strand.MINUS;
        int spanStart = 200;
        int spanEnd = 4900;

        List<Exon> exons = List.of(
                new Exon("E1", GENE_ID, 1, "chr1", 4400, 4900, strand),
                new Exon("E2", GENE_ID, 2, "chr1", 3600, 4100, strand),
                new Exon("E3", GENE_ID, 3, "chr1", 2900, 3400, strand),
                new Exon("E4", GENE_ID, 4, "chr1", 2200, 2600, strand),
                new Exon("E5", GENE_ID, 5, "chr1", 1400, 1900, strand),
                new Exon("E6", GENE_ID, 6, "chr1", 700, 1200, strand),
                new Exon("E7", GENE_ID, 7, "chr1", 200, 400, strand)
        );

        // donor/acceptor 按转录顺序给出；负链下 donorBoundary=genomicEnd。
        List<Junction> junctions = List.of(
                new Junction("j1", GENE_ID, "E1", "E2", "chr1", 4100, 4400, 0.97, strand),
                new Junction("j2", GENE_ID, "E1", "E3", "chr1", 3400, 4400, 0.95, strand),
                new Junction("j3", GENE_ID, "E2", "E4", "chr1", 2600, 3600, 0.62, strand),
                new Junction("j4", GENE_ID, "E3", "E4", "chr1", 2600, 2900, 0.96, strand),
                new Junction("j5", GENE_ID, "E4", "E5", "chr1", 1900, 2200, 0.98, strand),
                new Junction("j6", GENE_ID, "E4", "E6", "chr1", 1200, 2200, 0.94, strand),
                new Junction("j7", GENE_ID, "E5", "E7", "chr1", 400, 1400, 0.96, strand),
                new Junction("j8", GENE_ID, "E6", "E7", "chr1", 400, 700, 0.95, strand)
        );

        List<Sample> samples = List.of(
                new Sample("CTRL-1", "对照样本1", "CTRL"),
                new Sample("CTRL-2", "对照样本2", "CTRL"),
                new Sample("CTRL-3", "对照样本3", "CTRL"),
                new Sample("TRT-1", "处理样本1", "TRT"),
                new Sample("TRT-2", "处理样本2(文库失败)", "TRT"),
                new Sample("TRT-3", "处理样本3", "TRT")
        );

        List<JunctionCount> counts = new ArrayList<>();
        // 顺序: CTRL-1, CTRL-2, CTRL-3, TRT-1, TRT-2, TRT-3
        // null = 未采集；0 = 真实零计数，二者不得合并
        add(counts, "j1", new Integer[]{55, 25, 0, 60, null, 40}, samples, "library_failed");
        add(counts, "j2", new Integer[]{15, 5, 0, 0, null, 0}, samples, "library_failed");
        add(counts, "j3", new Integer[]{55, 25, null, 60, null, 40}, samples, "not_measured");
        add(counts, "j4", new Integer[]{15, 5, 0, 0, null, 0}, samples, "library_failed");
        add(counts, "j5", new Integer[]{20, 10, 10, 30, null, 0}, samples, "library_failed");
        add(counts, "j6", new Integer[]{35, 15, 10, 30, null, 40}, samples, "library_failed");
        add(counts, "j7", new Integer[]{20, 10, 10, 30, null, 0}, samples, "library_failed");
        add(counts, "j8", new Integer[]{35, 15, 10, 30, null, 40}, samples, "library_failed");

        return new Gene(GENE_ID, "DIAMOND1", "chr1", strand, spanStart, spanEnd,
                exons, junctions, samples, counts);
    }

    private static void add(List<JunctionCount> sink, String junctionId,
                            Integer[] values, List<Sample> samples, String nullReason) {
        for (int i = 0; i < values.length; i++) {
            Integer v = values[i];
            sink.add(new JunctionCount(junctionId, samples.get(i).id(),
                    v, v == null ? nullReason : null));
        }
    }
}
