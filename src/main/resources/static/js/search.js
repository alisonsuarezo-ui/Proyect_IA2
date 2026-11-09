(async () => {
  const params = new URLSearchParams(location.search);
  const examples = [
    '¿Qué tareas están pendientes?',
    '¿Qué actividades están relacionadas con el frontend?',
    '¿Cuáles son las tareas más urgentes?',
    '¿Qué actividades están atrasadas?',
    'Resume el progreso de este proyecto',
    '¿Qué tareas tienen descripciones similares?',
  ];
  const scope = document.getElementById('scope');
  const mode = document.getElementById('mode');
  const question = document.getElementById('question');

  document.getElementById('examples').innerHTML = examples
    .map((q) => `<button type="button" class="chip">${TF.esc(q)}</button>`).join('');
  document.getElementById('examples').addEventListener('click', (e) => {
    if (!e.target.classList.contains('chip')) return;
    question.value = e.target.textContent;
    mode.value = 'ask';
    run();
  });

  /** Convierte referencias #ID de la respuesta en enlaces al detalle de la tarea. */
  function linkify(text) {
    return TF.esc(text)
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      // (?<!&) evita romper entidades HTML numéricas como &#39; generadas por TF.esc
      .replace(/(?<!&)#(\d+)/g, '<a href="/task.html?id=$1">#$1</a>');
  }

  function renderSources(list, title) {
    document.getElementById('sources-card').classList.remove('hidden');
    document.getElementById('sources-title').textContent = title;
    document.getElementById('sources').innerHTML = list.length ? list.map((r) => `
      <div class="result-item">
        <div style="min-width:0">
          <a class="title-link" href="/task.html?id=${r.task.id}">#${r.task.id} ${TF.esc(r.task.title)}</a>
          <div class="small muted">${TF.esc(r.task.projectName)} · ${r.task.assignee ? TF.esc(r.task.assignee.fullName) : 'Sin asignar'}</div>
          <div style="display:flex;gap:.35rem;margin-top:.35rem;flex-wrap:wrap">${TF.statusBadge(r.task.status)} ${TF.priorityBadge(r.task.priority)}
            ${r.task.dueDate ? `<span class="badge">${TF.fmtDate(r.task.dueDate)}</span>` : ''}
            ${r.task.overdue ? '<span class="badge badge-overdue">Atrasada</span>' : ''}</div>
        </div>
        <span class="score">${r.score.toFixed(3)}</span>
      </div>`).join('') : '<div class="empty">No se encontraron tareas relevantes para la consulta</div>';
  }

  async function run() {
    const q = question.value.trim();
    if (!q) { question.focus(); return; }
    const btn = document.getElementById('ask-btn');
    TF.setLoading(btn, true, mode.value === 'ask' ? 'Pensando...' : 'Buscando...');
    document.getElementById('answer-card').classList.add('hidden');
    document.getElementById('sources-card').classList.add('hidden');
    try {
      if (mode.value === 'ask') {
        const res = await TF.api.post('/api/ai/ask', { question: q, projectId: scope.value ? Number(scope.value) : null });
        document.getElementById('answer-card').classList.remove('hidden');
        document.getElementById('answer').innerHTML = linkify(res.answer || '');
        document.getElementById('answer-meta').textContent = `${res.provider} · ${res.model} · ${res.contextTasks} tarea(s) recuperadas`;
        renderSources(res.sources, 'Tareas usadas como contexto');
      } else {
        const list = await TF.api.get('/api/ai/search' + TF.qs({ q, projectId: scope.value, limit: 15 }));
        renderSources(list, 'Resultados de búsqueda semántica');
      }
    } catch (err) {
      document.getElementById('answer-card').classList.remove('hidden');
      document.getElementById('answer-meta').textContent = '';
      document.getElementById('answer').innerHTML = `<div class="alert ${err.code === 'AI_NOT_CONFIGURED' ? 'alert-warning' : 'alert-error'}" style="margin:0">${TF.esc(err.message)}</div>`;
    } finally {
      TF.setLoading(btn, false);
    }
  }

  document.getElementById('ask-form').addEventListener('submit', (e) => { e.preventDefault(); run(); });

  try {
    const [projects, status] = await Promise.all([TF.api.get('/api/projects'), TF.api.get('/api/ai/status')]);
    scope.innerHTML += projects.map((p) => `<option value="${p.id}">${TF.esc(p.name)}</option>`).join('');
    if (params.get('projectId')) {
      scope.value = params.get('projectId');
      question.value = 'Resume el progreso de este proyecto';
    }
    const cls = status.configured && status.qdrantReachable ? 'alert-info' : 'alert-warning';
    document.getElementById('ai-status').innerHTML = `<div class="alert ${cls}">
      <strong>Estado IA:</strong> ${TF.esc(status.message)}
      <span class="small">(proveedor: ${TF.esc(status.provider)} · chat: ${TF.esc(status.chatModel)} · embeddings: ${TF.esc(status.embeddingModel)}
      · Qdrant: ${status.qdrantReachable ? 'conectado' : 'sin conexión'} · ${status.indexedTasks}/${status.totalTasks} tareas indexadas)</span></div>`;
  } catch (err) { TF.handleError(err); }
})();
