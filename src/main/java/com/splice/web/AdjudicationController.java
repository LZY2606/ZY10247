package com.splice.web;

import com.splice.repo.SpliceRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AdjudicationController {
    private final AdjudicationService service;
    private final SpliceRepository repository;

    public AdjudicationController(AdjudicationService service, SpliceRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @GetMapping("/loci")
    public List<Map<String, Object>> loci() {
        return repository.findLoci().stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.id());
            m.put("chromosome", l.chromosome());
            m.put("regionStart", l.regionStart());
            m.put("regionEnd", l.regionEnd());
            m.put("strand", String.valueOf(l.strand()));
            return m;
        }).toList();
    }

    @GetMapping("/samples")
    public Object samples() {
        return repository.findSamples();
    }

    @GetMapping("/loci/{id}/graph")
    public Object graph(@PathVariable String id,
                        @RequestParam(required = false) String strand,
                        @RequestParam(required = false) List<String> exclude) {
        return service.locusView(id, parseStrand(strand),
                exclude == null ? java.util.Set.of() : java.util.Set.copyOf(exclude));
    }

    public record SolveRequest(String locusId, String strand, List<String> exclude,
                               List<List<Integer>> lockPaths, boolean persist) {
    }

    @PostMapping("/solve")
    public Object solve(@RequestBody SolveRequest req) {
        if (req.locusId() == null || req.locusId().isBlank()) {
            throw new IllegalArgumentException("locusId 必填");
        }
        return service.solve(req.locusId(), parseStrand(req.strand()),
                req.exclude() == null ? java.util.Set.of() : java.util.Set.copyOf(req.exclude()),
                req.lockPaths(), req.persist());
    }

    @GetMapping("/runs")
    public Object runs(@RequestParam(required = false) String locusId) {
        return repository.listRuns(locusId);
    }

    @GetMapping("/runs/{id}")
    public Object run(@PathVariable long id) {
        Map<String, Object> row = repository.getRun(id);
        if (row == null) {
            return ResponseEntity.status(404).body(Map.of("error", "运行记录不存在"));
        }
        return row;
    }

    @DeleteMapping("/runs/{id}")
    public Object deleteRun(@PathVariable long id) {
        repository.deleteRun(id);
        return Map.of("deleted", id);
    }

    @GetMapping("/runs/{a}/diff/{b}")
    public Object diff(@PathVariable long a, @PathVariable long b) {
        return service.diff(a, b);
    }

    @PostMapping("/admin/reimport")
    public Object reimport() {
        return service.reimport();
    }

    @GetMapping("/fixture")
    public Object fixtureInfo() {
        return Map.of("sha256", service.fixtureSha());
    }

    private Character parseStrand(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        if (!s.equals("+") && !s.equals("-")) {
            throw new IllegalArgumentException("strand 仅允许 '+' 或 '-'");
        }
        return s.charAt(0);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
