/**
 * features/investments.js — Investment portfolio page (ES6 module).
 *
 * Features added in this version:
 *  - Profit / % Change column with green/red colouring
 *  - Sortable columns (Profit, % Change, Value, Symbol)
 *  - Manual "Refresh Prices" button + 60-second auto-poll
 *  - Portfolio summary cards with live totals
 *  - Per-asset performance row
 *
 * Imports everything it needs from the module layer — no window.* globals used.
 */

import { apiGet, apiPost, apiPut, apiDelete } from '../core/api.js';
import { formatCurrency }                      from '../core/state.js';
import { showSuccess, showError }              from '../ui/toast.js';
import { openModal, closeModal }               from '../ui/modal.js';

// ── State ─────────────────────────────────────────────────────────────────────

let portfolioChart   = null;
let allInvestments   = [];   // last fetched array of InvestmentDTO objects
let sortCol          = null; // currently sorted column key
let sortDir          = 'asc';
let refreshIntervalId = null;

function setLoading(btn, loading) {
    if (!btn) return;
    if (loading) { btn.disabled = true; btn.classList.add('btn-loading'); }
    else         { btn.disabled = false; btn.classList.remove('btn-loading'); }
}

function formatDate(dateStr) {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });
}

// ── Init ──────────────────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('investmentDate').value = new Date().toISOString().split('T')[0];

    loadInvestments();
    startAutoPoll();

    document.getElementById('investmentForm').addEventListener('submit', handleFormSubmit);
    document.getElementById('refreshBtn')?.addEventListener('click', handleManualRefresh);

    // Close modal when clicking backdrop
    document.getElementById('investmentModal')?.addEventListener('click', e => {
        if (e.target === document.getElementById('investmentModal')) closeInvestmentModal();
    });

    // Sort header click delegation
    document.getElementById('investmentTableHead')?.addEventListener('click', e => {
        const th = e.target.closest('th[data-sort]');
        if (!th) return;
        const col = th.dataset.sort;
        sortDir = (sortCol === col && sortDir === 'asc') ? 'desc' : 'asc';
        sortCol = col;
        document.querySelectorAll('#investmentTableHead th[data-sort]').forEach(h => {
            h.classList.remove('sort-asc', 'sort-desc');
        });
        th.classList.add(`sort-${sortDir}`);
        renderTable(sortedInvestments());
    });
});

// ── Data loading ──────────────────────────────────────────────────────────────

async function loadInvestments() {
    try {
        const [investments, summary] = await Promise.all([
            apiGet('/investments'),
            apiGet('/investments/portfolio/summary'),
        ]);
        allInvestments = investments || [];
        renderSummary(summary);
        renderTable(sortedInvestments());
        renderChart(allInvestments);
    } catch (err) {
        showError('Failed to load investments. ' + (err.message || ''));
    }
}

async function handleManualRefresh() {
    const btn = document.getElementById('refreshBtn');
    setLoading(btn, true);
    btn.textContent = ''; // cleared by btn-loading spinner
    try {
        const result = await apiGet('/investments/refresh');
        const n = result?.updated ?? 0;
        if (n > 0) {
            showSuccess(`Updated ${n} price${n === 1 ? '' : 's'}.`);
            await loadInvestments();
        } else {
            showSuccess('Prices are up to date (or live data is disabled).');
        }
    } catch (err) {
        showError('Price refresh failed. ' + (err.message || ''));
    } finally {
        setLoading(btn, false);
        btn.textContent = '↻ Refresh Prices';
    }
}

function startAutoPoll() {
    // Refresh every 60 s; only runs while tab is visible to be polite with API quota
    if (refreshIntervalId) clearInterval(refreshIntervalId);
    refreshIntervalId = setInterval(async () => {
        if (document.hidden) return;
        try {
            const result = await apiGet('/investments/refresh');
            if (result?.updated > 0) await loadInvestments();
        } catch (_) { /* silent — no UX noise for background polling */ }
    }, 60_000);
}

// ── Sorting ───────────────────────────────────────────────────────────────────

