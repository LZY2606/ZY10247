'use strict';

const state = {
  loci: [],
  current: null,
  solutions: [],
  candidates: []
};

const $ = (id) => document.getElementById(id);

async function api(url, opts) {
  const res = await fetch(url, opts);
  if (!res.ok) {
    const t = await res.text();
    throw new Error(t);
  }
  return res.json();
}

function cell(v) {
  if (v === null || v === undefined) return '<td class="null">未采集</td>';
  if (v === 0) return '<td class="num zero">0</td>';
  return `<td class="num">${v}</td>`;
}

function edgeClass(e) {
  if (e.excluded) return 'excluded';
  if (e.demand > 0) return 'supported';
  if (e.lowMappability) return 'lowmap';
  return 'zero';
}

async function init() {
  const info = await api('/api/fixture');
  $('fixtureSha').textContent = 'fixture sha256: ' + info.sha256.slice(0, 16) + '…';
  state.loci = await api('/api/loci');
  const sel = $('locusSelect');
  sel.innerHTML = state.loci.map(l => `<option value="${l.id}">${l.id} (${l.chromosome}:${l.regionStart}-${l.regionEnd}, ${l.strand})</option>`).join('');
  sel.addEventListener('change', () => solve(false));
  $('strandSelect').addEventListener('change', () => solve(false));
  $('solveBtn').addEventListener('click', () => solve(true));
  $('clearLock').addEventListener('click', () => { $('lockInput').value = ''; solve(false); });
  $('reimportBtn').addEventListener('click', reimport);
  $('refreshRuns').addEventListener('click', loadRuns);
  $('diffBtn').addEventListener('click', runDiff);
  await solve(true);
  await loadRuns();
}

function currentExcluded() {
  return [...document.querySelectorAll('#edgeToggles input:checked')].map(i => i.value);
}

function lockedPaths() {
  const raw = $('lockInput').value.trim();
  if (!raw) return [];
  return raw.split(';').map(part => part.split(',').map(x => parseInt(x.trim(), 10)).filter(x => !Number.isNaN(x)));
}

