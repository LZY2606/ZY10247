package com.splice.repo;

import com.splice.domain.Exon;
import com.splice.domain.Junction;
import com.splice.domain.Locus;
import com.splice.domain.SampleInfo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class SpliceRepository {
    private final JdbcTemplate jdbc;

    public SpliceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isEmpty() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM locus", Integer.class);
        return n == null || n == 0;
    }

    /** 清空并按固定 fixture 重新导入。零计数会写 cnt=0；未采集样本（collected=0）不写计数行。 */
    @Transactional
    public void reseed(FixtureDocument doc) {
        jdbc.update("DELETE FROM junction_count");
        jdbc.update("DELETE FROM junction");
        jdbc.update("DELETE FROM exon");
        jdbc.update("DELETE FROM sample");
        jdbc.update("DELETE FROM locus");
        jdbc.update("DELETE FROM run_record");

        for (FixtureDocument.LocusDto l : doc.loci) {
            jdbc.update("INSERT INTO locus(id,chromosome,region_start,region_end,strand) VALUES (?,?,?,?,?)",
                    l.id, l.chromosome, l.region[0], l.region[1], l.strand);
            for (FixtureDocument.ExonDto e : l.exons) {
                jdbc.update("INSERT INTO exon(locus_id,ord,name,start,end_pos) VALUES (?,?,?,?,?)",
                        l.id, e.ord, e.name, e.start, e.end);
            }
            for (FixtureDocument.JunctionDto j : l.junctions) {
                jdbc.update(
                        "INSERT INTO junction(id,locus_id,from_ord,to_ord,low_mappability,note) VALUES (?,?,?,?,?,?)",
                        j.id, l.id, j.from, j.to, j.lowMappability ? 1 : 0, j.note);
            }
        }
        for (FixtureDocument.SampleDto s : doc.samples) {
            jdbc.update("INSERT INTO sample(id,cond_group,collected) VALUES (?,?,?)",
                    s.id, s.group, s.collected ? 1 : 0);
        }
        if (doc.counts != null) {
            for (var locusEntry : doc.counts.entrySet()) {
                for (var junctionEntry : locusEntry.getValue().entrySet()) {
                    String junctionId = junctionEntry.getKey();
                    for (var sampleEntry : junctionEntry.getValue().entrySet()) {
                        jdbc.update(
                                "INSERT INTO junction_count(junction_id,sample_id,cnt) VALUES (?,?,?)",
                                junctionId, sampleEntry.getKey(), sampleEntry.getValue());
                    }
                }
            }
        }
    }

    public javax.sql.DataSource dataSource() {
        return jdbc.getDataSource();
    }

    public List<Locus> findLoci() {
        return jdbc.query("SELECT id,chromosome,region_start,region_end,strand FROM locus ORDER BY id",
                (rs, n) -> loadLocus(rs.getString("id"), rs.getString("chromosome"),
                        rs.getInt("region_start"), rs.getInt("region_end"), rs.getString("strand").charAt(0)));
    }

    public Locus findLocus(String id) {
        return jdbc.queryForObject(
                "SELECT id,chromosome,region_start,region_end,strand FROM locus WHERE id=?",
                (rs, n) -> loadLocus(rs.getString("id"), rs.getString("chromosome"),
                        rs.getInt("region_start"), rs.getInt("region_end"), rs.getString("strand").charAt(0)),
                id);
    }

    private Locus loadLocus(String id, String chromosome, int r0, int r1, char strand) {
        List<Exon> exons = jdbc.query(
                "SELECT ord,name,start,end_pos FROM exon WHERE locus_id=? ORDER BY ord",
                (rs, n) -> new Exon(rs.getInt("ord"), rs.getString("name"),
                        rs.getInt("start"), rs.getInt("end_pos")), id);
        List<Junction> junctions = jdbc.query(
                "SELECT id,from_ord,to_ord,low_mappability,note FROM junction WHERE locus_id=? ORDER BY id",
                (rs, n) -> new Junction(rs.getString("id"), rs.getInt("from_ord"), rs.getInt("to_ord"),
                        rs.getInt("low_mappability") == 1, rs.getString("note")), id);
        return new Locus(id, chromosome, r0, r1, strand, exons, junctions);
    }

    public List<SampleInfo> findSamples() {
        return jdbc.query("SELECT id,cond_group,collected FROM sample ORDER BY id",
                (rs, n) -> new SampleInfo(rs.getString("id"), rs.getString("cond_group"),
                        rs.getInt("collected") == 1));
    }

    /** junctionId -> (sampleId -> cnt)。缺失键表示该样本未采集（与显式 0 区分）。 */
    public Map<String, Map<String, Integer>> findCountMatrix(String locusId) {
        Map<String, Map<String, Integer>> matrix = new LinkedHashMap<>();
        jdbc.query(
                """
                SELECT jc.junction_id, jc.sample_id, jc.cnt
                FROM junction_count jc JOIN junction j ON j.id = jc.junction_id
                WHERE j.locus_id = ?
                ORDER BY jc.junction_id, jc.sample_id
                """,
                rs -> {
                    matrix.computeIfAbsent(rs.getString("junction_id"), k -> new LinkedHashMap<>())
                            .put(rs.getString("sample_id"), rs.getInt("cnt"));
                }, locusId);
        return matrix;
    }

    public long saveRun(String locusId, char strandUsed, String excludedIds, String lockedPaths,
                        String resultJson, String sha) {
        jdbc.update(
                """
                INSERT INTO run_record(created_at,locus_id,strand_used,excluded_ids,locked_paths,result_json,fixture_sha256)
                VALUES (datetime('now'),?,?,?,?,?,?)
                """,
                locusId, String.valueOf(strandUsed), excludedIds, lockedPaths, resultJson, sha);
        Long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        return id == null ? -1 : id;
    }

    public List<Map<String, Object>> listRuns(String locusId) {
        if (locusId == null || locusId.isBlank()) {
            return jdbc.queryForList(
                    "SELECT id,created_at,locus_id,strand_used,excluded_ids,locked_paths,fixture_sha256 "
                            + "FROM run_record ORDER BY id DESC");
        }
        return jdbc.queryForList(
                "SELECT id,created_at,locus_id,strand_used,excluded_ids,locked_paths,fixture_sha256 "
                        + "FROM run_record WHERE locus_id=? ORDER BY id DESC", locusId);
    }

    public Map<String, Object> getRun(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM run_record WHERE id=?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void deleteRun(long id) {
        jdbc.update("DELETE FROM run_record WHERE id=?", id);
    }

    public List<Map<String, Object>> runsByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        String placeholders = String.join(",", ids.stream().map(x -> "?").toList());
        return jdbc.queryForList(
                "SELECT * FROM run_record WHERE id IN (" + placeholders + ") ORDER BY id",
                ids.toArray());
    }
}
