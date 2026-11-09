/* =========== TaskFlow — layout común (sidebar, guardia de sesión) =========== */
(() => {
  if (!TF.auth.token) {
    window.location.href = '/index.html';
    return;
  }
  const user = TF.auth.user || {};
  const page = document.body.dataset.page;

  const links = [
    { id: 'dashboard', href: '/dashboard.html', icon: '📊', label: 'Dashboard' },
    { id: 'projects', href: '/projects.html', icon: '📁', label: 'Proyectos' },
    { id: 'tasks', href: '/tasks.html', icon: '✅', label: 'Tareas' },
    { id: 'search', href: '/search.html', icon: '✨', label: 'Búsqueda IA' },
    { id: 'notifications', href: '/notifications.html', icon: '🔔', label: 'Notificaciones', badge: true },
  ];
  if (user.role === 'ADMIN') links.push({ id: 'users', href: '/users.html', icon: '👥', label: 'Usuarios' });

  const sidebar = document.createElement('aside');
  sidebar.className = 'sidebar';
  sidebar.id = 'sidebar';
  sidebar.innerHTML = `
    <div class="brand"><div class="brand-logo">T</div> TaskFlow</div>
    <ul class="nav">
      ${links.map((l) => `
        <li><a href="${l.href}" class="${l.id === page ? 'active' : ''}">
          <span class="icon">${l.icon}</span> ${l.label}
          ${l.badge ? '<span class="badge badge-count hidden" id="notif-badge">0</span>' : ''}
        </a></li>`).join('')}
    </ul>
    <div class="sidebar-footer">
      <div class="user-name">${TF.esc(user.fullName)}</div>
      <div class="small">${TF.esc(user.email)} · ${user.role === 'ADMIN' ? 'Administrador' : 'Usuario'}</div>
      <button class="btn btn-secondary btn-sm" id="logout-btn">Cerrar sesión</button>
    </div>`;

  const app = document.querySelector('.app');
  app.insertBefore(sidebar, app.firstChild);

  const topbar = document.createElement('div');
  topbar.className = 'topbar';
  topbar.innerHTML = `<button class="btn btn-ghost" id="menu-btn" style="color:#fff">☰</button><strong>TaskFlow</strong><span></span>`;
  document.querySelector('.main').insertBefore(topbar, document.querySelector('.main').firstChild);

  document.getElementById('menu-btn').addEventListener('click', () => sidebar.classList.toggle('open'));
  document.getElementById('logout-btn').addEventListener('click', () => TF.auth.logout());

  async function refreshBadge() {
    try {
      const { count } = await TF.api.get('/api/notifications/unread-count');
      const badge = document.getElementById('notif-badge');
      badge.textContent = count;
      badge.classList.toggle('hidden', count === 0);
    } catch { /* silencioso */ }
  }
  // El usuario guardado en localStorage puede estar desactualizado (p. ej. un ADMIN cambió su rol):
  // se sincroniza con el servidor y, si cambió el rol, se recarga para regenerar el menú.
  TF.api.get('/api/users/me').then((fresh) => {
    TF.auth.setUser(fresh);
    if (fresh.role !== user.role) window.location.reload();
  }).catch(() => { /* 401 ya redirige al login */ });

  window.TFRefreshBadge = refreshBadge;
  refreshBadge();
  setInterval(refreshBadge, 60000);
})();
