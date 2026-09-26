package com.example.splice.repo;

import com.example.splice.domain.Models.Exon;
import com.example.splice.domain.Models.Gene;
import com.example.splice.domain.Models.Junction;
import com.example.splice.domain.Models.JunctionCount;
import com.example.splice.domain.Models.Sample;
import com.example.splice.domain.Models.Strand;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class GeneRepository {

    private final JdbcTemplate jdbc;

    public GeneRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isEmpty() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM genes", Integer.class);
        return n == null || n == 0;
    }

    @Transactional
    public void replaceAll(Gene gene) {
        replaceAll(gene, true);
    }

    @Transactional
    public void replaceAll(Gene gene, boolean clearRuns) {
        if (clearRuns) {
            jdbc.update("DELETE FROM locks");
            jdbc.update("DELETE FROM runs");
        }
        jdbc.update("DELETE FROM junction_counts");
        jdbc.update("DELETE FROM junctions");
        jdbc.update("DELETE FROM exons");
        jdbc.update("DELETE FROM samples");
        jdbc.update("DELETE FROM genes");

        jdbc.update("INSERT INTO genes(id,symbol,contig,strand,span_start,span_end) VALUES(?,?,?,?,?,?)",
                gene.id(), gene.symbol(), gene.contig(), gene.strand().code(),
                gene.spanStart(), gene.spanEnd());
        for (Exon e : gene.exons()) {
            jdbc.update("INSERT INTO exons(id,gene_id,rank,contig,start_pos,end_pos,strand) "
                            + "VALUES(?,?,?,?,?,?,?)",
                    e.id(), e.geneId(), e.rank(), e.contig(), e.start(), e.end(), e.strand().code());
        }
        for (Junction j : gene.junctions()) {
            jdbc.update("INSERT INTO junctions(id,gene_id,donor_exon_id,acceptor_exon_id,contig,"
                            + "start_pos,end_pos,mappability) VALUES(?,?,?,?,?,?,?,?)",
                    j.id(), j.geneId(), j.donorExonId(), j.acceptorExonId(), j.contig(),
                    j.genomicStart(), j.genomicEnd(), j.mappability());
        }
        for (Sample s : gene.samples()) {
            jdbc.update("INSERT INTO samples(id,name,condition_group) VALUES(?,?,?)",
                    s.id(), s.name(), s.conditionGroup());
        }
        for (JunctionCount c : gene.counts()) {
            jdbc.update("INSERT INTO junction_counts(junction_id,sample_id,count,reason) "
                            + "VALUES(?,?,?,?)",
                    c.junctionId(), c.sampleId(), c.count(), c.reason());
        }
    }

    public Gene load() {
        List<Gene> genes = jdbc.query("SELECT * FROM genes", GeneRepository::mapGene);
        if (genes.isEmpty()) {
            return null;
        }
        Gene head = genes.get(0);
        List<Exon> exons = jdbc.query(
                "SELECT * FROM exons WHERE gene_id=? ORDER BY rank",
                GeneRepository::mapExon, head.id());
        List<Junction> junctions = jdbc.query(
                "SELECT * FROM junctions WHERE gene_id=? ORDER BY id",
                (rs, i) -> mapJunction(rs, head.strand()), head.id());
        List<Sample> samples = jdbc.query("SELECT * FROM samples ORDER BY id",
                GeneRepository::mapSample);
        List<JunctionCount> counts = jdbc.query(
                "SELECT * FROM junction_counts ORDER BY junction_id,sample_id",
                GeneRepository::mapCount);
        return new Gene(head.id(), head.symbol(), head.contig(), head.strand(),
                head.spanStart(), head.spanEnd(), exons, junctions, samples, counts);
    }

    private static Gene mapGene(ResultSet rs, int i) throws SQLException {
        return new Gene(rs.getString("id"), rs.getString("symbol"),
                rs.getString("contig"), Strand.of(rs.getString("strand")),
                rs.getInt("span_start"), rs.getInt("span_end"),
                List.of(), List.of(), List.of(), List.of());
    }

    private static Exon mapExon(ResultSet rs, int i) throws SQLException {
        return new Exon(rs.getString("id"), rs.getString("gene_id"),
                rs.getInt("rank"), rs.getString("contig"),
                rs.getInt("start_pos"), rs.getInt("end_pos"),
                Strand.of(rs.getString("strand")));
    }

    private static Junction mapJunction(ResultSet rs, Strand strand) throws SQLException {
        return new Junction(rs.getString("id"), rs.getString("gene_id"),
                rs.getString("donor_exon_id"), rs.getString("acceptor_exon_id"),
                rs.getString("contig"), rs.getInt("start_pos"), rs.getInt("end_pos"),
                rs.getDouble("mappability"), strand);
    }

    private static Sample mapSample(ResultSet rs, int i) throws SQLException {
        return new Sample(rs.getString("id"), rs.getString("name"),
                rs.getString("condition_group"));
    }

    private static JunctionCount mapCount(ResultSet rs, int i) throws SQLException {
        int nullable = rs.getInt("count");
        Integer count = rs.wasNull() ? null : nullable;
        return new JunctionCount(rs.getString("junction_id"), rs.getString("sample_id"),
                count, rs.getString("reason"));
    }
}