async function solve(persist) {
  const locusId = $('locusSelect').value;
  const strand = $('strandSelect').value;
  const body = {
    locusId,
    strand: strand || null,
    exclude: currentExcluded(),
    lockPaths: lockedPaths(),
    persist: persist && $('persistRun').checked
  };
  try {
    const data = await api('/api/solve', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    state.current = data;
    state.solutions = data.solutions || [];
    renderEdgeToggles(data);
    renderSvg(data);
    renderSupport(data);
    renderCandidates(data);
    renderSolutions(data);
    renderResiduals(data);
    if (data.runId) await loadRuns();
  } catch (e) {
    alert('求解失败：' + e.message);
  }
}

function renderEdgeToggles(data) {
  const box = $('edgeToggles');
  const checked = new Set(data.excludedIds || []);
  box.innerHTML = data.edges.map(e => {
    const mark = e.lowMappability ? ' [低可比对性]' : '';
    return `<label class="persist"><input type="checkbox" value="${e.id}" ${checked.has(e.id) ? 'checked' : ''}/>${e.id} ${e.fromOrd}→${e.to}${mark}</label>`;
  }).join(' ');
  box.querySelectorAll('input').forEach(i => i.addEventListener('change', () => solve(false)));
}

function renderSvg(data) {
  const n = data.nodes.length;
  const w = Math.max(720, n * 150 + 60);
  const h = 230;
  const yExon = 150;
  const x0 = 40;
  const gap = (w - 80) / n;
  const positions = new Map();
  let exonRects = '';
  data.nodes.forEach((node, i) => {
    const x = x0 + i * gap;
    positions.set(node.ord, x);
    exonRects += `
      <rect x="${x}" y="${yExon}" width="${Math.min(90, gap - 30)}" height="34" rx="4"
            fill="#e0e7ff" stroke="#3730a3" />
      <text x="${x + (Math.min(90, gap - 30)) / 2}" y="${yExon + 21}" text-anchor="middle"
            font-size="12" fill="#1e1b4b">${node.name}</text>
      <text x="${x}" y="${yExon - 8}" font-size="10" fill="#475569">
        ord ${node.ord} · t[${node.tStart},${node.tEnd}) · g[${node.genStart},${node.genEnd})
      </text>`;
  });

  const lane = {};
  let laneCount = 0;
  data.edges.forEach(e => {
    const span = e.toOrd - e.fromOrd;
    if (lane[span] === undefined) { lane[span] = span % 2 === 0 ? 80 - ((laneCount % 3) * 18) : 60 + ((laneCount % 3) * 18); laneCount++; }
  });

  const edgeSvg = data.edges.map(e => {
    const x1 = positions.get(e.fromOrd) + 30;
    const x2 = positions.get(e.toOrd) + 30;
    const span = e.toOrd - e.fromOrd;
    const yUp = lane[span] ?? 70;
    const cy = yExon - Math.max(20, 30 + span * 22) + (yUp - 60);
    const color = {excluded: '#b91c1c', supported: '#0f766e', zero: '#cbd2d9', lowmap: '#7c3aed'}[edgeClass(e)];
    const dash = e.excluded ? 'stroke-dasharray="6 4"' : '';
    const label = `${e.id} obs=${e.demand} intron g[${e.intronGenStart},${e.intronGenEnd}) t[${e.intronTStart},${e.intronTEnd})`;
    return `
      <path d="M ${x1} ${yExon} Q ${(x1 + x2) / 2} ${cy}, ${x2} ${yExon}"
            fill="none" stroke="${color}" stroke-width="2" ${dash}>
        <title>${label}${e.note ? '\\n' + e.note : ''}</title>
      </path>
      <text x="${(x1 + x2) / 2}" y="${cy - 4}" text-anchor="middle" font-size="10.5" fill="${color}">${e.id}=${e.demand}</text>`;
  }).join('');

  $('graphSvg').innerHTML = `
    <svg width="${w}" height="${h}" xmlns="http://www.w3.org/2000/svg">
      <text x="10" y="20" font-size="11" fill="#334155">
        ${data.locusId} ${data.chromosome}:[${data.regionStart},${data.regionEnd}) 声明链 ${data.declaredStrand} → 生效链 ${data.effectiveStrand}
      </text>
      ${exonRects}
      ${edgeSvg}
    </svg>`;
}

function renderSupport(data) {
  const sampleIds = data.samples.map(s => s.id);
  let html = `<tr><th>junction</th><th>方向</th><th>内含子基因组 [start,end)</th>${sampleIds.map(id => `<th>${id}</th>`).join('')}<th>聚合观测</th><th>状态</th></tr>`;
  data.edges.forEach(e => {
    html += `<tr><td>${e.id}</td><td>${e.fromOrd}→${e.toOrd}</td><td>[${e.intronGenStart},${e.intronGenEnd})</td>`;
    data.samples.forEach(s => {
      const v = e.perSample[s.id];
      html += cell(s.collected ? v : null);
    });
    html += `<td class="num">${e.demand}</td>`;
    const st = e.excluded ? '已排除' : (e.lowMappability ? '低可比对性' : (e.demand > 0 ? '有支持' : '显式零'));
    html += `<td>${st}</td></tr>`;
  });
  let sampleRow = `<tr><th colspan="3">样本组别/采集</th>`;
  data.samples.forEach(s => { sampleRow += `<th class="small">${s.group}<br/>${s.collected ? '已采集' : '未采集'}</th>`; });
  sampleRow += '<th colspan="2"></th></tr>';
  $('supportTable').innerHTML = sampleRow + html;
}

function renderCandidates(data) {
  const nodes = new Map(data.nodes.map(n => [n.ord, n.name]));
  const all = [];
  const seen = new Set();
  const walk = (graph, node, end, stack, edgeStack, out) => {
    if (node === end) { out.push({nodes: [...stack], edges: [...edgeStack]}); return; }
    graph.filter(e => e.fromOrd === node && !e.excluded && !stack.includes(e.toOrd))
      .forEach(e => { stack.push(e.toOrd); edgeStack.push(e.id); walk(graph, e.toOrd, end, stack, edgeStack, out); edgeStack.pop(); stack.pop(); });
  };
  walk(data.edges, 1, data.nodes.length, [1], [], all);
  state.candidates = all;
  let html = '<tr><th>#</th><th>exon 路径（转录顺序）</th><th>junction 边</th></tr>';
  all.forEach((p, i) => {
    html += `<tr><td>${i + 1}</td><td>${p.nodes.map(o => `${o}:${nodes.get(o)}`).join(' → ')}</td><td>${p.edges.join(', ')}</td></tr>`;
  });
  $('candidateTable').innerHTML = html;
}

function renderSolutions(data) {
  const sol = data.solutions || [];
  const signatures = new Map();
  sol.forEach(s => signatures.set(s.flowSignature, (signatures.get(s.flowSignature) || 0) + 1));
  if (!sol.length) {
    $('solutions').innerHTML = '<p class="small">没有可重建的路径（所有边可能都被排除）。</p>';
    return;
  }
  $('solutions').innerHTML = sol.map((s, i) => {
    const ties = signatures.get(s.flowSignature) > 1
      ? `<span class="badge tie">非唯一分解：同边流量另有并列路径集</span>` : '';
    const rows = Object.entries(s.assignment.copies).map(([path, copies]) => {
      const groupInfo = Object.entries(s.pathGroupCopies || {})
        .filter(([k]) => k.startsWith(path + '@'))
        .map(([k, v]) => `${k.split('@')[1]}≤${v}`).join(', ');
      return `<tr><td>${path}</td><td class="num">${copies}</td><td class="small">${groupInfo || '—'}</td></tr>`;
    }).join('');
    const cov = Object.entries((s.coverage && s.coverage.groupCoverage) || {})
      .map(([g, v]) => `${g}: ${(v * 100).toFixed(0)}%`).join(' · ');
    return `
      <div class="solution optimal">
        <div class="head">解 ${i + 1} · L1 误差 ${s.l1Error} · 路径拷贝 ${s.pathCount}
          <span class="badge">optimal</span>${ties}
          <span class="small">flowSignature: ${s.flowSignature}</span>
        </div>
        <table><tr><th>路径 (exon ord)</th><th>拷贝数</th><th>各组可支持上限(min-edge)</th></tr>${rows}</table>
        <div class="small" style="margin-top:6px">分组覆盖：${cov || '—'}</div>
      </div>`;
  }).join('');
}

function renderResiduals(data) {
  const first = (data.solutions || [])[0];
  if (!first) {
    $('residualTable').innerHTML = '<tr><td class="small">无</td></tr>';
    return;
  }
  let html = '<tr><th>边</th><th>方向</th><th>观测聚合</th><th>重建流量</th><th>误差|obs-rec|</th><th>排除</th><th>说明</th></tr>';
  first.residuals.forEach(r => {
    html += `<tr>
      <td>${r.edgeId}</td><td>${r.fromOrd}→${r.toOrd}</td>
      <td class="num">${r.observed}</td><td class="num">${r.reconstructed}</td>
      <td class="num">${r.error}</td><td>${r.excluded ? '是' : ''}</td>
      <td class="small">${r.lowMappability ? '低可比对性' : ''}</td></tr>`;
  });
  $('residualTable').innerHTML = html;
}

async function loadRuns() {
  const runs = await api('/api/runs');
  let html = '<tr><th>id</th><th>时间</th><th>位点</th><th>生效链</th><th>排除边</th><th>锁定路径</th><th>fixture</th><th>操作</th></tr>';
  runs.forEach(r => {
    html += `<tr>
      <td>${r.id}</td><td>${r.created_at}</td><td>${r.locus_id}</td><td>${r.strand_used}</td>
      <td>${r.excluded_ids}</td><td>${r.locked_paths}</td>
      <td class="small">${String(r.fixture_sha256).slice(0, 10)}…</td>
      <td><a href="/api/runs/${r.id}" target="_blank">导出 JSON</a></td></tr>`;
  });
  $('runsTable').innerHTML = html;
  const opts = runs.map(r => `<option value="${r.id}">#${r.id} ${r.locus_id} ${r.strand_used}</option>`).join('');
  $('runA').innerHTML = opts;
  $('runB').innerHTML = opts;
  if (runs.length > 1) $('runB').selectedIndex = 1;
}

async function runDiff() {
  const a = $('runA').value;
  const b = $('runB').value;
  if (!a || !b) { alert('请选择两个运行'); return; }
  const d = await api(`/api/runs/${a}/diff/${b}`);
  const summarize = (sols) => (sols || []).map(s =>
    `  - flow=${s.flowSignature} L1=${s.l1Error} copies=${s.pathCount} paths={${Object.keys(s.assignment.copies).join(', ')}}`).join('\n');
  $('diffOut').textContent =
    `运行 #${a} vs #${b}\n` +
    `位点变化: ${d.locusChanged}  链变化: ${d.strandChanged}  排除边变化: ${d.excludedChanged}  锁定变化: ${d.lockedChanged}\n` +
    `主解边流量签名变化: ${d.flowSignatureChanged}\n\n` +
    `A 解:\n${summarize(d.solutionsA)}\n\nB 解:\n${summarize(d.solutionsB)}`;
}

async function reimport() {
  if (!confirm('将清空 locus/exon/junction/count 与全部运行记录，并用固定 fixture 重新导入。继续？')) return;
  const r = await api('/api/admin/reimport', { method: 'POST' });
  alert(`已重新导入 ${r.loci} 个位点。sha256=${r.fixtureSha256}`);
  await solve(true);
  await loadRuns();
}

init().catch(e => console.error(e));