function sortedInvestments() {
    if (!sortCol) return allInvestments;
    return [...allInvestments].sort((a, b) => {
        let va, vb;
        switch (sortCol) {
            case 'symbol':       va = a.symbol;              vb = b.symbol;              break;
            case 'value':        va = a.currentValue ?? 0;   vb = b.currentValue ?? 0;   break;
            case 'profit':       va = a.profitLoss ?? 0;     vb = b.profitLoss ?? 0;     break;
            case 'profitPct':    va = a.profitLossPercentage ?? 0; vb = b.profitLossPercentage ?? 0; break;
            default: return 0;
        }
        if (typeof va === 'string') return sortDir === 'asc' ? va.localeCompare(vb) : vb.localeCompare(va);
        return sortDir === 'asc' ? va - vb : vb - va;
    });
}

// ── Render: summary cards ─────────────────────────────────────────────────────

function renderSummary(s) {
    setText('portfolioTotal',    formatCurrency(s.totalPortfolioValue));
    setText('portfolioInvested', formatCurrency(s.totalInvestmentValue));

    const gl    = parseFloat(s.totalProfitLoss || 0);
    const glEl  = document.getElementById('portfolioGainLoss');
    if (glEl) {
        glEl.textContent = (gl >= 0 ? '+' : '') + formatCurrency(gl);
        const card = glEl.closest('.card');
        if (card) {
            card.classList.toggle('from-red-400', gl < 0);
            card.classList.toggle('to-red-600',   gl < 0);
            card.classList.toggle('from-green-400', gl >= 0);
            card.classList.toggle('to-green-600',   gl >= 0);
        }
    }

    const pct   = parseFloat(s.profitLossPercentage || 0);
    const pctEl = document.getElementById('portfolioReturn');
    if (pctEl) {
        pctEl.textContent = (pct >= 0 ? '+' : '') + pct.toFixed(2) + '%';
        pctEl.classList.toggle('text-green-200', pct >= 0);
        pctEl.classList.toggle('text-red-200',   pct < 0);
    }
}

function setText(id, value) {
    const el = document.getElementById(id);
    if (el) el.textContent = value;
}

// ── Render: holdings table ────────────────────────────────────────────────────

function renderTable(investments) {
    const tbody = document.getElementById('investmentTableBody');
    if (!tbody) return;

    if (!investments.length) {
        tbody.innerHTML = `<tr><td colspan="10" class="text-center py-8 text-gray-500 dark:text-gray-400">
            No investments yet. Add your first holding.</td></tr>`;
        return;
    }

    tbody.innerHTML = investments.map(inv => {
        const profitLoss = parseFloat(inv.profitLoss || 0);
        const profitPct  = parseFloat(inv.profitLossPercentage || 0);
        const glClass    = profitLoss >= 0
            ? 'text-green-600 dark:text-green-400'
            : 'text-red-600 dark:text-red-400';
        const sign       = profitLoss >= 0 ? '+' : '';
        const pctSign    = profitPct  >= 0 ? '+' : '';
        const arrow      = profitLoss >= 0 ? '▲' : '▼';

        return `<tr class="border-b border-gray-100 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors">
            <td class="py-3 px-2 font-semibold text-gray-900 dark:text-white">${inv.symbol}</td>
            <td class="py-3 px-2 text-gray-700 dark:text-gray-300 max-w-xs truncate" title="${inv.assetName}">${inv.assetName}</td>
            <td class="py-3 px-2">
                <span class="px-2 py-0.5 rounded text-xs bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200">${inv.assetType}</span>
            </td>
            <td class="py-3 px-2 text-right tabular-nums">${parseFloat(inv.quantity).toFixed(4)}</td>
            <td class="py-3 px-2 text-right tabular-nums">${formatCurrency(inv.purchasePrice, inv.currency)}</td>
            <td class="py-3 px-2 text-right tabular-nums">${formatCurrency(inv.currentPrice, inv.currency)}</td>
            <td class="py-3 px-2 text-right font-medium tabular-nums">${formatCurrency(inv.currentValue, inv.currency)}</td>
            <td class="py-3 px-2 text-right font-medium tabular-nums ${glClass}">${sign}${formatCurrency(profitLoss, inv.currency)}</td>
            <td class="py-3 px-2 text-right font-medium tabular-nums ${glClass}">${arrow} ${pctSign}${profitPct.toFixed(2)}%</td>
            <td class="py-3 px-2 text-center whitespace-nowrap">
                <button onclick="window._editInvestment(${inv.id})"   class="text-xs text-indigo-600 hover:text-indigo-800 dark:text-indigo-400 px-1">Edit</button>
                <button onclick="window._deleteInvestment(${inv.id})" class="text-xs text-red-600 hover:text-red-800 dark:text-red-400 px-1">Delete</button>
            </td>
        </tr>`;
    }).join('');
}

