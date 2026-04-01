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

// ─── Subscription / plan state ────────────────────────────────────────────────
let _planState = null; // { active, plan, status, username, trialEnd?, currentPeriodEnd? }

async function fetchPlanState() {
    if (_planState) return _planState;
    try {
        const cached = sessionStorage.getItem('_planState');
        if (cached) { _planState = JSON.parse(cached); return _planState; }
        const data = await apiGet('/payments/status');
        _planState = data || { plan: 'FREE', status: '', active: false };
        try { sessionStorage.setItem('_planState', JSON.stringify(_planState)); } catch (_) {}
    } catch (_) {
        _planState = { plan: 'FREE', status: '', active: false };
    }
    return _planState;
}

function isProUser() {
    if (!_planState) return false;
    const plan = _planState.plan;
    return plan === 'PRO' && (_planState.status === 'active' || _planState.status === 'trialing');
}

function openUpgradeModal() {
    const modal = document.getElementById('upgrade-modal');
    if (modal) { modal.classList.remove('hidden'); document.body.style.overflow = 'hidden'; }
}

function closeUpgradeModal() {
    const modal = document.getElementById('upgrade-modal');
    if (modal) { modal.classList.add('hidden'); document.body.style.overflow = ''; }
}

// ─── Nav currency selector (auto-injected on every authenticated page) ────────
function injectCurrencySelector() {
    // Now handled by injectSidebar — skip if sidebar is present
    if (document.getElementById('app-sidebar')) return;
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

// ─── Page title map ───────────────────────────────────────────────────────────
function _getPageTitle(path) {
    const titles = {
        '/dashboard': 'Dashboard', '/transactions': 'Transactions',
        '/investments': 'Assets', '/analytics': 'Analytics', '/settings': 'Settings',
        '/profile': 'My Profile', '/admin': 'Admin Panel',
    };
    return titles[path] || 'Smart Finance';
}

// ─── Sidebar injection (runs on every authenticated page) ─────────────────────
function injectSidebar() {
    if (document.getElementById('app-sidebar')) return;
    const existingNav = document.querySelector('body > nav');
    if (!existingNav) return; // login / register / index — no sidebar needed
    existingNav.style.display = 'none';

    const path = window.location.pathname;
    const isDark = document.documentElement.classList.contains('dark');

    const navItems = [
        { href: '/dashboard',    label: 'Dashboard',     icon: '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"/>' },
        { href: '/transactions', label: 'Transactions',  icon: '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4"/>' },
        { href: '/investments',  label: 'Assets',        icon: '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6"/>' },
        { href: '/analytics',   label: 'Analytics',     icon: '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 8v8m-4-5v5m-4-2v2m-2 4h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"/>' },
        { href: '/settings',    label: 'Settings',      icon: '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>' },
    ];

    const moonPath  = 'M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z';
    const sunPath   = 'M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z';
    const themePath = isDark ? sunPath : moonPath;

    // ── Sidebar ──
    const sidebar = document.createElement('aside');
    sidebar.id = 'app-sidebar';
    sidebar.innerHTML = `
        <div class="sidebar-logo">
            <span class="sidebar-logo-icon">💰</span>
            <span class="sidebar-logo-text">Smart Finance</span>
        </div>
        <nav class="sidebar-nav">
            ${navItems.map(item => {
                const active = path === item.href || (item.href !== '/' && path.startsWith(item.href));
                return `<a href="${item.href}" class="sidebar-nav-item${active ? ' active' : ''}">
                    <span class="sidebar-nav-icon">
                        <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" class="icon-sm">${item.icon}</svg>
                    </span>
                    <span class="sidebar-nav-label">${item.label}</span>
                </a>`;
            }).join('')}
        </nav>
        <div class="sidebar-footer">
            <select id="globalCurrencySelect" title="Display currency" class="sidebar-currency">
                ${SUPPORTED_CURRENCIES.map(c => `<option value="${c}"${c === currentCurrency ? ' selected' : ''}>${c}</option>`).join('')}
            </select>
            <button id="sidebarThemeBtn" class="sidebar-icon-btn" title="Toggle theme">
                <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" class="icon-sm">
                    <path id="sidebarThemePath" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="${themePath}"/>
                </svg>
            </button>
            <a href="/profile" class="sidebar-icon-btn${path === '/profile' ? ' active' : ''}" title="My Profile">
                <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" class="icon-sm">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5.121 17.804A13.937 13.937 0 0112 16c2.5 0 4.847.655 6.879 1.804M15 10a3 3 0 11-6 0 3 3 0 016 0zm6 2a9 9 0 11-18 0 9 9 0 0118 0z"/>
                </svg>
            </a>
            <a href="/logout" class="sidebar-icon-btn sidebar-logout" title="Logout">
                <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" class="icon-sm">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"/>
                </svg>
            </a>
        </div>`;

    // ── Top header ──
    const header = document.createElement('header');
    header.id = 'app-header';
    header.innerHTML = `
        <div class="header-left">
            <button id="sidebarToggle" class="sidebar-toggle" title="Toggle sidebar">
                <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" class="icon">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h7"/>
                </svg>
            </button>
            <span class="header-title">${_getPageTitle(path)}</span>
        </div>
        <div class="header-right">
            <div class="relative">
                <button id="notificationBtn" class="header-icon-btn" title="Notifications">
                    <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" class="icon">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"/>
                    </svg>
                    <span id="notificationBadge" class="hidden notification-badge">0</span>
                </button>
            </div>
        </div>`;

    // ── Mobile backdrop ──
    const backdrop = document.createElement('div');
    backdrop.id = 'sidebar-backdrop';
    backdrop.addEventListener('click', () => {
        sidebar.classList.remove('mobile-open');
        backdrop.style.display = 'none';
    });

    document.body.insertBefore(sidebar, document.body.firstChild);
    document.body.insertBefore(header, sidebar.nextSibling);
    document.body.appendChild(backdrop);
    document.body.classList.add('has-sidebar');

    // Adjust existing <main> padding
    const mainEl = document.querySelector('main');
    if (mainEl) mainEl.style.maxWidth = 'none';

    // Wire currency selector
    const currSel = document.getElementById('globalCurrencySelect');
    if (currSel) {
        currSel.addEventListener('change', e => { setCurrency(e.target.value); location.reload(); });
    }

    // Wire theme toggle (independent of theme.js which binds to the hidden nav's button)
    const themePath2 = document.getElementById('sidebarThemePath');
    const themeBtn = document.getElementById('sidebarThemeBtn');
    if (themeBtn) {
        themeBtn.addEventListener('click', () => {
            const nowDark = document.documentElement.classList.toggle('dark');
            localStorage.setItem('theme', nowDark ? 'dark' : 'light');
            if (themePath2) themePath2.setAttribute('d', nowDark ? sunPath : moonPath);
        });
    }

    // Wire sidebar toggle
    const toggleBtn = document.getElementById('sidebarToggle');
    if (toggleBtn) {
        toggleBtn.addEventListener('click', () => {
            if (window.innerWidth <= 768) {
                const opening = !sidebar.classList.contains('mobile-open');
                sidebar.classList.toggle('mobile-open');
                backdrop.style.display = opening ? 'block' : 'none';
            } else {
                sidebar.classList.toggle('collapsed');
                document.body.classList.toggle('sidebar-collapsed');
            }
        });
    }
}

// ─── Upgrade UI injection (floating button + comparison modal) ────────────────
function injectUpgradeUI() {
    // Don't inject on public pages (no sidebar = no auth)
    if (!document.getElementById('app-sidebar')) return;
    // Don't double-inject
    if (document.getElementById('upgrade-modal')) return;

    const features = [
        { label: 'Transaction tracking',   free: true,  pro: true },
        { label: 'Budgets & goals',        free: true,  pro: true },
        { label: 'Investment tracking',    free: true,  pro: true },
        { label: 'Basic analytics',        free: true,  pro: true },
        { label: 'CSV import',             free: false, pro: true },
        { label: 'Export CSV / PDF',       free: false, pro: true },
        { label: 'AI-powered insights',    free: false, pro: true },
        { label: 'Spending forecast',      free: false, pro: true },
        { label: 'Auto-categorization',    free: false, pro: true },
        { label: 'Priority support',       free: false, pro: true },
    ];

    const featureRow = (f, isPro) => {
        const avail = isPro ? f.pro : f.free;
        return `<div class="plan-feature${!avail ? ' locked' : ''}">
            <span class="plan-feature-icon ${avail ? 'check' : 'cross'}">${avail ? '✓' : '✗'}</span>
            <span>${f.label}</span>
        </div>`;
    };

    const modal = document.createElement('div');
    modal.id = 'upgrade-modal';
    modal.className = 'hidden';
    modal.addEventListener('click', e => { if (e.target === modal) closeUpgradeModal(); });
    modal.innerHTML = `
        <div id="upgrade-modal-box">
            <div id="upgrade-modal-header">
                <div>
                    <h2>Choose your plan</h2>
                    <p>Unlock powerful features to take control of your finances.</p>
                </div>
                <button id="upgrade-modal-close" onclick="closeUpgradeModal()" aria-label="Close">✕</button>
            </div>
            <div id="upgrade-modal-body">
                <div class="plan-cards">
                    <!-- Free -->
                    <div class="plan-card">
                        <div>
                            <div class="plan-name">Free</div>
                            <div class="plan-price"><strong>$0</strong>forever</div>
                        </div>
                        <div class="plan-features">
                            ${features.map(f => featureRow(f, false)).join('')}
                        </div>
                        <div class="plan-cta">
                            <div style="font-size:0.75rem;color:#9ca3af;text-align:center;padding:0.5rem 0;">Your current plan</div>
                        </div>
                    </div>
                    <!-- Pro -->
                    <div class="plan-card pro">
                        <div class="plan-badge">Recommended</div>
                        <div>
                            <div class="plan-name">Pro</div>
                            <div class="plan-price"><strong>$9</strong>per month</div>
                        </div>
                        <div class="plan-features">
                            ${features.map(f => featureRow(f, true)).join('')}
                        </div>
                        <div class="plan-cta">
                            <button id="modal-upgrade-btn" class="btn-primary" style="width:100%;justify-content:center;border-radius:9999px;"
                                onclick="handleUpgradeClick(this)">
                                Upgrade to Pro →
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>`;
    document.body.appendChild(modal);

    // Floating button — only for FREE users
    if (!isProUser()) {
        const btn = document.createElement('button');
        btn.id = 'upgrade-float-btn';
        btn.title = 'Upgrade to Pro';
        btn.setAttribute('aria-label', 'Upgrade to Pro plan');
        btn.innerHTML = `<svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M5 10l7-7m0 0l7 7m-7-7v18"/></svg>Upgrade Plan`;
        btn.addEventListener('click', openUpgradeModal);
        document.body.appendChild(btn);
    }
}

async function handleUpgradeClick(btn) {
    if (btn) { btn.disabled = true; btn.classList.add('btn-loading'); }
    try {
        const res = await apiPost('/payments/checkout', {});
        if (res?.url) {
            sessionStorage.removeItem('_planState');
            window.location.href = res.url;
        } else {
            showError('Could not start checkout. Please try again.');
        }
    } catch (_) {
        showError('Could not start checkout. Please check Stripe configuration.');
    } finally {
        if (btn) { btn.disabled = false; btn.classList.remove('btn-loading'); }
    }
}

// ─── Feature gate overlay ─────────────────────────────────────────────────────
/**
 * Applies a lock overlay to `el` for FREE users.
 * Call after plan state is known. No-ops for PRO users.
 */
function featureGate(el, message = 'Upgrade to Pro to unlock this feature') {
    if (!el || isProUser()) return;
    el.classList.add('feature-gated');
    el.style.position = 'relative';
    const overlay = document.createElement('div');
    overlay.className = 'feature-gate-overlay';
    overlay.innerHTML = `
        <div class="feature-gate-content">
            <span class="feature-gate-lock">🔒</span>
            <p class="feature-gate-message">${message}</p>
            <button class="feature-gate-btn" onclick="openUpgradeModal()">Upgrade to Pro</button>
        </div>`;
    el.appendChild(overlay);
}

// ─── Toast notifications ──────────────────────────────────────────────────────
(function initToastContainer() {
    const el = document.createElement('div');
    el.id = 'toast-container';
    document.body.appendChild(el);
})();

function toast(message, type = 'info', duration = 3500) {
    const icons = { success: '✅', error: '❌', warning: '⚠️', info: 'ℹ️' };
    const container = document.getElementById('toast-container');
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

function dismissToast(el) {
    if (!el || el.classList.contains('toast-out')) return;
    el.classList.add('toast-out');
    // Safety fallback: remove after animation duration even if animationend doesn't fire
    const cleanup = () => { if (el.parentNode) el.remove(); };
    el.addEventListener('animationend', cleanup, { once: true });
    setTimeout(cleanup, 400);
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
document.addEventListener('DOMContentLoaded', async () => {
    injectSidebar();
    injectCurrencySelector(); // fallback for pages without a nav
    fetchExchangeRates();

    // Clear cached plan state on successful Stripe redirect so it re-fetches
    if (new URLSearchParams(window.location.search).get('subscription') === 'success') {
        sessionStorage.removeItem('_planState');
    }

    await fetchPlanState();
    injectUpgradeUI();

    // Inject admin nav item for ADMIN role users
    try {
        const me = await apiGet('/user/me');
        if (me && me.role === 'ADMIN') {
            const nav = document.querySelector('#app-sidebar .sidebar-nav');
            if (nav && !nav.querySelector('a[href="/admin"]')) {
                const a = document.createElement('a');
                a.href = '/admin';
                a.className = 'sidebar-nav-item' + (window.location.pathname === '/admin' ? ' active' : '');
                a.innerHTML = `<span class="sidebar-nav-icon">
                    <svg fill="none" stroke="currentColor" viewBox="0 0 24 24" class="icon-sm">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                            d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"/>
                    </svg>
                </span>
                <span class="sidebar-nav-label">Admin</span>`;
                nav.appendChild(a);
            }
        }
    } catch (_) { /* not admin or not logged in */ }
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
    // Subscription / plan
    fetchPlanState, isProUser, openUpgradeModal, closeUpgradeModal,
    handleUpgradeClick, featureGate,
});
