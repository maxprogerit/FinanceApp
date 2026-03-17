/**
 * app.js — Shared utilities for Smart Finance Dashboard
 * Provides: toast notifications, API helpers, loading states,
 * currency conversion, formatters, and nav currency selector.
 */

// ─── Tailwind dark-mode ───────────────────────────────────────────────────────
if (window.tailwind) {
    tailwind.config = { darkMode: 'class' };
}

// ─── API base ─────────────────────────────────────────────────────────────────
const API = '/api';

// ─── Currency state ───────────────────────────────────────────────────────────
let currentCurrency = localStorage.getItem('currentCurrency') || 'USD';

// Static fallback rates vs USD — used before real-time rates arrive
const STATIC_RATES = {
    USD: 1.0,    EUR: 0.9234, GBP: 0.7891, JPY: 149.52,
    CAD: 1.3612, AUD: 1.5243, CHF: 0.8965, CNY: 7.2410,
    INR: 83.12,  BRL: 4.9720, RSD: 107.80,
};

let exchangeRates = {};   // Populated by fetchExchangeRates(); merged over STATIC_RATES

const SUPPORTED_CURRENCIES = [
    'USD', 'EUR', 'GBP', 'JPY', 'CAD', 'AUD', 'CHF', 'CNY', 'INR', 'BRL', 'RSD',
];

function convertAmount(amount, from = 'USD', to = currentCurrency) {
    if (!from || !to || from === to) return amount ?? 0;
    const rates = Object.assign({}, STATIC_RATES, exchangeRates);
    const inUSD = (amount ?? 0) / (rates[from] ?? 1);
    return inUSD * (rates[to] ?? 1);
}

/**
 * Format and optionally convert a monetary amount.
 * @param {number} amount         - The numeric value to display.
 * @param {string} storedCurrency - The currency the value is stored in (default: 'USD').
 * Converts to `currentCurrency` before formatting.
 */
function formatCurrency(amount, storedCurrency = 'USD') {
    const display = currentCurrency || 'USD';
    const source  = storedCurrency  || 'USD';
    const value   = source !== display
        ? convertAmount(amount ?? 0, source, display)
        : (amount ?? 0);
    try {
        return new Intl.NumberFormat('en-US', { style: 'currency', currency: display }).format(value);
    } catch (_) {
        return `${display} ${value.toFixed(2)}`;
    }
}

function setCurrency(code) {
    currentCurrency = code;
    localStorage.setItem('currentCurrency', code);
    window.dispatchEvent(new CustomEvent('currencyChange', { detail: { currency: code } }));
}

async function fetchExchangeRates() {
    try {
        const rates = await apiGet('/currency/rates');
        if (rates && typeof rates === 'object') {
            exchangeRates = rates;
            window.exchangeRates = rates;   // Keep window in sync for other scripts
            window.dispatchEvent(new CustomEvent('ratesLoaded', { detail: rates }));
        }
    } catch (_) { /* keep STATIC_RATES fallback */ }
}

// ─── Nav currency selector (auto-injected on every authenticated page) ────────
function injectCurrencySelector() {
    if (document.getElementById('globalCurrencySelect')) return;
    const container = document.querySelector('nav .flex.items-center.space-x-4');
    if (!container) return;

    const select = document.createElement('select');
    select.id = 'globalCurrencySelect';
    select.title = 'Display currency';
    select.className =
        'text-xs border border-gray-300 dark:border-gray-600 rounded-md ' +
        'bg-white dark:bg-gray-700 text-gray-900 dark:text-white px-2 py-1 ' +
        'focus:outline-none focus:ring-2 focus:ring-indigo-500 cursor-pointer';

    SUPPORTED_CURRENCIES.forEach(c => {
        const opt = document.createElement('option');
        opt.value = c;
        opt.textContent = c;
        if (c === currentCurrency) opt.selected = true;
        select.appendChild(opt);
    });

    select.addEventListener('change', e => {
        setCurrency(e.target.value);
        location.reload();
    });

    container.insertBefore(select, container.firstChild);
}

// ─── Toast notifications ──────────────────────────────────────────────────────
(function initToastContainer() {
    if (document.getElementById('toast-container')) return;
    const el = document.createElement('div');
    el.id = 'toast-container';
    document.body.appendChild(el);
})();

