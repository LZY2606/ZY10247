package com.example.splice.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Repository
public class RunRepository {

    private final JdbcTemplate jdbc;

    public RunRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public long insert(String createdAt, String note, String settingsJson, String resultJson,
                       String lockedPathId, String lockedAmount) {
        jdbc.update("INSERT INTO runs(created_at,note,settings_json,result_json) VALUES(?,?,?,?)",
                createdAt, note, settingsJson, resultJson);
        Long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        long runId = id == null ? 0L : id;
        if (lockedPathId != null) {
            jdbc.update("INSERT INTO locks(run_id,path_id,amount) VALUES(?,?,?)",
                    runId, lockedPathId, lockedAmount);
        }
        return runId;
    }

    public List<Map<String, Object>> list() {
        return jdbc.queryForList(
                "SELECT r.id, r.created_at, r.note, r.settings_json, r.result_json, "
                        + "l.path_id AS locked_path_id, l.amount AS locked_amount "
                        + "FROM runs r LEFT JOIN locks l ON l.run_id=r.id ORDER BY r.id");
    }

    public Map<String, Object> get(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT r.id, r.created_at, r.note, r.settings_json, r.result_json, "
                        + "l.path_id AS locked_path_id, l.amount AS locked_amount "
                        + "FROM runs r LEFT JOIN locks l ON l.run_id=r.id WHERE r.id=?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
