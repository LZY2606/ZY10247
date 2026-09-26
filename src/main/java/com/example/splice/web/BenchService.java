package com.example.splice.web;

import com.example.splice.domain.Models.Gene;
import com.example.splice.domain.Models.Strand;
import com.example.splice.repo.GeneRepository;
import com.example.splice.repo.RunRepository;
import com.example.splice.solver.DecompositionSolver;
import com.example.splice.solver.Frac;
import com.example.splice.solver.SolverModels;
import com.example.splice.splice.GraphBuilder;
import com.example.splice.splice.SpliceGraph;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BenchService {

    private final GeneRepository geneRepository;
    private final RunRepository runRepository;
    private final DecompositionSolver solver;
    private final ObjectMapper mapper;

    public BenchService(GeneRepository geneRepository, RunRepository runRepository,
                        DecompositionSolver solver, ObjectMapper mapper) {
        this.geneRepository = geneRepository;
        this.runRepository = runRepository;
        this.solver = solver;
        this.mapper = mapper;
    }

    public Gene gene() {
        Gene g = geneRepository.load();
        if (g == null) {
            throw new IllegalStateException("数据库为空，请先重新导入 fixture");
        }
        return g;
    }

    /** 当前图视图（默认链、未排除），供首页渲染。 */
    public SpliceGraph currentGraph() {
        Gene g = gene();
        return GraphBuilder.build(g, g.strand());
    }

    public Map<String, Object> createRun(RunRequest request) {
        Gene g = gene();
        Strand effective = request.strand() == null || request.strand().isBlank()
                ? g.strand() : Strand.of(request.strand());
        SpliceGraph graph = GraphBuilder.build(g, effective);

        Set<String> excluded = new LinkedHashSet<>();
        if (request.excludeEdges() != null) {
            excluded.addAll(request.excludeEdges());
        }
        double threshold = request.minMappability() == null ? -1.0 : request.minMappability();
        if (threshold >= 0) {
            for (SpliceGraph.Edge e : graph.edges()) {
                if (e.mappability() < threshold) {
                    excluded.add(e.junctionId());
                }
            }
        }

        Map<String, Frac> locks = new LinkedHashMap<>();
        if (request.locks() != null) {
            for (Map.Entry<String, String> en : request.locks().entrySet()) {
                if (en.getValue() == null || en.getValue().isBlank()) {
                    continue;
                }
                locks.put(en.getKey(), parseAmount(en.getValue()));
            }
        }

        List<SolverModels.ScopeResult> scopes = solver.solve(graph, excluded, locks);

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("strand", effective.code());
        settings.put("strandFlipped", graph.strandFlipped());
        settings.put("excludeEdges", new ArrayList<>(excluded));
        settings.put("minMappability", threshold < 0 ? null : threshold);
        settings.put("locks", locks.entrySet().stream()
                .collect(LinkedHashMap::new,
                        (m, e) -> m.put(e.getKey(), e.getValue().toExactString()),
                        LinkedHashMap::putAll));
        settings.put("note", request.note());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("graph", graphView(graph));
        result.put("scopes", scopes);

        String createdAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String settingsJson = toJson(settings);
        String resultJson = toJson(result);
        String lockedPathId = locks.isEmpty() ? null : locks.keySet().iterator().next();
        String lockedAmount = locks.isEmpty() ? null : locks.values().iterator().next().toExactString();
        long id = runRepository.insert(createdAt, request.note(), settingsJson, resultJson,
                lockedPathId, lockedAmount);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        response.put("createdAt", createdAt);
        response.put("settings", settings);
        response.putAll(result);
        return response;
    }

    public List<Map<String, Object>> listRuns() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : runRepository.list()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("createdAt", row.get("created_at"));
            item.put("note", row.get("note"));
            item.put("settings", readJson((String) row.get("settings_json")));
            item.put("lockedPathId", row.get("locked_path_id"));
            item.put("lockedAmount", row.get("locked_amount"));
            out.add(item);
        }
        return out;
    }

    public Map<String, Object> getRun(long id) {
        Map<String, Object> row = runRepository.get(id);
        if (row == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("createdAt", row.get("created_at"));
        out.put("note", row.get("note"));
        out.put("settings", readJson((String) row.get("settings_json")));
        out.putAll(readJson((String) row.get("result_json"), Map.class));
        return out;
    }

    /** 两次运行的结构化差分。 */
    public Map<String, Object> diff(long fromId, long toId) {
        Map<String, Object> a = getRun(fromId);
        Map<String, Object> b = getRun(toId);
        if (a == null || b == null) {
            throw new IllegalArgumentException("运行不存在: " + (a == null ? fromId : toId));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fromId", fromId);
        out.put("toId", toId);
        out.put("settingsChanged", !a.get("settings").equals(b.get("settings")));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sa = (List<Map<String, Object>>) a.get("scopes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sb = (List<Map<String, Object>>) b.get("scopes");
        Map<String, Map<String, Object>> byA = new LinkedHashMap<>();
        for (Map<String, Object> s : sa) {
            byA.put((String) s.get("scope"), s);
        }
        List<Map<String, Object>> scopeDiffs = new ArrayList<>();
        for (Map<String, Object> t : sb) {
            Map<String, Object> u = byA.get(t.get("scope"));
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("scope", t.get("scope"));
            d.put("tiedBefore", u != null && Boolean.TRUE.equals(u.get("tied")));
            d.put("tiedAfter", Boolean.TRUE.equals(t.get("tied")));
            d.put("fingerprintBefore", u == null ? null : u.get("tieFingerprint"));
            d.put("fingerprintAfter", t.get("tieFingerprint"));
            d.put("flowChanged", u == null
                    || !java.util.Objects.equals(u.get("tieFingerprint"), t.get("tieFingerprint")));
            d.put("coverageBefore", u == null ? null : u.get("coverage"));
            d.put("coverageAfter", t.get("coverage"));
            scopeDiffs.add(d);
        }
        out.put("scopes", scopeDiffs);
        return out;
    }

    public Map<String, Object> graphView(SpliceGraph graph) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("geneId", graph.geneId());
        view.put("symbol", graph.symbol());
        view.put("contig", graph.contig());
        view.put("spanStart", graph.spanStart());
        view.put("spanEnd", graph.spanEnd());
        view.put("effectiveStrand", graph.effectiveStrand().code());
        view.put("storedStrand", gene().strand().code());
        view.put("strandFlipped", graph.strandFlipped());
        view.put("nodes", graph.nodes());
        view.put("edges", graph.edges());
        view.put("paths", graph.paths());
        view.put("samples", graph.samples());
        view.put("cells", graph.cellsByEdge());
        return view;
    }

    static Frac parseAmount(String raw) {
        String s = raw.trim();
        if (s.contains("/")) {
            String[] parts = s.split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("bad amount: " + raw);
            }
            return new Frac(Long.parseLong(parts[0].trim()), Long.parseLong(parts[1].trim()));
        }
        return Frac.of(Long.parseLong(s));
    }

    private String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> readJson(String json) {
        return readJson(json, Map.class);
    }

    @SuppressWarnings("unchecked")
    private <T> T readJson(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
