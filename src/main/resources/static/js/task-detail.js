(async () => {
  const id = new URLSearchParams(location.search).get('id');
  if (!id) { window.location.href = '/tasks.html'; return; }
  let task = null;

  async function loadTask() {
    task = await TF.api.get('/api/tasks/' + id);
    document.title = 'TaskFlow · ' + task.title;
    document.getElementById('t-title').textContent = task.title;
    document.getElementById('t-sub').innerHTML = `#${task.id} · Proyecto <a href="/tasks.html?projectId=${task.projectId}">${TF.esc(task.projectName)}</a>`;
    document.getElementById('t-description').innerHTML = task.description
      ? TF.esc(task.description) : '<span class="muted">Sin descripción</span>';
    document.getElementById('t-details').innerHTML = `
      <dt>Estado</dt><dd>
        <select id="status-select" style="width:auto;padding:.25rem .5rem">
          ${Object.entries(TF.STATUS_LABEL).map(([k, v]) => `<option value="${k}" ${k === task.status ? 'selected' : ''}>${v}</option>`).join('')}
        </select></dd>
      <dt>Prioridad</dt><dd>${TF.priorityBadge(task.priority)}</dd>
      <dt>Fecha límite</dt><dd>${TF.dueCell(task)}</dd>
      <dt>Responsable</dt><dd>${task.assignee ? TF.esc(task.assignee.fullName) : '<span class="muted">Sin asignar</span>'}</dd>
      <dt>Creada por</dt><dd>${TF.esc(task.createdBy.fullName)}</dd>
      <dt>Creada</dt><dd>${TF.fmtDateTime(task.createdAt)}</dd>
      <dt>Actualizada</dt><dd>${TF.fmtDateTime(task.updatedAt)}</dd>
      ${task.completedAt ? `<dt>Completada</dt><dd>${TF.fmtDateTime(task.completedAt)}</dd>` : ''}
      <dt>Vector IA</dt><dd>${task.indexed ? '<span class="badge st-COMPLETADA">Indexada en Qdrant</span>' : '<span class="badge st-PENDIENTE">Pendiente de indexar</span>'}</dd>`;
    document.getElementById('status-select').addEventListener('change', async (e) => {
      try {
        await TF.api.patch(`/api/tasks/${id}/status`, { status: e.target.value });
        TF.toast('Estado actualizado', 'success');
        loadTask();
      } catch (err) { TF.handleError(err); }
    });
    document.getElementById('t-actions').innerHTML = `
      <button class="btn btn-secondary" id="edit-btn">Editar</button>
      <button class="btn btn-danger" id="delete-btn">Eliminar</button>`;
    document.getElementById('edit-btn').onclick = () => TaskForm.openEdit(task);
    document.getElementById('delete-btn').onclick = async () => {
      if (!TF.confirmAction('¿Eliminar esta tarea y sus comentarios?')) return;
      try {
        await TF.api.del('/api/tasks/' + id);
        TF.toast('Tarea eliminada', 'success');
        window.location.href = '/tasks.html';
      } catch (err) { TF.handleError(err); }
    };
  }

  async function loadComments() {
    const comments = await TF.api.get(`/api/tasks/${id}/comments`);
    document.getElementById('c-count').textContent = comments.length;
    const box = document.getElementById('comments');
    box.innerHTML = comments.length ? comments.map((c) => `
      <div class="comment">
        <div class="comment-head">
          <span><span class="avatar" style="display:inline-grid;margin:0 .4rem 0 0;width:26px;height:26px">${TF.initials(c.author.fullName)}</span>
            <strong>${TF.esc(c.author.fullName)}</strong> <span class="muted">· ${TF.fmtDateTime(c.createdAt)}</span></span>
          ${c.canDelete ? `<button class="btn btn-ghost btn-sm" data-del-comment="${c.id}">Eliminar</button>` : ''}
        </div>
        <div class="comment-body">${TF.esc(c.content)}</div>
      </div>`).join('') : '<div class="empty">Sin comentarios todavía</div>';
  }

  async function loadSimilar() {
    const box = document.getElementById('similar');
    box.innerHTML = '<span class="spinner dark"></span>';
    try {
      const list = await TF.api.get(`/api/tasks/${id}/similar?limit=5`);
      box.innerHTML = list.length ? list.map((r) => `
        <div class="result-item">
          <div><a class="title-link" href="/task.html?id=${r.task.id}">${TF.esc(r.task.title)}</a>
            <div class="small muted">${TF.esc(r.task.projectName)} · ${TF.STATUS_LABEL[r.task.status]}</div></div>
          <span class="score" title="Similitud coseno">${r.score.toFixed(2)}</span>
        </div>`).join('') : '<div class="empty small">No se encontraron tareas similares</div>';
    } catch (err) {
      box.innerHTML = `<div class="alert alert-warning small">${TF.esc(err.message)}</div>`;
    }
  }

  document.getElementById('comments').addEventListener('click', async (e) => {
    const cid = e.target.dataset.delComment;
    if (!cid || !TF.confirmAction('¿Eliminar el comentario?')) return;
    try {
      await TF.api.del('/api/comments/' + cid);
      loadComments();
    } catch (err) { TF.handleError(err); }
  });

  document.getElementById('comment-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const content = e.target.content.value.trim();
    if (!content) return;
    const btn = document.getElementById('c-btn');
    TF.setLoading(btn, true, 'Enviando...');
    try {
      await TF.api.post(`/api/tasks/${id}/comments`, { content });
      e.target.reset();
      loadComments();
    } catch (err) { TF.handleError(err); } finally { TF.setLoading(btn, false); }
  });

  try {
    await TaskForm.init(() => { loadTask(); setTimeout(loadSimilar, 2500); });
    await Promise.all([loadTask(), loadComments()]);
    loadSimilar();
  } catch (err) {
    TF.handleError(err);
    document.getElementById('t-title').textContent = 'Tarea no disponible';
  }
})();
