(async () => {
  const params = new URLSearchParams(location.search);
  const rows = document.getElementById('task-rows');
  const f = {
    q: document.getElementById('f-q'),
    project: document.getElementById('f-project'),
    status: document.getElementById('f-status'),
    priority: document.getElementById('f-priority'),
    assignee: document.getElementById('f-assignee'),
    overdue: document.getElementById('f-overdue'),
  };
  let tasks = [];

  async function load() {
    try {
      tasks = await TF.api.get('/api/tasks' + TF.qs({
        q: f.q.value.trim(),
        projectId: f.project.value,
        status: f.status.value,
        priority: f.priority.value,
        assigneeId: f.assignee.value,
        overdue: f.overdue.checked ? 'true' : '',
      }));
      render();
    } catch (err) { TF.handleError(err); }
  }

  function render() {
    document.getElementById('task-count').textContent = `${tasks.length} tarea(s)`;
    if (!tasks.length) {
      rows.innerHTML = '<tr><td colspan="7" class="empty">No hay tareas que coincidan con los filtros</td></tr>';
      return;
    }
    rows.innerHTML = tasks.map((t) => `<tr>
      <td><a class="title-link" href="/task.html?id=${t.id}">${TF.esc(t.title)}</a>
        <div class="small muted">#${t.id}</div></td>
      <td class="hide-sm">${TF.esc(t.projectName)}</td>
      <td>
        <select class="status-select" data-id="${t.id}" aria-label="Cambiar estado" style="padding:.25rem .4rem;font-size:.82rem;width:auto">
          ${Object.entries(TF.STATUS_LABEL).map(([k, v]) => `<option value="${k}" ${k === t.status ? 'selected' : ''}>${v}</option>`).join('')}
        </select>
      </td>
      <td>${TF.priorityBadge(t.priority)}</td>
      <td class="hide-sm">${t.assignee ? TF.esc(t.assignee.fullName) : '<span class="muted">Sin asignar</span>'}</td>
      <td>${TF.dueCell(t)}</td>
      <td><div class="actions">
        <button class="btn btn-sm btn-secondary" data-edit="${t.id}">Editar</button>
        <button class="btn btn-sm btn-danger" data-delete="${t.id}">Eliminar</button>
      </div></td>
    </tr>`).join('');
  }

  rows.addEventListener('change', async (e) => {
    if (!e.target.classList.contains('status-select')) return;
    try {
      await TF.api.patch(`/api/tasks/${e.target.dataset.id}/status`, { status: e.target.value });
      TF.toast('Estado actualizado', 'success');
      load();
    } catch (err) { TF.handleError(err); load(); }
  });

  rows.addEventListener('click', async (e) => {
    const editId = e.target.dataset.edit;
    const delId = e.target.dataset.delete;
    if (editId) TaskForm.openEdit(tasks.find((t) => t.id === Number(editId)));
    if (delId) {
      const t = tasks.find((x) => x.id === Number(delId));
      if (!TF.confirmAction(`¿Eliminar la tarea "${t.title}"?`)) return;
      try {
        await TF.api.del('/api/tasks/' + delId);
        TF.toast('Tarea eliminada', 'success');
        load();
      } catch (err) { TF.handleError(err); }
    }
  });

  let debounce;
  f.q.addEventListener('input', () => { clearTimeout(debounce); debounce = setTimeout(load, 300); });
  [f.project, f.status, f.priority, f.assignee, f.overdue].forEach((el) => el.addEventListener('change', load));
  document.getElementById('new-task-btn').addEventListener('click', () => TaskForm.openCreate(f.project.value ? Number(f.project.value) : null));

  try {
    const [projects, users] = await Promise.all([TaskForm.init(load), TF.api.get('/api/users')]);
    f.project.innerHTML += projects.map((p) => `<option value="${p.id}">${TF.esc(p.name)}</option>`).join('');
    f.assignee.innerHTML += users.map((u) => `<option value="${u.id}">${TF.esc(u.fullName)}</option>`).join('');
    if (params.get('projectId')) {
      f.project.value = params.get('projectId');
      const p = projects.find((x) => x.id === Number(params.get('projectId')));
      if (p) document.getElementById('tasks-subtitle').textContent = 'Tareas del proyecto ' + p.name;
    }
    if (params.get('status')) f.status.value = params.get('status');
    if (params.get('overdue')) f.overdue.checked = true;
    await load();
    if (params.get('new')) TaskForm.openCreate(f.project.value ? Number(f.project.value) : null);
  } catch (err) { TF.handleError(err); }
})();
