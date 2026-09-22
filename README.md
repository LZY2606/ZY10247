# 剪接路径裁决台（Splice Path Adjudication Console）

从固定 fixture 构建 RNA 剪接有向图，枚举受支持的转录本候选路径，并在边流量无法唯一
分解时**保留所有并列的稀疏路径集**——而不是按字典序选一个再声称唯一。页面以 SVG 展示
exon 节点、splice junction 边、样本支持、路径候选和残余边流量；支持链方向更正、排除低可
比对性边、锁定已知路径；运行结果持久化到 SQLite，可导出 JSON 并在运行间直接差分。

技术栈：Java 17 + Spring Boot 3.5 + Spring JDBC + SQLite（`sqlite-jdbc`）+ 原生 SVG/JS 页面 + JUnit 5。

## 安装与演示

```bash
mvn -q -DskipTests package

mvn -q test && mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=5587
# 浏览器访问 http://127.0.0.1:5587 ，页面标题为“剪接路径裁决台”
```

SQLite 数据库默认位于 `data/splice.db`（可用环境变量 `SPLICE_DB=/path/to.db` 覆盖）。
数据库为空时应用启动会自动从 classpath 固定 fixture 导入。

## 数据口径（重要）

- **统一半开坐标**：exon、区域与内含子区间一律为半开 `[start,end)`，长度 = `end-start`。
- **反向链必须转换坐标语义**：不是把 exon 数组倒序。对区域 `[R0,R1)`，基因组位置 `g` 在
  反向链转录顺序上的位置为 `t = R1 - g`，半开区间映射为 `[R1-end, R1-start)`，长度不变；
  junction 内含子端点必须交换（转录上游 exon 在基因组上坐标更大）。代码见
  `src/main/java/com/splice/domain/Coordinates.java` 与 `graph/GraphBuilder.java`。
- **零计数 ≠ 未采集**：
  - `junction_count` 表中**行存在且 `cnt=0`** 表示该样本已观测、跨连接读数为零（显式零）。
  - 样本 `sample.collected=0`（fixture 中的 `S4`）表示未采集，**不写任何计数行**，
    图 JSON 中该样本对应值为 `null`；聚合观测与分组覆盖都会跳过未采集样本。
- **非唯一分解保留**：多个路径集只要重建出完全相同的边流量（`flowSignature` 相同）且
  同为最小 L1 误差、最稀疏，就一并返回并标注“非唯一分解”。不允许字典序二选一。
- **残余边流量**：每条边给出 `observed`（已采集样本聚合）、`reconstructed`（路径流之和）、
  `error=|obs-rec|`。排除边不参与重建（`reconstructed=0`）。
- **分组覆盖（min-edge 口径）**：某已采集样本被“完整解释”当且仅当存在一条被选用路径，
  其包含该样本**全部**正计数 junction（即该路径上最弱的边也支持该样本）。组覆盖率 = 组内
  被完整解释、且至少有一条正计数边的样本比例。边流量相同的并列分解在该口径下可能给出不同
  覆盖（这正是保留它们的生物学理由）。

## 固定 fixture

`src/main/resources/fixtures/splice-fixture.json`（SHA-256 指纹见页面顶部与 `/api/fixture`）。

- `GENE-D`（chr1:`[1000,1900)`，正链）：5 个 exon、7 条 junction，其中 `j24` 为显式零边。
  聚合边流量同时等于两组**都是 2 条转录本、L1 误差为 0** 的稀疏分解：
  - `{ B = 1-2-3-4-5, D = 1-3-5 }`
  - `{ C = 1-3-4-5, E = 1-2-3-5 }`

  两组的 `flowSignature` 完全相同，但 `{B,D}` 完整解释两个 control 样本（覆盖 100%），
  `{C,E}` 不能完整解释任一 control 样本（覆盖 0%）。
- `GENE-R`（chr2:`[5000,6000)`，反链）：4 个 exon、3 条 junction。`k2r` 为低可比对性边，
  三个已采集样本均为显式 0；唯一线性路径 `1-2-3-4` 会穿过零边，故默认解的零边残差为 4，
  排除 `k2r` 后无完整路径、正边残余合计 8。
- 样本：`S1/S2`（control，已采集），`S3`（treatment，已采集但全零），
  `S4`（treatment，**未采集**）。

## 页面操作

- 顶部选择位点、更正链方向（`+`/`-`），勾选要排除的边（低可比对性边已标注）。
- 在“锁定已知路径”中按 exon 转录顺序 ord 输入，如 `1,3,4,5`；多条用分号分隔。
  锁定路径若使用被排除边会返回 400。
- “求解候选路径集”展示 SVG 图、样本支持矩阵、全部候选路径、并列路径集解（L1、拷贝数、
  分组覆盖、min-edge 上限）与逐边残差。
- 勾选“保存为运行记录”后结果写入 SQLite；底部可导出单条运行 JSON、选择两个运行做差分。
- “清空数据库并重新导入 fixture”会清空所有表并按固定 fixture 重放（运行记录也会清空，
  需要留档请先用导出链接下载 JSON）。

## HTTP 接口

- `GET /api/loci`、`GET /api/samples`、`GET /api/fixture`
- `GET /api/loci/{id}/graph?strand=-&exclude=k2r`
- `POST /api/solve`：body `{locusId, strand(+|-|null), exclude[], lockPaths[[ord...]], persist}`
- `GET /api/runs?locusId=`、`GET /api/runs/{id}`（完整结果 JSON，可导出）、`DELETE /api/runs/{id}`
- `GET /api/runs/{a}/diff/{b}`：链/排除边/锁定是否变化、两组解与边流量签名是否变化
- `POST /api/admin/reimport`：清空并按固定 fixture 重新导入

## 清空数据库后复核（重放）

```bash
# 1) 删除 SQLite 文件后重启，DataSeeder 会自动重新导入
find data -name 'splice.db' -delete
mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=5587
# 2) 或在运行中的实例上调用 /api/admin/reimport（等价于清空全部表后重新播种）
curl -s -X POST http://127.0.0.1:5587/api/admin/reimport
```

导入内容与 fixture SHA-256 一并记录在每条运行记录中，可据此核对结果是否可复现。

## 自动化测试

`src/test/java` 下 12 个用例覆盖：半开区间长度保持、反向链坐标镜像（非数组倒序）、
内含子端点交换、零计数与未采集区分、菱形并列分解保留与分组覆盖差异、链更正重映射、
排除边残差、锁定路径选择/拒绝、HTTP 页面标题与求解往返、运行差分、清空重导。

```bash
mvn -q test
```
