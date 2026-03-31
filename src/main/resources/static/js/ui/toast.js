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
    const el = document.createElement('div');
    el.className = `toast toast-${type} fade-in`;
    el.innerHTML = `<span>${icons[type] ?? 'ℹ️'}</span><span>${message}</span>`;
    el.addEventListener('click', () => dismissToast(el));
    container.appendChild(el);
    if (duration > 0) setTimeout(() => dismissToast(el), duration);
    return el;
}

export function dismissToast(el) {
    if (!el || el.classList.contains('hiding')) return;
    el.classList.add('hiding');
    el.addEventListener('animationend', () => el.remove(), { once: true });
}

export const showSuccess = (msg) => toast(msg, 'success');
export const showError   = (msg) => toast(msg, 'error');
export const showWarning = (msg) => toast(msg, 'warning');
export const showInfo    = (msg) => toast(msg, 'info');
