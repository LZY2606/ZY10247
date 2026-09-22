package com.splice.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.splice.domain.Locus;
import com.splice.domain.SampleInfo;
import com.splice.graph.GraphBuilder;
import com.splice.graph.GraphView;
import com.splice.graph.Solution;
import com.splice.repo.FixtureDocument;
import com.splice.repo.FixtureLoader;
import com.splice.repo.SpliceRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AdjudicationService {
    private final SpliceRepository repository;
    private final FixtureLoader fixtureLoader;
    private final GraphBuilder graphBuilder;
    private final com.splice.graph.PathSolver pathSolver;
    private final ObjectMapper mapper = new ObjectMapper();

    public AdjudicationService(SpliceRepository repository, FixtureLoader fixtureLoader,
                               GraphBuilder graphBuilder,
                               com.splice.graph.PathSolver pathSolver) {
        this.repository = repository;
        this.fixtureLoader = fixtureLoader;
        this.graphBuilder = graphBuilder;
        this.pathSolver = pathSolver;
    }

    public String fixtureSha() {
        return fixtureLoader.sha256();
    }

    public Map<String, Object> locusView(String locusId, Character strandOverride, Set<String> excluded) {
        Locus locus = repository.findLocus(locusId);
        char strand = strandOverride == null ? locus.strand() : strandOverride;
        List<SampleInfo> samples = repository.findSamples();
        GraphView view = graphBuilder.build(locus, samples, repository.findCountMatrix(locusId),
                strand, excluded == null ? Set.of() : excluded);
        return serializeView(view);
    }

    public Map<String, Object> solve(String locusId, Character strandOverride, Set<String> excluded,
                                     List<List<Integer>> lockedNodes, boolean persist) {
        Locus locus = repository.findLocus(locusId);
        char strand = strandOverride == null ? locus.strand() : strandOverride;
        List<SampleInfo> samples = repository.findSamples();
        GraphView view = graphBuilder.build(locus, samples, repository.findCountMatrix(locusId),
                strand, excluded == null ? Set.of() : excluded);
        List<Solution> solutions = pathSolver.solve(view,
                lockedNodes == null ? List.of() : lockedNodes);

        Map<String, Object> result = serializeView(view);
        result.put("solutions", solutions);
        result.put("excludedIds", excluded == null ? List.of() : excluded.stream().sorted().toList());
        result.put("lockedNodes", lockedNodes == null ? List.of() : lockedNodes);
        result.put("fixtureSha256", fixtureLoader.sha256());
        if (persist) {
            String excludedJson = writeJson(excluded == null ? List.of() : excluded);
            String lockedJson = writeJson(lockedNodes == null ? List.of() : lockedNodes);
            long runId = repository.saveRun(locusId, strand, excludedJson, lockedJson,
                    writeJson(result), fixtureLoader.sha256());
            result.put("runId", runId);
        }
        return result;
    }

    public Map<String, Object> reimport() {
        FixtureDocument doc = fixtureLoader.load();
        repository.reseed(doc);
        return Map.of("reimported", true, "loci", doc.loci.size(), "fixtureSha256",
                fixtureLoader.sha256());
    }

    public Map<String, Object> diff(long a, long b) {
        Map<String, Object> ra = repository.getRun(a);
        Map<String, Object> rb = repository.getRun(b);
        if (ra == null || rb == null) {
            throw new IllegalArgumentException("运行记录不存在：" + a + " / " + b);
        }
        try {
            Map<String, Object> va = mapper.readValue((String) ra.get("result_json"),
                    new TypeReference<Map<String, Object>>() {
                    });
            Map<String, Object> vb = mapper.readValue((String) rb.get("result_json"),
                    new TypeReference<Map<String, Object>>() {
                    });
            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("a", runHeader(ra));
            diff.put("b", runHeader(rb));
            diff.put("locusChanged", !ra.get("locus_id").equals(rb.get("locus_id")));
            diff.put("strandChanged", !ra.get("strand_used").equals(rb.get("strand_used")));
            diff.put("excludedChanged", !ra.get("excluded_ids").equals(rb.get("excluded_ids")));
            diff.put("lockedChanged", !ra.get("locked_paths").equals(rb.get("locked_paths")));
            diff.put("solutionsA", va.get("solutions"));
            diff.put("solutionsB", vb.get("solutions"));
            diff.put("flowSignatureChanged", !primarySignature(va).equals(primarySignature(vb)));
            return diff;
        } catch (Exception e) {
            throw new IllegalStateException("运行记录解析失败", e);
        }
    }

    private String primarySignature(Map<String, Object> v) {
        List<?> sols = (List<?>) v.get("solutions");
        if (sols == null || sols.isEmpty()) {
            return "";
        }
        return String.valueOf(((Map<?, ?>) sols.get(0)).get("flowSignature"));
    }

    private Map<String, Object> runHeader(Map<String, Object> row) {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("id", row.get("id"));
        h.put("createdAt", row.get("created_at"));
        h.put("locusId", row.get("locus_id"));
        h.put("strandUsed", row.get("strand_used"));
        h.put("excludedIds", row.get("excluded_ids"));
        h.put("lockedPaths", row.get("locked_paths"));
        h.put("fixtureSha256", row.get("fixture_sha256"));
        return h;
    }

    private Map<String, Object> serializeView(GraphView view) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("locusId", view.locus().id());
        m.put("chromosome", view.locus().chromosome());
        m.put("regionStart", view.locus().regionStart());
        m.put("regionEnd", view.locus().regionEnd());
        m.put("declaredStrand", String.valueOf(view.declaredStrand()));
        m.put("effectiveStrand", String.valueOf(view.effectiveStrand()));
        m.put("nodes", view.nodes());
        m.put("edges", view.edges());
        m.put("samples", view.samples());
        return m;
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }
}
