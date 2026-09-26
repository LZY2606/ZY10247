package com.example.splice.web;

import com.example.splice.fixture.FixtureData;
import com.example.splice.repo.GeneRepository;
import com.example.splice.splice.GraphBuilder;
import com.example.splice.splice.SpliceGraph;
import com.example.splice.svg.GraphSvg;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class BenchController {

    private final BenchService service;
    private final GeneRepository geneRepository;

    public BenchController(BenchService service, GeneRepository geneRepository) {
        this.service = service;
        this.geneRepository = geneRepository;
    }

    @GetMapping("/graph")
    public Map<String, Object> graph(@RequestParam(required = false) String strand) {
        com.example.splice.domain.Models.Gene g = service.gene();
        com.example.splice.domain.Models.Strand effective =
                strand == null || strand.isBlank() ? g.strand()
                        : com.example.splice.domain.Models.Strand.of(strand);
        SpliceGraph graph = GraphBuilder.build(g, effective);
        return service.graphView(graph);
    }

    @PostMapping("/runs")
    public Map<String, Object> createRun(@RequestBody RunRequest request) {
        return service.createRun(request);
    }

    @GetMapping("/runs")
    public List<Map<String, Object>> runs() {
        return service.listRuns();
    }

    @GetMapping("/runs/{id}")
    public Map<String, Object> run(@PathVariable long id) {
        Map<String, Object> run = service.getRun(id);
        if (run == null) {
            throw new NotFoundException("run not found: " + id);
        }
        return run;
    }

    @GetMapping("/runs/{fromId}/diff/{toId}")
    public Map<String, Object> diff(@PathVariable long fromId, @PathVariable long toId) {
        return service.diff(fromId, toId);
    }

    /** 导出运行记录为 JSON Lines（每行一条完整记录，便于跨运行重放/审计）。 */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> summary : service.listRuns()) {
            long id = ((Number) summary.get("id")).longValue();
            sb.append(toJsonLine(service.getRun(id))).append('\n');
        }
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"splice-runs.jsonl\"")
                .contentType(MediaType.parseMediaType("application/x-ndjson"))
                .body(body);
    }

    /**
     * 清空业务数据并重新导入固定 fixture。
     * 默认同时清空运行历史；keepRuns=true 仅重建业务表、保留运行记录。
     */
    @PostMapping("/admin/reimport")
    public Map<String, Object> reimport(@RequestParam(defaultValue = "false") boolean keepRuns) {
        geneRepository.replaceAll(FixtureData.gene(), !keepRuns);
        SpliceGraph graph = GraphBuilder.build(FixtureData.gene(), FixtureData.gene().strand());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reimported", true);
        out.put("geneId", graph.geneId());
        out.put("exons", graph.nodes().size());
        out.put("junctions", graph.edges().size());
        out.put("paths", graph.paths().size());
        return out;
    }

    /** 服务端 SVG（也可直接在浏览器地址栏打开）。 */
    @GetMapping(value = "/graph.svg", produces = "image/svg+xml;charset=UTF-8")
    public ResponseEntity<String> svg(@RequestParam(required = false) String strand) {
        com.example.splice.domain.Models.Gene g = service.gene();
        com.example.splice.domain.Models.Strand effective =
                strand == null || strand.isBlank() ? g.strand()
                        : com.example.splice.domain.Models.Strand.of(strand);
        SpliceGraph graph = GraphBuilder.build(g, effective);
        return ResponseEntity.ok(GraphSvg.render(graph));
    }

    private String toJsonLine(Map<String, Object> run) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(run);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static class NotFoundException extends RuntimeException {
        NotFoundException(String message) {
            super(message);
        }
    }
}
