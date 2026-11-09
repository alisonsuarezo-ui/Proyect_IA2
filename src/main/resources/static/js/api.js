/* =========== TaskFlow — cliente API y utilidades comunes =========== */
const TF = (() => {
  const TOKEN_KEY = 'taskflow.token';
  const USER_KEY = 'taskflow.user';

  const auth = {
    get token() { return localStorage.getItem(TOKEN_KEY); },
    get user() {
      try { return JSON.parse(localStorage.getItem(USER_KEY)); } catch { return null; }
    },
    save(data) {
      localStorage.setItem(TOKEN_KEY, data.token);
      localStorage.setItem(USER_KEY, JSON.stringify(data.user));
    },
    setUser(user) { localStorage.setItem(USER_KEY, JSON.stringify(user)); },
    clear() { localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(USER_KEY); },
    isAdmin() { return auth.user && auth.user.role === 'ADMIN'; },
    logout() { auth.clear(); window.location.href = '/index.html'; },
  };

  class ApiError extends Error {
    constructor(status, body) {
      super((body && body.message) || `Error ${status}`);
      this.status = status;
      this.code = body && body.code;
      this.fieldErrors = (body && body.fieldErrors) || {};
    }
  }

  async function request(method, path, body) {
    const headers = { 'Accept': 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (auth.token) headers['Authorization'] = 'Bearer ' + auth.token;
    let res;
    try {
      res = await fetch(path, { method, headers, body: body !== undefined ? JSON.stringify(body) : undefined });
    } catch (e) {
      throw new ApiError(0, { message: 'No se pudo conectar con el servidor' });
    }
    if (res.status === 401 && !path.startsWith('/api/auth/')) {
      auth.clear();
      window.location.href = '/index.html?expired=1';
      throw new ApiError(401, { message: 'Sesión expirada' });
    }
    if (res.status === 204) return null;
    const text = await res.text();
    let data = null;
    if (text) { try { data = JSON.parse(text); } catch { data = { message: text }; } }
    if (!res.ok) throw new ApiError(res.status, data);
    return data;
  }

  const api = {
    get: (p) => request('GET', p),
    post: (p, b) => request('POST', p, b ?? {}),
    put: (p, b) => request('PUT', p, b ?? {}),
    patch: (p, b) => request('PATCH', p, b ?? {}),
    del: (p) => request('DELETE', p),
  };

  // ---------- Utilidades de UI ----------
  function esc(value) {
    if (value === null || value === undefined) return '';
    return String(value).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }

  function qs(params) {
    const sp = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => { if (v !== null && v !== undefined && v !== '') sp.append(k, v); });
    const s = sp.toString();
    return s ? '?' + s : '';
  }

  function toast(message, type = 'info') {
    let box = document.getElementById('toasts');
    if (!box) { box = document.createElement('div'); box.id = 'toasts'; document.body.appendChild(box); }
    const el = document.createElement('div');
    el.className = 'toast ' + type;
    el.textContent = message;
    box.appendChild(el);
    setTimeout(() => el.remove(), 4500);
  }

  function handleError(err) {
    console.error(err);
    toast(err.message || 'Error inesperado', 'error');
  }

  const STATUS_LABEL = { PENDIENTE: 'Pendiente', EN_PROGRESO: 'En progreso', COMPLETADA: 'Completada' };
  const PRIORITY_LABEL = { BAJA: 'Baja', MEDIA: 'Media', ALTA: 'Alta', URGENTE: 'Urgente' };

  const statusBadge = (s) => `<span class="badge st-${s}">${STATUS_LABEL[s] || s}</span>`;
  const priorityBadge = (p) => `<span class="badge pr-${p}">${PRIORITY_LABEL[p] || p}</span>`;

  function fmtDate(iso) {
    if (!iso) return '—';
    const [y, m, d] = iso.split('-');
    return `${d}/${m}/${y}`;
  }

  function fmtDateTime(iso) {
    if (!iso) return '—';
    return new Date(iso).toLocaleString('es', { dateStyle: 'medium', timeStyle: 'short' });
  }

  function initials(name) {
    return (name || '?').split(' ').filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('');
  }

  function dueCell(task) {
    if (!task.dueDate) return '<span class="muted">Sin fecha</span>';
    return fmtDate(task.dueDate) + (task.overdue ? ' <span class="badge badge-overdue">Atrasada</span>' : '');
  }

  // ---------- Modal ----------
  function openModal(id) { document.getElementById(id).classList.add('open'); }
  function closeModal(id) { document.getElementById(id).classList.remove('open'); }
  document.addEventListener('click', (e) => {
    const closer = e.target.closest('[data-close-modal]');
    if (closer) closeModal(closer.getAttribute('data-close-modal'));
    if (e.target.classList && e.target.classList.contains('modal-backdrop')) e.target.classList.remove('open');
  });
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') document.querySelectorAll('.modal-backdrop.open').forEach((m) => m.classList.remove('open'));
  });

  function confirmAction(message) { return window.confirm(message); }

  /** Muestra errores de validación por campo debajo de cada input (data-field). */
  function showFieldErrors(form, err) {
    form.querySelectorAll('.field-error').forEach((e) => e.remove());
    Object.entries(err.fieldErrors || {}).forEach(([field, msg]) => {
      const input = form.querySelector(`[name="${field}"]`);
      if (input) {
        const span = document.createElement('span');
        span.className = 'field-error';
        span.textContent = msg;
        input.insertAdjacentElement('afterend', span);
      }
    });
  }

  function setLoading(btn, loading, text) {
    if (!btn) return;
    if (loading) {
      btn.dataset.label = btn.innerHTML;
      btn.disabled = true;
      btn.innerHTML = `<span class="spinner"></span> ${text || 'Procesando...'}`;
    } else {
      btn.disabled = false;
      if (btn.dataset.label) btn.innerHTML = btn.dataset.label;
    }
  }

  return {
    auth, api, ApiError, esc, qs, toast, handleError, statusBadge, priorityBadge, fmtDate, fmtDateTime,
    initials, dueCell, openModal, closeModal, confirmAction, showFieldErrors, setLoading,
    STATUS_LABEL, PRIORITY_LABEL,
  };
})();
