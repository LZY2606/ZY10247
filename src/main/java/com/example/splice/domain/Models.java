package com.example.splice.domain;

import java.util.List;

/**
 * 核心领域模型。所有基因组坐标一律使用半开区间 [start, end)（start < end），
 * 坐标方向恒为基因组正方向；链方向由 {@link Strand} 单独表达。
 */
public final class Models {
    private Models() {
    }

    public enum Strand {
        PLUS('+'), MINUS('-');

        private final char code;

        Strand(char code) {
            this.code = code;
        }

        public char code() {
            return code;
        }

        public Strand opposite() {
            return this == PLUS ? MINUS : PLUS;
        }

        public static Strand of(String value) {
            if (value == null) {
                throw new IllegalArgumentException("strand is null");
            }
            String v = value.trim();
            if ("+".equals(v) || "plus".equalsIgnoreCase(v)) {
                return PLUS;
            }
            if ("-".equals(v) || "minus".equalsIgnoreCase(v)) {
                return MINUS;
            }
            throw new IllegalArgumentException("unknown strand: " + value);
        }

        @Override
        public String toString() {
            return String.valueOf(code);
        }
    }

    /** 半开基因组区间 [start, end)，坐标沿基因组正方向，与链无关。 */
    public record GenomicInterval(String contig, int start, int end) {
        public GenomicInterval {
            if (start < 0 || end <= start) {
                throw new IllegalArgumentException(
                        "half-open interval requires 0 <= start < end: " + start + "," + end);
            }
        }

        public int length() {
            return end - start;
        }
    }

    /**
     * exon：半开区间 [start,end)，转录顺序序号按 5'->3' 给出。
     *
     * @param rank 转录顺序（1 基），正链上随坐标递增，负链上随坐标递减
     */
    public record Exon(String id, String geneId, int rank, String contig,
                       int start, int end, Strand strand) {
        public GenomicInterval interval() {
            return new GenomicInterval(contig, start, end);
        }

        public int length() {
            return end - start;
        }
    }

    /**
     * splice junction：donor 与 acceptor 为外显子边界（转录 5'->3' 意义）。
     * 存储时仍使用基因组半开坐标；genomicStart/genomicEnd 为内含子区间
     * [genomicStart,genomicEnd)，始终满足 genomicStart < genomicEnd。
     *
     * <p>正链：donorExon 边界 = genomicStart（donor site, 外显子末端），
     * acceptor 边界 = genomicEnd；
     * 负链：供体在坐标更大的一端，即 donorBoundary=genomicEnd，
     * acceptorBoundary=genomicStart。仅倒序数组而不做坐标镜像会得到错误语义。
     */
    public record Junction(String id, String geneId,
                           String donorExonId, String acceptorExonId,
                           String contig, int genomicStart, int genomicEnd,
                           double mappability, Strand strand) {
        public Junction {
            if (genomicEnd <= genomicStart) {
                throw new IllegalArgumentException(
                        "intron interval must be ascending half-open: " + genomicStart + "," + genomicEnd);
            }
            if (mappability < 0.0 || mappability > 1.0) {
                throw new IllegalArgumentException("mappability must be in [0,1]: " + mappability);
            }
        }

        /** 转录 5' 端外显子边界（供体位点坐标）。 */
        public int donorBoundary() {
            return strand == Strand.PLUS ? genomicStart : genomicEnd;
        }

        /** 转录 3' 端外显子边界（受体位点坐标）。 */
        public int acceptorBoundary() {
            return strand == Strand.PLUS ? genomicEnd : genomicStart;
        }
    }

    public record Sample(String id, String name, String conditionGroup) {
    }

    /**
     * 单个 junction x sample 的计数单元。
     *
     * @param count  null 表示该样本“未采集”(not measured)；0 表示采集到的真实零计数，
     *               二者语义不同，不得互相归并
     * @param reason count 为 null 时给出未采集原因
     */
    public record JunctionCount(String junctionId, String sampleId,
                                Integer count, String reason) {
        public boolean measured() {
            return count != null;
        }
    }

    public record Gene(String id, String symbol, String contig, Strand strand,
                       int spanStart, int spanEnd,
                       List<Exon> exons, List<Junction> junctions,
                       List<Sample> samples, List<JunctionCount> counts) {
        public Gene {
            exons = List.copyOf(exons);
            junctions = List.copyOf(junctions);
            samples = List.copyOf(samples);
            counts = List.copyOf(counts);
        }
    }
}
