let graph = null;

async function api(path, options) {
  const res = await fetch(path, options);
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) throw new Error((data && data.error) || res.statusText);
  return data;
}

function esc(s) {
  return String(s).replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
}

async function loadGraph() {
  const strand = document.getElementById('strand').value;
  graph = await api('/api/graph' + (strand ? '?strand=' + encodeURIComponent(strand) : ''));
  renderSvg();
  renderExons();
  renderCounts();
  renderControls();
}

function renderSvg() {
  const host = document.getElementById('svgHost');
  const url = '/api/graph.svg?strand=' + encodeURIComponent(graph.effectiveStrand);
  fetch(url).then(r => r.text()).then(svg => { host.innerHTML = svg; });
}

function renderExons() {
  let h = '<tr><th class="lbl">rank(5′→3′)</th><th class="lbl">exon</th><th>contig</th><th>start</th><th>end</th><th>长度</th></tr>';
  for (const n of graph.nodes) {
    h += `<tr><td class="lbl">${n.rank}</td><td class="lbl mono">${esc(n.exonId)}</td><td>${esc(n.contig)}</td><td class="mono">${n.start}</td><td class="mono">${n.end}</td><td>${n.end - n.start}</td></tr>`;
  }
  h += `<tr><td colspan="6" class="muted">基因跨度（半开）：${esc(graph.contig)}:[${graph.spanStart},${graph.spanEnd}) · 有效链 ${graph.effectiveStrand}${graph.strandFlipped ? '（已由存储链 ' + graph.storedStrand + ' 镜像更正）' : ''}</td></tr>`;
  document.getElementById('exonTable').innerHTML = h;
}

function renderCounts() {
  let h = '<tr><th class="lbl">junction</th><th class="lbl">供体→受体</th><th class="lbl">内含子[start,end)</th><th>map</th>';
  for (const s of graph.samples) h += `<th>${esc(s.id)}<br><span class="muted">${esc(s.conditionGroup)}</span></th>`;
  h += '</tr>';
  for (const e of graph.edges) {
    const cells = graph.cells[e.junctionId] || graph.cellsByEdge[e.junctionId];
    h += `<tr><td class="lbl mono">${esc(e.junctionId)}</td><td class="lbl mono">${esc(e.donorExonId)}→${esc(e.acceptorExonId)}</td><td class="lbl mono">[${e.genomicStart},${e.genomicEnd})</td><td>${e.mappability.toFixed(2)}</td>`;
    for (const c of cells) {
      if (!c.measured) h += `<td class="na">N/A</td>`;
      else if (c.count === 0) h += `<td class="zero">0</td>`;
      else h += `<td>${c.count}</td>`;
    }
    h += '</tr>';
  }
  document.getElementById('countTable').innerHTML = h;
}

function renderControls() {
  const box = document.getElementById('edgeToggles');
  box.innerHTML = '';
  for (const e of graph.edges) {
    const lbl = document.createElement('label');
    lbl.style.display = 'inline-flex';
    lbl.style.alignItems = 'center';
    lbl.style.gap = '3px';
    lbl.innerHTML = `<input type="checkbox" class="edgeExcl" value="${e.junctionId}"> <span class="mono">${e.junctionId}</span><span class="muted">(${e.mappability.toFixed(2)})</span>`;
    box.appendChild(lbl);
  }
  const locks = document.getElementById('lockRows');
  locks.innerHTML = '';
  for (const p of graph.paths) {
    const wrap = document.createElement('div');
    wrap.className = 'row';
    wrap.style.marginTop = '4px';
    wrap.innerHTML = `<span class="mono" style="width:34px">${p.id}</span><span class="muted" style="flex:1">${p.exonIds.join('-')}</span><input type="text" class="lockAmt" data-path="${p.id}" placeholder="留空">`;
    locks.appendChild(wrap);
  }
}

function collectRequest() {
  const excludeEdges = [...document.querySelectorAll('.edgeExcl:checked')].map(c => c.value);
  const locks = {};
  for (const inp of document.querySelectorAll('.lockAmt')) {
    const v = inp.value.trim();
    if (v) locks[inp.dataset.path] = v;
  }
  const minMapRaw = document.getElementById('minMap').value;
  return {
    strand: document.getElementById('strand').value,
    excludeEdges,
    minMappability: minMapRaw === '' ? null : Number(minMapRaw),
    locks,
    note: document.getElementById('note').value
  };
}

