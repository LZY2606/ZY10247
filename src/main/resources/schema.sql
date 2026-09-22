-- 剪接路径裁决台：SQLite schema（全部为整数计数，缺失用 NULL 表达）
CREATE TABLE IF NOT EXISTS locus (
  id            TEXT PRIMARY KEY,
  chromosome    TEXT NOT NULL,
  region_start  INTEGER NOT NULL,   -- 半开坐标 [start,end)
  region_end    INTEGER NOT NULL,
  strand        TEXT NOT NULL CHECK (strand IN ('+','-'))
);

CREATE TABLE IF NOT EXISTS exon (
  locus_id      TEXT NOT NULL REFERENCES locus(id),
  ord           INTEGER NOT NULL,   -- 转录顺序上的顺序（5'->3'）
  name          TEXT NOT NULL,
  start         INTEGER NOT NULL,   -- 基因组半开坐标
  end_pos       INTEGER NOT NULL,
  PRIMARY KEY (locus_id, ord)
);

CREATE TABLE IF NOT EXISTS sample (
  id            TEXT PRIMARY KEY,
  cond_group    TEXT NOT NULL,
  collected     INTEGER NOT NULL    -- 0 = 该样本未采集（任何 junction 都无行）
);

CREATE TABLE IF NOT EXISTS junction (
  id            TEXT PRIMARY KEY,
  locus_id      TEXT NOT NULL REFERENCES locus(id),
  from_ord      INTEGER NOT NULL,
  to_ord        INTEGER NOT NULL,
  low_mappability INTEGER NOT NULL DEFAULT 0,
  note          TEXT
);

-- junction_count 行存在且 cnt=0：已观测、计数为零
-- junction_count 行不存在（或该样本 sample.collected=0）：未采集
CREATE TABLE IF NOT EXISTS junction_count (
  junction_id   TEXT NOT NULL REFERENCES junction(id),
  sample_id     TEXT NOT NULL REFERENCES sample(id),
  cnt           INTEGER NOT NULL,   -- 显式允许 0
  PRIMARY KEY (junction_id, sample_id)
);

CREATE TABLE IF NOT EXISTS run_record (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  created_at    TEXT NOT NULL,
  locus_id      TEXT NOT NULL,
  strand_used   TEXT NOT NULL,
  excluded_ids  TEXT NOT NULL,
  locked_paths  TEXT NOT NULL,
  result_json   TEXT NOT NULL,
  fixture_sha256 TEXT NOT NULL
);
