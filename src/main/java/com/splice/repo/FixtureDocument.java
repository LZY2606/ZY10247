package com.splice.repo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/** fixture JSON 的 POJO 映射（Jackson）。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class FixtureDocument {
    public List<LocusDto> loci;
    public List<SampleDto> samples;
    public Map<String, Map<String, Map<String, Integer>>> counts;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class LocusDto {
        public String id;
        public String chromosome;
        public int[] region;
        public String strand;
        public List<ExonDto> exons;
        public List<JunctionDto> junctions;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class ExonDto {
        public int ord;
        public String name;
        public int start;
        public int end;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class JunctionDto {
        public String id;
        public int from;
        public int to;
        public boolean lowMappability;
        public String note;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class SampleDto {
        public String id;
        public String group;
        public boolean collected;
    }
}
