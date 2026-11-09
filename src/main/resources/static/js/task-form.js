/* =========== TaskFlow — modal reutilizable de crear/editar tarea =========== */
const TaskForm = (() => {
  let projects = [];
  let onSaved = () => {};
  let editingId = null;

  function inject() {
    if (document.getElementById('task-modal')) return;
    const div = document.createElement('div');
    div.innerHTML = `
    <div class="modal-backdrop" id="task-modal">
      <div class="modal" role="dialog" aria-modal="true" aria-labelledby="task-modal-title">
        <div class="modal-header">
          <h2 id="task-modal-title">Nueva tarea</h2>
          <button class="btn btn-ghost btn-sm" data-close-modal="task-modal" aria-label="Cerrar">✕</button>
        </div>
        <form id="task-form" novalidate>
          <div class="modal-body">
            <div class="form-group">
              <label for="tf-title">Título *</label>
              <input id="tf-title" name="title" required maxlength="200">
            </div>
            <div class="form-group">
              <label for="tf-description">Descripción</label>
              <textarea id="tf-description" name="description" maxlength="5000" rows="4"></textarea>
            </div>
            <div class="form-row">
              <div class="form-group">
                <label for="tf-project">Proyecto *</label>
                <select id="tf-project" name="projectId" required></select>
              </div>
              <div class="form-group">
                <label for="tf-assignee">Responsable</label>
                <select id="tf-assignee" name="assigneeId"></select>
              </div>
            </div>
            <div class="form-row">
              <div class="form-group">
                <label for="tf-priority">Prioridad</label>
                <select id="tf-priority" name="priority">
                  <option value="BAJA">Baja</option>
                  <option value="MEDIA" selected>Media</option>
                  <option value="ALTA">Alta</option>
                  <option value="URGENTE">Urgente</option>
                </select>
              </div>
              <div class="form-group">
                <label for="tf-status">Estado</label>
                <select id="tf-status" name="status">
                  <option value="PENDIENTE">Pendiente</option>
                  <option value="EN_PROGRESO">En progreso</option>
                  <option value="COMPLETADA">Completada</option>
                </select>
              </div>
            </div>
            <div class="form-group">
              <label for="tf-due">Fecha límite</label>
              <input id="tf-due" name="dueDate" type="date">
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" data-close-modal="task-modal">Cancelar</button>
            <button type="submit" class="btn" id="task-save-btn">Guardar</button>
          </div>
        </form>
      </div>
    </div>`;
    document.body.appendChild(div.firstElementChild);
    document.getElementById('tf-project').addEventListener('change', () => fillAssignees(null));
    document.getElementById('task-form').addEventListener('submit', submit);
  }

  function fillProjects(selectedId) {
    const sel = document.getElementById('tf-project');
    sel.innerHTML = projects.map((p) => `<option value="${p.id}">${TF.esc(p.name)}</option>`).join('');
    if (selectedId) sel.value = String(selectedId);
  }

  /** Solo el propietario y los miembros del proyecto pueden ser responsables. */
  function fillAssignees(selectedId) {
    const pid = Number(document.getElementById('tf-project').value);
    const project = projects.find((p) => p.id === pid);
    const people = project ? [project.owner, ...project.members] : [];
    const sel = document.getElementById('tf-assignee');
    sel.innerHTML = '<option value="">Sin asignar</option>' +
      people.map((u) => `<option value="${u.id}">${TF.esc(u.fullName)}</option>`).join('');
    if (selectedId) sel.value = String(selectedId);
  }

  async function init(callback) {
    inject();
    onSaved = callback || (() => {});
    projects = await TF.api.get('/api/projects');
    return projects;
  }

  function openCreate(defaultProjectId) {
    if (!projects.length) {
      TF.toast('Primero crea un proyecto', 'error');
      return;
    }
    editingId = null;
    const form = document.getElementById('task-form');
    form.reset();
    form.querySelectorAll('.field-error').forEach((e) => e.remove());
    document.getElementById('task-modal-title').textContent = 'Nueva tarea';
    fillProjects(defaultProjectId || projects[0].id);
    fillAssignees(null);
    TF.openModal('task-modal');
    document.getElementById('tf-title').focus();
  }

  function openEdit(task) {
    editingId = task.id;
    const form = document.getElementById('task-form');
    form.reset();
    form.querySelectorAll('.field-error').forEach((e) => e.remove());
    document.getElementById('task-modal-title').textContent = 'Editar tarea #' + task.id;
    fillProjects(task.projectId);
    fillAssignees(task.assignee ? task.assignee.id : null);
    form.title.value = task.title;
    form.description.value = task.description || '';
    form.priority.value = task.priority;
    form.status.value = task.status;
    form.dueDate.value = task.dueDate || '';
    TF.openModal('task-modal');
  }

  async function submit(e) {
    e.preventDefault();
    const form = e.target;
    const btn = document.getElementById('task-save-btn');
    const payload = {
      title: form.title.value.trim(),
      description: form.description.value.trim() || null,
      projectId: Number(form.projectId.value),
      assigneeId: form.assigneeId.value ? Number(form.assigneeId.value) : null,
      priority: form.priority.value,
      status: form.status.value,
      dueDate: form.dueDate.value || null,
    };
    if (!payload.title) {
      TF.showFieldErrors(form, { fieldErrors: { title: 'El título es obligatorio' } });
      return;
    }
    TF.setLoading(btn, true, 'Guardando...');
    try {
      const saved = editingId
        ? await TF.api.put('/api/tasks/' + editingId, payload)
        : await TF.api.post('/api/tasks', payload);
      TF.closeModal('task-modal');
      TF.toast(editingId ? 'Tarea actualizada' : 'Tarea creada', 'success');
      onSaved(saved);
    } catch (err) {
      TF.showFieldErrors(form, err);
      TF.handleError(err);
    } finally {
      TF.setLoading(btn, false);
    }
  }

  return { init, openCreate, openEdit, get projects() { return projects; } };
})();
