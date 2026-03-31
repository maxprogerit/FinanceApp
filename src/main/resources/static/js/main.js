/**
 * main.js — Application entry point (ES6 module).
 * Replaces app.js on pages that have been migrated to the module architecture.
 * Initialises sidebar, exchange rates, plan state, and the upgrade modal.
 *
 * Pages still using the legacy app.js are unaffected — they can be migrated
 * page-by-page by swapping <script src="/js/app.js"> for
 * <script type="module" src="/js/main.js">.
 */

import { apiGet, apiPost, apiGet as _apiGet } from './core/api.js';
import {
    getCurrentCurrency, setCurrentCurrency, setExchangeRates,
    SUPPORTED_CURRENCIES, STATIC_RATES, convertAmount, formatCurrency,
    getPlanState, setPlanState, isProUser,
} from './core/state.js';
import {
    fetchPlanState, openUpgradeModal, closeUpgradeModal,
    handleUpgradeClick, featureGate,
} from './core/auth.js';
import { injectSidebar, injectAdminNavItem } from './ui/sidebar.js';
import { toast, dismissToast, showSuccess, showError, showWarning, showInfo } from './ui/toast.js';

// ── Tailwind dark-mode config ─────────────────────────────────────────────────
if (window.tailwind) tailwind.config = { darkMode: 'class' };

// ── Formatters (used by feature modules) ──────────────────────────────────────
export function formatDate(dateStr) {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });
}

export function formatDateTime(dateStr) {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleString('en-US', {
        year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
    });
}

export function daysUntil(dateStr) {
    if (!dateStr) return null;
    return Math.ceil((new Date(dateStr) - Date.now()) / 86_400_000);
}

export function setLoading(btn, loading) {
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

export function emptyStateHTML(icon, message) {
    return `<div class="empty-state"><div class="text-5xl mb-3">${icon}</div><p class="text-base">${message}</p></div>`;
}

export function makeTableSortable(tableId, onSort) {
    const table = document.getElementById(tableId);
    if (!table) return;
    let currentCol = -1, currentDir = 'asc';
    table.querySelectorAll('th.sortable').forEach((th, i) => {
        th.style.cursor = 'pointer';
        th.addEventListener('click', () => {
            table.querySelectorAll('th.sortable').forEach(h => h.classList.remove('sort-asc', 'sort-desc'));
            currentDir = (currentCol === i && currentDir === 'asc') ? 'desc' : 'asc';
            currentCol = i;
            th.classList.add(`sort-${currentDir}`);
            onSort(i, currentDir);
        });
    });
}

// ── Exchange-rate fetching ─────────────────────────────────────────────────────
async function fetchExchangeRates() {
    try {
        const rates = await apiGet('/currency/rates');
        if (rates && typeof rates === 'object') {
            setExchangeRates(rates);
            window.exchangeRates = rates; // keep in-sync for legacy scripts
        }
    } catch (_) { /* keep STATIC_RATES fallback */ }
}

// ── Upgrade modal ─────────────────────────────────────────────────────────────
function injectUpgradeUI() {
    if (!document.getElementById('app-sidebar'))  return;
    if (document.getElementById('upgrade-modal')) return;

    const features = [
        { label: 'Transaction tracking',  free: true,  pro: true },
        { label: 'Budgets & goals',       free: true,  pro: true },
        { label: 'Investment tracking',   free: true,  pro: true },
        { label: 'Basic analytics',       free: true,  pro: true },
        { label: 'CSV import',            free: false, pro: true },
        { label: 'Export CSV / PDF',      free: false, pro: true },
        { label: 'AI-powered insights',   free: false, pro: true },
        { label: 'Spending forecast',     free: false, pro: true },
        { label: 'Auto-categorization',   free: false, pro: true },
        { label: 'Priority support',      free: false, pro: true },
    ];

    const row = (f, isPro) => {
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
                <button id="upgrade-modal-close" aria-label="Close">✕</button>
            </div>
            <div id="upgrade-modal-body">
                <div class="plan-cards">
                    <div class="plan-card">
                        <div><div class="plan-name">Free</div><div class="plan-price"><strong>$0</strong>forever</div></div>
                        <div class="plan-features">${features.map(f => row(f, false)).join('')}</div>
                        <div class="plan-cta"><div style="font-size:0.75rem;color:#9ca3af;text-align:center;padding:0.5rem 0;">Your current plan</div></div>
                    </div>
                    <div class="plan-card pro">
                        <div class="plan-badge">Recommended</div>
                        <div><div class="plan-name">Pro</div><div class="plan-price"><strong>$9</strong>per month</div></div>
                        <div class="plan-features">${features.map(f => row(f, true)).join('')}</div>
                        <div class="plan-cta">
                            <button id="modal-upgrade-btn" class="btn-primary" style="width:100%;justify-content:center;border-radius:9999px;">Upgrade to Pro →</button>
                        </div>
                    </div>
                </div>
            </div>
        </div>`;
    document.body.appendChild(modal);
    document.getElementById('upgrade-modal-close')?.addEventListener('click', closeUpgradeModal);
    document.getElementById('modal-upgrade-btn')?.addEventListener('click', e => handleUpgradeClick(e.currentTarget));

    if (!isProUser()) {
        const btn = document.createElement('button');
        btn.id = 'upgrade-float-btn';
        btn.title = 'Upgrade to Pro';
        btn.setAttribute('aria-label', 'Upgrade to Pro plan');
        btn.innerHTML = `<svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 10l7-7m0 0l7 7m-7-7v18"/></svg>Upgrade Plan`;
        btn.addEventListener('click', openUpgradeModal);
        document.body.appendChild(btn);
    }
}

// ── Init ──────────────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', async () => {
    injectSidebar();
    fetchExchangeRates();

    if (new URLSearchParams(window.location.search).get('subscription') === 'success') {
        sessionStorage.removeItem('_planState');
    }

    await fetchPlanState();
    injectUpgradeUI();

    try {
        const me = await apiGet('/user/me');
        if (me?.role === 'ADMIN') injectAdminNavItem();
    } catch (_) { /* not logged in / no role */ }
});

// ── Re-export everything for feature files that import from main.js ────────────
export {
    // API
    apiGet, apiPost,
    // State
    getCurrentCurrency, setCurrentCurrency, SUPPORTED_CURRENCIES, STATIC_RATES,
    convertAmount, formatCurrency, isProUser,
    // Auth
    fetchPlanState, openUpgradeModal, closeUpgradeModal, handleUpgradeClick, featureGate,
    // Toast
    toast, dismissToast, showSuccess, showError, showWarning, showInfo,
};

// ── Backward-compat: expose on window for non-module feature scripts ───────────
// Remove this block once all feature pages are migrated to ES6 modules.
import { apiGet as _ag, apiPost as _ap, apiPut as _apu, apiPatch as _apc, apiDelete as _ad, apiFetch as _af } from './core/api.js';
Object.assign(window, {
    API: '/api',
    currentCurrency: getCurrentCurrency(),
    STATIC_RATES, SUPPORTED_CURRENCIES,
    convertAmount, setCurrency: setCurrentCurrency, fetchExchangeRates,
    formatCurrency, formatDate, formatDateTime, daysUntil,
    toast, dismissToast, showSuccess, showError, showWarning, showInfo,
    setLoading, emptyStateHTML, makeTableSortable,
    apiFetch: _af, apiGet: _ag, apiPost: _ap, apiPut: _apu, apiPatch: _apc, apiDelete: _ad,
    fetchPlanState, isProUser, openUpgradeModal, closeUpgradeModal, handleUpgradeClick, featureGate,
});
