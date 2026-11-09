(() => {
  const me = TF.auth.user || {};
  let projects = [];
  let users = [];
  let editingId = null;

  const grid = document.getElementById('project-grid');
  const form = document.getElementById('project-form');

  function render() {
    const q = document.getElementById('filter-q').value.trim().toLowerCase();
    const list = projects.filter((p) => !q || p.name.toLowerCase().includes(q)
      || (p.description || '').toLowerCase().includes(q));
    if (!list.length) {
      grid.innerHTML = `<div class="card empty" style="grid-column:1/-1">${projects.length
        ? 'Ningún proyecto coincide con la búsqueda' : 'Aún no tienes proyectos. ¡Crea el primero!'}</div>`;
      return;
    }
    grid.innerHTML = list.map((p) => {
      const people = [p.owner, ...p.members];
      return `<div class="card project-card">
        <div style="display:flex;justify-content:space-between;gap:.5rem">
          <h2 style="margin:0">${TF.esc(p.name)}</h2>
          ${p.owner.id === me.id ? '<span class="badge">Propietario</span>' : ''}
        </div>
        <p class="muted small" style="margin:0;min-height:2.5em">${TF.esc(p.description || 'Sin descripción')}</p>
        <div>
          <div style="display:flex;justify-content:space-between" class="small muted">
            <span>${p.completedTasks}/${p.totalTasks} tareas completadas</span><strong>${p.progress}%</strong>
          </div>
          <div class="progress" style="margin-top:.3rem"><span style="width:${p.progress}%"></span></div>
        </div>
        <div class="meta">
          <div class="avatars">${people.slice(0, 6).map((u) => `<span class="avatar" title="${TF.esc(u.fullName)}">${TF.initials(u.fullName)}</span>`).join('')}</div>
          <span class="small muted">${people.length} participante(s)</span>
        </div>
        <div style="display:flex;gap:.4rem;flex-wrap:wrap">
          <a class="btn btn-sm" href="/tasks.html?projectId=${p.id}">Ver tareas</a>
          <a class="btn btn-sm btn-secondary" href="/search.html?projectId=${p.id}">✨ Resumen IA</a>
          ${p.canManage ? `
            <button class="btn btn-sm btn-secondary" data-edit="${p.id}">Editar</button>
            <button class="btn btn-sm btn-danger" data-delete="${p.id}">Eliminar</button>` : ''}
        </div>
      </div>`;
    }).join('');
  }

  async function load() {
    try {
      [projects, users] = await Promise.all([TF.api.get('/api/projects'), TF.api.get('/api/users')]);
      render();
    } catch (err) { TF.handleError(err); }
  }

  function openForm(project) {
    editingId = project ? project.id : null;
    form.reset();
    form.querySelectorAll('.field-error').forEach((e) => e.remove());
    document.getElementById('project-modal-title').textContent = project ? 'Editar proyecto' : 'Nuevo proyecto';
    const ownerId = project ? project.owner.id : me.id;
    const memberIds = new Set(project ? project.members.map((m) => m.id) : []);
    document.getElementById('p-members').innerHTML = users.filter((u) => u.id !== ownerId)
      .map((u) => `<option value="${u.id}" ${memberIds.has(u.id) ? 'selected' : ''}>${TF.esc(u.fullName)} (${TF.esc(u.email)})</option>`)
      .join('');
    if (project) {
      form.name.value = project.name;
      form.description.value = project.description || '';
    }
    TF.openModal('project-modal');
    form.name.focus();
  }

  document.getElementById('new-project-btn').addEventListener('click', () => openForm(null));
  document.getElementById('filter-q').addEventListener('input', render);

  grid.addEventListener('click', async (e) => {
    const editId = e.target.dataset.edit;
    const delId = e.target.dataset.delete;
    if (editId) openForm(projects.find((p) => p.id === Number(editId)));
    if (delId) {
      const p = projects.find((x) => x.id === Number(delId));
      if (!TF.confirmAction(`¿Eliminar el proyecto "${p.name}" y todas sus tareas? Esta acción no se puede deshacer.`)) return;
      try {
        await TF.api.del('/api/projects/' + delId);
        TF.toast('Proyecto eliminado', 'success');
        load();
      } catch (err) { TF.handleError(err); }
    }
  });

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const payload = {
      name: form.name.value.trim(),
      description: form.description.value.trim() || null,
      memberIds: Array.from(document.getElementById('p-members').selectedOptions).map((o) => Number(o.value)),
    };
    if (!payload.name) {
      TF.showFieldErrors(form, { fieldErrors: { name: 'El nombre es obligatorio' } });
      return;
    }
    const btn = document.getElementById('project-save-btn');
    TF.setLoading(btn, true, 'Guardando...');
    try {
      if (editingId) await TF.api.put('/api/projects/' + editingId, payload);
      else await TF.api.post('/api/projects', payload);
      TF.closeModal('project-modal');
      TF.toast(editingId ? 'Proyecto actualizado' : 'Proyecto creado', 'success');
      load();
    } catch (err) {
      TF.showFieldErrors(form, err);
      TF.handleError(err);
    } finally {
      TF.setLoading(btn, false);
    }
  });

  load();
})();
