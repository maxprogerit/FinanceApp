/**
 * ui/toast.js — Toast notification system.
 * Replaces the legacy toast.js in /js/. Exported functions can be imported
 * by ES6 modules and are also exposed on window for backward-compatible scripts.
 */

(function ensureContainer() {
    if (document.getElementById('toast-container')) return;
    const el = document.createElement('div');
    el.id = 'toast-container';
    document.body.appendChild(el);
})();

export function toast(message, type = 'info', duration = 3500) {
    const icons     = { success: '✅', error: '❌', warning: '⚠️', info: 'ℹ️' };
    const container = document.getElementById('toast-container');
    if (!container) return null;
    // Limit to 3 visible toasts — evict oldest first
    const visible = container.querySelectorAll('.toast:not(.toast-out)');
    if (visible.length >= 3) dismissToast(visible[0]);
    const el = document.createElement('div');
    el.className = `toast toast-${type}`;
    el.innerHTML = `<span>${icons[type] ?? 'ℹ️'}</span><span>${message}</span>`;
    el.addEventListener('click', () => dismissToast(el));
    container.appendChild(el);
    if (duration > 0) setTimeout(() => dismissToast(el), duration);
    return el;
}

export function dismissToast(el) {
    if (!el || el.classList.contains('toast-out')) return;
    el.classList.add('toast-out');
    const cleanup = () => { if (el.parentNode) el.remove(); };
    el.addEventListener('animationend', cleanup, { once: true });
    setTimeout(cleanup, 400);
}

export const showSuccess = (msg) => toast(msg, 'success');
export const showError   = (msg) => toast(msg, 'error');
export const showWarning = (msg) => toast(msg, 'warning');
export const showInfo    = (msg) => toast(msg, 'info');
