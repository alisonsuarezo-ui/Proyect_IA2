(async () => {
  const user = TF.auth.user || {};
  document.getElementById('greeting').textContent = `Hola, ${user.fullName ? user.fullName.split(' ')[0] : ''} 👋`;

  function bars(container, entries, total, labelFn) {
    container.innerHTML = entries.map(([k, v]) => {
      const pct = total ? Math.round((v * 100) / total) : 0;
      return `<div class="bar-row"><span>${labelFn(k)}</span><div class="progress"><span style="width:${pct}%"></span></div><strong>${v}</strong></div>`;
    }).join('');
  }

  function taskTable(tasks, emptyMsg) {
    if (!tasks.length) return `<div class="empty">${emptyMsg}</div>`;
    return `<table><thead><tr><th>Tarea</th><th class="hide-sm">Proyecto</th><th>Prioridad</th><th>Fecha</th></tr></thead><tbody>
      ${tasks.map((t) => `<tr>
        <td><a class="title-link" href="/task.html?id=${t.id}">${TF.esc(t.title)}</a>
          <div class="small muted">${t.assignee ? TF.esc(t.assignee.fullName) : 'Sin asignar'}</div></td>
        <td class="hide-sm">${TF.esc(t.projectName)}</td>
        <td>${TF.priorityBadge(t.priority)}</td>
        <td>${TF.dueCell(t)}</td></tr>`).join('')}
    </tbody></table>`;
  }

  try {
    const [d, ai] = await Promise.all([TF.api.get('/api/dashboard'), TF.api.get('/api/ai/status')]);
    if (!ai.configured) {
      document.getElementById('ai-alert').innerHTML =
        `<div class="alert alert-warning"><strong>IA no configurada.</strong> ${TF.esc(ai.message)}</div>`;
    }
    document.getElementById('s-total').textContent = d.totalTasks;
    document.getElementById('s-projects').textContent = `${d.totalProjects} proyecto(s)`;
    document.getElementById('s-open').textContent = d.pending + d.inProgress;
    document.getElementById('s-mine').textContent = `${d.myOpenTasks} asignada(s) a ti`;
    document.getElementById('s-done').textContent = d.completed;
    document.getElementById('s-rate').textContent = `${d.completionRate}% de finalización`;
    document.getElementById('s-overdue').textContent = d.overdue;
    document.getElementById('s-soon').textContent = `${d.dueSoon} vencen en 7 días`;

    bars(document.getElementById('status-bars'),
      [['PENDIENTE', d.pending], ['EN_PROGRESO', d.inProgress], ['COMPLETADA', d.completed]],
      d.totalTasks, (k) => TF.STATUS_LABEL[k]);
    const openTotal = Object.values(d.byPriority).reduce((a, b) => a + b, 0);
    bars(document.getElementById('priority-bars'), Object.entries(d.byPriority), openTotal, (k) => TF.PRIORITY_LABEL[k]);

    const pp = document.getElementById('project-progress');
    pp.innerHTML = d.projects.length ? d.projects.map((p) => `
      <div style="margin-bottom:1rem">
        <div style="display:flex;justify-content:space-between;font-size:.9rem;margin-bottom:.3rem">
          <a href="/tasks.html?projectId=${p.id}"><strong>${TF.esc(p.name)}</strong></a>
          <span class="muted">${p.completed}/${p.total} · ${p.progress}%</span>
        </div>
        <div class="progress"><span style="width:${p.progress}%"></span></div>
      </div>`).join('') : '<div class="empty">Aún no tienes proyectos. <a href="/projects.html">Crea el primero</a>.</div>';

    document.getElementById('overdue-list').innerHTML = taskTable(d.overdueTasks, '¡Sin tareas atrasadas! 🎉');
    document.getElementById('upcoming-list').innerHTML = taskTable(d.upcoming, 'No hay vencimientos en los próximos 7 días');
  } catch (err) {
    TF.handleError(err);
  }
})();
