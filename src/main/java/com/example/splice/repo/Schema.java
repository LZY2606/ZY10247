package com.example.splice.repo;

/** SQLite DDL。计数表 count 可空：NULL=未采集，0=真实零计数。 */
public final class Schema {
    private Schema() {
    }

    public static final String[] DDL = {
            """
            CREATE TABLE IF NOT EXISTS app_meta (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS genes (
                id TEXT PRIMARY KEY,
                symbol TEXT NOT NULL,
                contig TEXT NOT NULL,
                strand TEXT NOT NULL,
                span_start INTEGER NOT NULL,
                span_end INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS exons (
                id TEXT PRIMARY KEY,
                gene_id TEXT NOT NULL,
                rank INTEGER NOT NULL,
                contig TEXT NOT NULL,
                start_pos INTEGER NOT NULL,
                end_pos INTEGER NOT NULL,
                strand TEXT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS junctions (
                id TEXT PRIMARY KEY,
                gene_id TEXT NOT NULL,
                donor_exon_id TEXT NOT NULL,
                acceptor_exon_id TEXT NOT NULL,
                contig TEXT NOT NULL,
                start_pos INTEGER NOT NULL,
                end_pos INTEGER NOT NULL,
                mappability REAL NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS samples (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                condition_group TEXT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS junction_counts (
                junction_id TEXT NOT NULL,
                sample_id TEXT NOT NULL,
                count INTEGER,
                reason TEXT,
                PRIMARY KEY (junction_id, sample_id)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS runs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_at TEXT NOT NULL,
                note TEXT,
                settings_json TEXT NOT NULL,
                result_json TEXT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS locks (
                run_id INTEGER PRIMARY KEY,
                path_id TEXT NOT NULL,
                amount TEXT NOT NULL,
                FOREIGN KEY(run_id) REFERENCES runs(id)
            )
            """
    };
}
