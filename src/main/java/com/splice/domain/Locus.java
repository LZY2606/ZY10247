package com.splice.domain;

import java.util.List;

public record Locus(String id, String chromosome, int regionStart, int regionEnd, char strand,
                    List<Exon> exons, List<Junction> junctions) {
    public Exon exon(int ord) {
        return exons.stream().filter(e -> e.ord() == ord).findFirst().orElseThrow();
    }
}
