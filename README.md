# 剪接路径裁决台（Splicing Path Adjudication Console）

本地运行的 RNA 剪接路径裁决工具：从固定 fixture 建立有向剪接图，枚举受支持的转录本
候选路径，求解稀疏非负路径分解，并**保留边流量相同但路径构成不同的并列分解**。

- 技术栈：Java 17 + Spring Boot 3.5 + SQLite（xerial JDBC）+ 原生 SVG
- 页面：exon 节点、splice junction 边、样本计数矩阵、路径候选、并列稀疏分解、
  每条边的重建误差、对条件分组的覆盖、运行记录与运行间差分
- 求解：精确有理数（无浮点容差）的 Gauss–Jordan 线性求解 + 最小支撑路径集枚举

## 快速开始

```bash
# 仅打包
mvn -q -DskipTests package

# 自动化测试 + 启动演示（端口 5587）
mvn -q test
mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=5587
```

浏览器访问 <http://127.0.0.1:5587>，页面标题为“剪接路径裁决台”。

SQLite 数据库默认位于 `./data/splice-bench.db`，可用环境变量 `SPLICE_DB` 覆盖：

```bash
SPLICE_DB=/tmp/bench.db mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=5587
```

## 数据口径（重要）

### 半开坐标

- exon 与 intron 一律使用半开区间 `[start, end)`，恒有 `start < end`，长度为 `end - start`。
- 坐标方向始终是**基因组正方向**；链方向单独用 `+ / -` 表达。
- junction 的内含子以升序半开区间 `[genomicStart, genomicEnd)` 存储。
  - 正链：供体边界 = `genomicStart`，受体边界 = `genomicEnd`；
  - 负链：转录方向与基因组坐标相反，供体边界 = `genomicEnd`，受体边界 = `genomicStart`。

### 负链更正必须转换坐标语义

把链从 `-` 更正为 `+`（或反向）时，在基因跨度 `[spanStart,spanEnd)` 内做镜像：

```
c'                = spanStart + spanEnd - c
[a,b)  -> [spanStart+spanEnd-b, spanStart+spanEnd-a)
```

镜像后区间仍升序、长度不变，且供体/受体转录语义互换。仅把 exon 数组倒序
**不**做坐标转换是错误实现，测试 `FixtureGraphTest.flippingIsNotArrayReversal...`
专门防范这种情况。

### 零计数 ≠ 未采集

- `count = 0`：该样本在该 junction **采集到真实零读段**（参与汇总，计入已采集单元数）；
- `count = NULL` + `reason`：该位点/样本**未采集**（不参与汇总）。
- fixture 中 `TRT-2` 文库失败，全部 junction 为 NULL（`library_failed`）；
  另有 `j3` 对 `CTRL-3` 未采集（`not_measured`），同一行仍保留其他样本的真实 0。

## Fixture 与“不可唯一分解”

基因 `DIAMOND1`（chr1，负链，跨度 `[200,4900)`）为“串行双菱形”：

```
E1 -> [ E2 / E3 ] -> E4 -> [ E5 / E6 ] -> E7
```

四条受支持的全长候选转录本：

| 路径 | exon 序列 | junction |
|---|---|---|
| P1 | E1-E2-E4-E5-E7 | j1,j3,j5,j7 |
| P2 | E1-E2-E4-E6-E7 | j1,j3,j6,j8 |
| P3 | E1-E3-E4-E5-E7 | j2,j4,j5,j7 |
| P4 | E1-E3-E4-E6-E7 | j2,j4,j6,j8 |

两个菱形“中间连接边”的联合流量不可由 junction 计数直接区分，因此池化流量存在
一个自由度。求解器枚举最小支撑（最稀疏）非负解，会同时返回两组**路径构成不同、
边流量完全相同**的分解，例如默认 fixture 池化结果：

- 解 A：`P1=50, P2=130, P3=20`
- 解 B：`P1=70, P2=110, P4=20`