function renderResult(data) {
  const host = document.getElementById('result');
  let h = '';
  h += '<details open><summary>受支持的转录本候选路径</summary><table><tr><th class="lbl">路径</th><th class="lbl">外显子序列</th><th class="lbl">边序列</th></tr>';
  for (const p of data.graph.paths) {
    h += `<tr><td class="lbl mono">${p.id}</td><td class="lbl">${p.exonIds.join(' → ')}</td><td class="lbl mono">${p.edgeIds.join(', ')}</td></tr>`;
  }
  h += '</table></details>';

  for (const sc of data.scopes) {
    h += `<div class="scope"><div class="row" style="justify-content:space-between"><strong>${esc(sc.scope)}${sc.scope === 'POOLED' ? '（全部样本汇总）' : '（条件分组）'}</strong>`;
    if (sc.infeasible) h += '<span class="pill bad">不可行</span>';
    else if (sc.tied) h += `<span class="pill tied">并列：${sc.solutions.length} 组边流量相同的稀疏分解</span>`;
    else h += '<span class="pill unique">唯一稀疏分解</span>';
    h += '</div>';
    if (!sc.infeasible) {
      for (const sol of sc.solutions) {
        const ws = sol.weights.map(w => `<span class="mono">${w.pathId}=${w.exact}</span>`).join('，');
        h += `<div class="sol">#${sol.index + 1} ${ws}${sol.locked ? ' <span class="muted">(含锁定)</span>' : ''}</div>`;
      }
      h += `<div class="muted mono" style="font-size:11px;word-break:break-all">边流量指纹：${esc(sc.tieFingerprint)}</div>`;
    }
    h += '<table style="margin-top:8px"><tr><th class="lbl">junction</th><th>观测汇总</th><th>已采集/全部</th><th>重建</th><th>残余 |obs−rec|</th><th>状态</th></tr>';
    for (const e of sc.edges) {
      const obs = e.observed === null ? '<span class="na">N/A</span>' : e.observed;
      const resCls = Math.abs(e.residual) > 0 ? 'resid-pos' : '';
      h += `<tr><td class="lbl mono">${esc(e.junctionId)}</td><td>${obs}</td><td>${e.measuredCells}/${e.totalCells}</td><td class="mono">${e.reconstructedExact}</td><td class="mono ${resCls}">${e.residualExact}</td><td>${e.excluded ? '<span class="na">已排除</span>' : ''}</td></tr>`;
    }
    h += '</table>';
    h += `<div class="muted" style="margin-top:6px">条件分组覆盖（1 − Σ残余/Σ观测，排除边不计）：${sc.coverage === null ? 'N/A' : (sc.coverage * 100).toFixed(1) + '%'}；总残余=${sc.totalResidualExact}</div>`;
    h += '</div>';
  }
  host.innerHTML = h;
}

async function solve() {
  const req = collectRequest();
  const data = await api('/api/runs', {
    method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(req)
  });
  renderResult(data);
  await loadRuns(data.id);
}

async function loadRuns(selectId) {
  const runs = await api('/api/runs');
  const list = document.getElementById('runList');
  const f = document.getElementById('fromRun');
  const t = document.getElementById('toRun');
  list.innerHTML = '';
  f.innerHTML = ''; t.innerHTML = '';
  for (const r of runs) {
    const opt = `<option value="${r.id}">#${r.id} ${esc(r.note || r.settings.strand + ' 链')}</option>`;
    f.insertAdjacentHTML('beforeend', opt);
    t.insertAdjacentHTML('beforeend', opt);
    const item = document.createElement('div');
    item.className = 'run-item';
    item.innerHTML = `<span>#${r.id} <span class="muted">${esc(r.createdAt || '')}</span> ${esc(r.note || '')} ${r.lockedPathId ? '🔒' + esc(r.lockedPathId) + '=' + esc(r.lockedAmount) : ''}</span>`;
    const btn = document.createElement('button');
    btn.className = 'ghost'; btn.textContent = '载入';
    btn.onclick = async () => {
      const full = await api('/api/runs/' + r.id);
      renderResult(full);
    };
    item.appendChild(btn);
    list.appendChild(item);
  }
  if (runs.length >= 2) { f.value = runs[runs.length - 2].id; t.value = runs[runs.length - 1].id; }
  if (selectId) t.value = selectId;
}

async function doDiff() {
  const a = document.getElementById('fromRun').value;
  const b = document.getElementById('toRun').value;
  if (!a || !b) return;
  const d = await api(`/api/runs/${a}/diff/${b}`);
  let h = `<div class="${d.settingsChanged ? 'delta changed' : 'delta same'}">设置${d.settingsChanged ? '有变化' : '相同'}</div>`;
  for (const s of d.scopes) {
    h += `<div class="delta ${s.flowChanged ? 'changed' : 'same'}">${esc(s.scope)}：边流量${s.flowChanged ? '改变' : '相同'}；并列 ${s.tiedBefore}→${s.tiedAfter}；覆盖 ${s.coverageBefore === null ? 'N/A' : (s.coverageBefore*100).toFixed(1)+'%'}→${s.coverageAfter === null ? 'N/A' : (s.coverageAfter*100).toFixed(1)+'%'}</div>`;
  }
  document.getElementById('diffOut').innerHTML = h;
}

document.getElementById('solveBtn').onclick = () => solve().catch(e => alert(e.message));
document.getElementById('diffBtn').onclick = () => doDiff().catch(e => alert(e.message));
document.getElementById('strand').onchange = loadGraph;
document.getElementById('resetBtn').onclick = () => { document.querySelectorAll('input').forEach(i => { if (i.type !== 'button') i.value = ''; }); document.querySelectorAll('.edgeExcl').forEach(c => c.checked = false); document.getElementById('strand').value = ''; loadGraph(); };
document.getElementById('reimportBtn').onclick = async () => {
  if (!confirm('确认清空全部数据（含运行历史）并重新导入固定 fixture？')) return;
  const r = await api('/api/admin/reimport', {method: 'POST'});
  alert(`已重新导入：${r.exons} 个 exon，${r.junctions} 条 junction，${r.paths} 条候选路径`);
  await loadGraph(); await loadRuns(); document.getElementById('result').innerHTML=''; document.getElementById('diffOut').innerHTML='';
};

loadGraph()
  .then(() => {
    if (location.hash.includes('autorun')) {
      return solve().then(() => loadRuns());
    }
  })
  .catch(e => alert(e.message));
loadRuns();