function toast(message, type = 'info', duration = 3500) {
    const icons = { success: '✅', error: '❌', warning: '⚠️', info: 'ℹ️' };
    const container = document.getElementById('toast-container');
    const el = document.createElement('div');
    el.className = `toast toast-${type} fade-in`;
    el.innerHTML = `<span>${icons[type] ?? 'ℹ️'}</span><span>${message}</span>`;
    el.addEventListener('click', () => dismissToast(el));
    container.appendChild(el);
    if (duration > 0) setTimeout(() => dismissToast(el), duration);
    return el;
}

function dismissToast(el) {
    if (!el || el.classList.contains('hiding')) return;
    el.classList.add('hiding');
    el.addEventListener('animationend', () => el.remove(), { once: true });
}

const showSuccess = (msg) => toast(msg, 'success');
const showError   = (msg) => toast(msg, 'error');
const showWarning = (msg) => toast(msg, 'warning');
const showInfo    = (msg) => toast(msg, 'info');

// ─── Loading button state ─────────────────────────────────────────────────────
function setLoading(btn, loading) {
    if (!btn) return;
    if (loading) {
        btn.dataset.originalText = btn.textContent;
        btn.classList.add('btn-loading');
        btn.disabled = true;
    } else {
        btn.classList.remove('btn-loading');
        btn.disabled = false;
        if (btn.dataset.originalText) btn.textContent = btn.dataset.originalText;
    }
}

// ─── HTTP helpers ─────────────────────────────────────────────────────────────
async function apiFetch(url, options = {}) {
    const defaults = { headers: { 'Content-Type': 'application/json' } };
    const response = await fetch(url, { ...defaults, ...options });
    if (!response.ok) {
        let msg = `Server error ${response.status}`;
        try { const body = await response.json(); msg = body.message || msg; } catch (_) {}
        throw new Error(msg);
    }
    if (response.status === 204 || response.headers.get('content-length') === '0') return null;
    return response.json();
}

async function apiGet(path)         { return apiFetch(`${API}${path}`); }
async function apiPost(path, body)  { return apiFetch(`${API}${path}`, { method: 'POST',  body: JSON.stringify(body) }); }
async function apiPut(path, body)   { return apiFetch(`${API}${path}`, { method: 'PUT',   body: JSON.stringify(body) }); }
async function apiPatch(path, body) { return apiFetch(`${API}${path}`, { method: 'PATCH', body: JSON.stringify(body) }); }
async function apiDelete(path)      { return apiFetch(`${API}${path}`, { method: 'DELETE' }); }

// ─── Formatters ───────────────────────────────────────────────────────────────
function formatDate(dateStr) {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('en-US', {
        year: 'numeric', month: 'short', day: 'numeric',
    });
}

function formatDateTime(dateStr) {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleString('en-US', {
        year: 'numeric', month: 'short', day: 'numeric',
        hour: '2-digit', minute: '2-digit',
    });
}

function daysUntil(dateStr) {
    if (!dateStr) return null;
    return Math.ceil((new Date(dateStr) - Date.now()) / 86400000);
}

// ─── Table sort helper ────────────────────────────────────────────────────────
function makeTableSortable(tableId, onSort) {
    const table = document.getElementById(tableId);
    if (!table) return;
    const headers = table.querySelectorAll('th.sortable');
    let currentCol = -1, currentDir = 'asc';
    headers.forEach((th, i) => {
        th.addEventListener('click', () => {
            headers.forEach(h => h.classList.remove('sort-asc', 'sort-desc'));
            currentDir = (currentCol === i && currentDir === 'asc') ? 'desc' : 'asc';
            currentCol = i;
            th.classList.add(`sort-${currentDir}`);
            onSort(i, currentDir);
        });
    });
}

// ─── Empty state helper ───────────────────────────────────────────────────────
function emptyStateHTML(icon, message) {
    return `<div class="empty-state"><div class="text-5xl mb-3">${icon}</div><p class="text-base">${message}</p></div>`;
}

// ─── Init on every page ───────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    injectCurrencySelector();
    fetchExchangeRates();
});

// ─── Expose on window ─────────────────────────────────────────────────────────
Object.assign(window, {
    // Currency
    currentCurrency, STATIC_RATES, SUPPORTED_CURRENCIES,
    convertAmount, setCurrency, fetchExchangeRates,
    // Toast
    toast, showSuccess, showError, showWarning, showInfo,
    // API
    setLoading, apiFetch, apiGet, apiPost, apiPut, apiPatch, apiDelete,
    // Formatters
    formatCurrency, formatDate, formatDateTime, daysUntil,
    // Misc
    makeTableSortable, emptyStateHTML, API,
});