两组解对每条边的重建流量完全一致（边流量指纹相同、残余全为 0）。系统**不会**
用字典序挑一个并声称唯一；结果中的 `tied=true` 与并列指纹用于显式表达不可唯一。
对照组 CTRL 同样并列；处理组 TRT 的计数只支持 P1/P2，因此唯一，便于对比
“条件分组覆盖”的差异。

## 页面操作

- **更正链方向**：下拉选择 `+ / -`，图与坐标立即按镜像重算。
- **排除低可比对性边**：按 mappability 阈值（junction `j3` mappability=0.62，
  默认阈值示例 0.7 会排除它）或逐条手工勾选；被排除边不参与拟合/覆盖，仍给出
  预测重建流量并标记“已排除”，残余单独可见。
- **锁定已知路径**：为某条候选路径填写整数或 `a/b` 精确流量，剩余流量重新做
  稀疏分解；例如池化锁定 `P2=110` 后并列被打破，只剩唯一分解。
- **运行记录与差分**：每次求解都落库；选择两次运行可比较设置、各 scope 的并列性、
  边流量指纹与覆盖率。
- **导出**：`导出 JSONL` 下载每次运行的完整 JSON Lines 记录（含设置与结果）。
- **清空重导入**：`清空并重新导入 fixture` 清空业务表并重新写入固定 fixture
  （运行历史保留），用于复核；也可直接删除 `data/*.db` 后重启，应用会自动建表并播种。

## HTTP API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/graph?strand=+\|-` | 当前剪接图视图（节点/边/路径/计数单元） |
| GET | `/api/graph.svg?strand=...` | 服务端渲染的剪接图 SVG |
| POST | `/api/runs` | 创建一次裁决运行并落库 |
| GET | `/api/runs` / `/api/runs/{id}` | 运行摘要 / 完整结果 |
| GET | `/api/runs/{fromId}/diff/{toId}` | 两次运行差分 |
| GET | `/api/export` | 导出 JSONL（`application/x-ndjson`） |
| POST | `/api/admin/reimport` | 清空业务表并重新导入 fixture |

POST `/api/runs` 请求体示例：

```json
{
  "strand": "-",
  "minMappability": 0.7,
  "excludeEdges": ["j3"],
  "locks": { "P2": "110" },
  "note": "排除低可比对性边并锁定 P2"
}
```

精确流量以字符串（整数或 `a/b`）返回，例如 `reconstructedExact`、`residualExact`；
同时给出 double 字段便于页面展示。

## 清空数据库后重新导入复核

```bash
# 方式一：接口
curl -X POST http://127.0.0.1:5587/api/admin/reimport

# 方式二：物理删除后重启（自动建表 + 播种）
find data -name '*.db' -delete
mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=5587
```

## 重放运行记录

1. `mvn -q test && mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=5587`
2. 页面按相同设置再次“求解并记录”，或读取导出的 JSONL 比对 settings/结果；
3. 用两次运行的 id 调用 `/api/runs/{from}/diff/{to}` 做结构化差分。

## 自动化测试

```bash
mvn -q test
```

- `CoordinatesTest`：半开区间、镜像公式、镜像保长、对合性。
- `FixtureGraphTest`：负链供体在高坐标端、翻转后坐标镜像与供受体互换、
  “不能只倒序数组”的防护、4 条候选路径枚举。
- `SolverTest`：池化/对照组的两组并列稀疏解、边流量指纹相同且残余为 0、
  处理组唯一、零与 NULL 的区分、锁定破并列、排除边标记与预测、不可行锁定上报。
- `WebIntegrationTest`：首页标题、图 API、并列结果、排除+锁定、运行差分、
  JSONL 导出、清空重导入全链路。

## 目录结构

```
src/main/java/com/example/splice
├── config/      幂等建表
├── domain/      半开区间/链/exon/junction/计数单元模型 + 坐标镜像
├── fixture/     固定 fixture 与空库播种
├── repo/        SQLite JdbcTemplate 仓储（genes/exons/junctions/samples/counts/runs）
├── solver/      精确分数、有理线性系统、稀疏并列路径分解
├── splice/      剪接图构建（含链翻转）与候选路径 DFS 枚举
├── svg/         服务端 SVG 渲染
└── web/         REST API、页面控制器、编排服务
```
