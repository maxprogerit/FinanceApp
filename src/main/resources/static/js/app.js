/**
 * app.js — Shared utilities for Smart Finance Dashboard
 * Included on every page. Provides: toast notifications, API helper,
 * loading states, common formatters.
 */

// ─── Tailwind dark-mode: class strategy ─────────────────────────────────────
if (window.tailwind) {
    tailwind.config = { darkMode: 'class' };
}

// ─── API base ────────────────────────────────────────────────────────────────
const API = '/api';

// ─── Toast notifications ─────────────────────────────────────────────────────
(function initToastContainer() {
    if (document.getElementById('toast-container')) return;
    const el = document.createElement('div');
    el.id = 'toast-container';
    document.body.appendChild(el);
})();

/**
 * Show a toast notification.
 * @param {string} message
 * @param {'success'|'error'|'warning'|'info'} type
 * @param {number} duration  milliseconds before auto-dismiss (0 = never)
 */
function toast(message, type = 'info', duration = 3500) {
    const icons = {
        success: '✅',
        error:   '❌',
        warning: '⚠️',
        info:    'ℹ️',
    };
    const container = document.getElementById('toast-container');
    const el = document.createElement('div');
    el.className = `toast toast-${type} fade-in`;
    el.innerHTML = `<span>${icons[type] ?? 'ℹ️'}</span><span>${message}</span>`;

    // Click to dismiss
    el.addEventListener('click', () => dismissToast(el));
    container.appendChild(el);

    if (duration > 0) {
        setTimeout(() => dismissToast(el), duration);
    }
    return el;
}

function dismissToast(el) {
    if (!el || el.classList.contains('hiding')) return;
    el.classList.add('hiding');
    el.addEventListener('animationend', () => el.remove(), { once: true });
}

// Convenience aliases
const showSuccess = (msg) => toast(msg, 'success');
const showError   = (msg) => toast(msg, 'error');
const showWarning = (msg) => toast(msg, 'warning');
const showInfo    = (msg) => toast(msg, 'info');

// ─── Loading button state ────────────────────────────────────────────────────
function setLoading(btn, loading) {
    if (!btn) return;
    if (loading) {
        btn.dataset.originalText = btn.textContent;
        btn.classList.add('btn-loading');
        btn.disabled = true;
    } else {
        btn.classList.remove('btn-loading');
        btn.disabled = false;
        if (btn.dataset.originalText) {
            btn.textContent = btn.dataset.originalText;
        }
    }
}

// ─── HTTP helpers ────────────────────────────────────────────────────────────
async function apiFetch(url, options = {}) {
    const defaults = {
        headers: { 'Content-Type': 'application/json' },
    };
    const response = await fetch(url, { ...defaults, ...options });
    if (!response.ok) {
        let msg = `Server error ${response.status}`;
        try { const body = await response.json(); msg = body.message || msg; } catch (_) {}
        throw new Error(msg);
    }
    if (response.status === 204 || response.headers.get('content-length') === '0') {
        return null;
    }
    return response.json();
}

async function apiGet(path)         { return apiFetch(`${API}${path}`); }
async function apiPost(path, body)  { return apiFetch(`${API}${path}`, { method: 'POST',   body: JSON.stringify(body) }); }
async function apiPut(path, body)   { return apiFetch(`${API}${path}`, { method: 'PUT',    body: JSON.stringify(body) }); }
async function apiPatch(path, body) { return apiFetch(`${API}${path}`, { method: 'PATCH',  body: JSON.stringify(body) }); }
async function apiDelete(path)      { return apiFetch(`${API}${path}`, { method: 'DELETE' }); }

// ─── Formatters ──────────────────────────────────────────────────────────────
function formatCurrency(amount, currency = 'USD') {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(amount ?? 0);
}

function formatDate(dateStr) {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('en-US', {
        year: 'numeric', month: 'short', day: 'numeric'
    });
}

function formatDateTime(dateStr) {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleString('en-US', {
        year: 'numeric', month: 'short', day: 'numeric',
        hour: '2-digit', minute: '2-digit'
    });
}

function daysUntil(dateStr) {
    if (!dateStr) return null;
    return Math.ceil((new Date(dateStr) - Date.now()) / 86400000);
}

// ─── Table sort helper ───────────────────────────────────────────────────────
/**
 * Make a <table> sortable by clicking column headers.
 * @param {string} tableId   ID of the <table> element
 * @param {(col:number, dir:'asc'|'desc') => void} onSort  callback
 */
function makeTableSortable(tableId, onSort) {
    const table = document.getElementById(tableId);
    if (!table) return;
    const headers = table.querySelectorAll('th.sortable');
    let currentCol = -1, currentDir = 'asc';

    headers.forEach((th, i) => {
        th.addEventListener('click', () => {
            headers.forEach(h => h.classList.remove('sort-asc', 'sort-desc'));
            if (currentCol === i) {
                currentDir = currentDir === 'asc' ? 'desc' : 'asc';
            } else {
                currentCol = i;
                currentDir = 'asc';
            }
            th.classList.add(`sort-${currentDir}`);
            onSort(i, currentDir);
        });
    });
}

// ─── Empty state helper ──────────────────────────────────────────────────────
function emptyStateHTML(icon, message) {
    return `<div class="empty-state">
        <div class="text-5xl mb-3">${icon}</div>
        <p class="text-base">${message}</p>
    </div>`;
}

// ─── Expose on window ────────────────────────────────────────────────────────
Object.assign(window, {
    toast, showSuccess, showError, showWarning, showInfo,
    setLoading, apiFetch, apiGet, apiPost, apiPut, apiPatch, apiDelete,
    formatCurrency, formatDate, formatDateTime, daysUntil,
    makeTableSortable, emptyStateHTML, API,
});