// ── Render: allocation doughnut ───────────────────────────────────────────────

function renderChart(investments) {
    const canvas = document.getElementById('portfolioChart');
    if (!canvas || !investments.length) return;

    const grouped = {};
    investments.forEach(inv => {
        const type  = inv.assetType || 'OTHER';
        const value = parseFloat(inv.currentValue || inv.purchasePrice || 0);
        grouped[type] = (grouped[type] || 0) + value;
    });

    const isDark = document.documentElement.classList.contains('dark');
    if (portfolioChart) portfolioChart.destroy();
    portfolioChart = new Chart(canvas.getContext('2d'), {
        type: 'doughnut',
        data: {
            labels: Object.keys(grouped),
            datasets: [{
                data: Object.values(grouped),
                backgroundColor: ['#6366f1','#22c55e','#f59e0b','#ef4444','#8b5cf6'],
                borderWidth: 2,
                borderColor: isDark ? '#1f2937' : '#fff',
            }],
        },
        options: {
            responsive: true,
            plugins: {
                legend: {
                    position: 'bottom',
                    labels: { color: isDark ? '#d1d5db' : '#374151', padding: 16 },
                },
                tooltip: {
                    callbacks: {
                        label: ctx => ` ${ctx.label}: ${formatCurrency(ctx.raw)}`,
                    },
                },
            },
        },
    });
}

// ── Modal: open / close / populate ───────────────────────────────────────────

function openInvestmentModal(inv = null) {
    document.getElementById('investmentModalTitle').textContent = inv ? 'Edit Investment' : 'Add Investment';
    document.getElementById('investmentId').value        = inv?.id || '';
    document.getElementById('investmentSymbol').value    = inv?.symbol || '';
    document.getElementById('investmentName').value      = inv?.assetName || '';
    document.getElementById('investmentType').value      = inv?.assetType || 'STOCK';
    document.getElementById('investmentShares').value    = inv?.quantity || '';
    document.getElementById('investmentPrice').value     = inv?.purchasePrice || '';
    document.getElementById('investmentDate').value      = inv?.purchaseDate?.split('T')[0]
        || new Date().toISOString().split('T')[0];
    document.getElementById('investmentModal').classList.remove('hidden');
}

function closeInvestmentModal() {
    document.getElementById('investmentModal').classList.add('hidden');
    document.getElementById('investmentForm').reset();
}

// ── Form submit ───────────────────────────────────────────────────────────────

async function handleFormSubmit(e) {
    e.preventDefault();
    const submitBtn = e.target.querySelector('[type="submit"]');
    setLoading(submitBtn, true);

    const id = document.getElementById('investmentId').value;
    const payload = {
        symbol:       document.getElementById('investmentSymbol').value.trim().toUpperCase(),
        assetName:    document.getElementById('investmentName').value.trim(),
        assetType:    document.getElementById('investmentType').value,
        quantity:     parseFloat(document.getElementById('investmentShares').value),
        purchasePrice:parseFloat(document.getElementById('investmentPrice').value),
        purchaseDate: document.getElementById('investmentDate').value + 'T00:00:00',
        currency:     'USD',
    };

    try {
        if (id) {
            await apiPut(`/investments/${id}`, payload);
            showSuccess('Investment updated.');
        } else {
            await apiPost('/investments', payload);
            showSuccess('Investment added.');
        }
        closeInvestmentModal();
        await loadInvestments();
    } catch (err) {
        showError('Failed to save investment. ' + (err.message || ''));
    } finally {
        setLoading(submitBtn, false);
    }
}

// ── Edit / Delete (called from inline onclick attrs; bridge via window) ────────

async function editInvestment(id) {
    try {
        const inv = await apiGet(`/investments/${id}`);
        openInvestmentModal(inv);
    } catch (err) {
        showError('Failed to load investment.');
    }
}

async function deleteInvestment(id) {
    if (!confirm('Delete this investment?')) return;
    try {
        await apiDelete(`/investments/${id}`);
        showSuccess('Investment deleted.');
        await loadInvestments();
    } catch (err) {
        showError('Failed to delete investment.');
    }
}

// Expose to onclick attrs (safe minimal surface)
window._editInvestment   = editInvestment;
window._deleteInvestment = deleteInvestment;

// Expose to inline HTML buttons (Add Investment, Cancel)
window.openInvestmentModal  = openInvestmentModal;
window.closeInvestmentModal = closeInvestmentModal;
