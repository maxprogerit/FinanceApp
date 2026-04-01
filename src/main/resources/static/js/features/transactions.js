/**
 * features/transactions.js — Transactions page (ES6 module).
 *
 * Features:
 *  - Quick Add bar: type "500 coffee Enter" to add in under 3 s
 *  - Smart text parser: maps keywords to categories client-side
 *  - Full Add/Edit modal with Enter-to-submit, auto-focus, last-used memory
 *  - Scan Receipt (PRO+): upload an image → auto-fill modal
 *  - CSV Import (PRO+): bulk import via file upload
 *  - Filter by type, category, tag, and date range
 *  - Export CSV
 */

import { apiGet, apiPost, apiPut, apiDelete, apiUpload } from '../core/api.js';
import { formatCurrency, getCurrentCurrency, getCurrentUser, isProUser } from '../core/state.js';
import { showSuccess, showError, showInfo }               from '../ui/toast.js';
import { openUpgradeModal }                               from '../core/auth.js';

// ── State ─────────────────────────────────────────────────────────────────────

let allTransactions = [];
let categories      = [];   // Category objects {id, name, type, icon}
let storageTypes    = [];   // {id, name, icon}
let incomeSources   = [];   // {id, name, icon}
let editingId       = null; // null = adding, number = editing

// ── Quick-Add text parser ─────────────────────────────────────────────────────

/** keyword → category mapping (mirrors backend TextParserService) */
const CATEGORY_KEYWORDS = [
    ['uber eats',   'Food & Drinks'],
    ['coffee',      'Food & Drinks'],
    ['cafe',        'Food & Drinks'],
    ['restaurant',  'Food & Drinks'],
    ['lunch',       'Food & Drinks'],
    ['dinner',      'Food & Drinks'],
    ['breakfast',   'Food & Drinks'],
    ['pizza',       'Food & Drinks'],
    ['burger',      'Food & Drinks'],
    ['sushi',       'Food & Drinks'],
    ['bakery',      'Food & Drinks'],
    ['kebab',       'Food & Drinks'],
    ['taxi',        'Transportation'],
    ['uber',        'Transportation'],
    ['lyft',        'Transportation'],
    ['bus',         'Transportation'],
    ['metro',       'Transportation'],
    ['train',       'Transportation'],
    ['parking',     'Transportation'],
    ['fuel',        'Transportation'],
    ['petrol',      'Transportation'],
    ['supermarket', 'Groceries'],
    ['groceries',   'Groceries'],
    ['lidl',        'Groceries'],
    ['kaufland',    'Groceries'],
    ['maxi',        'Groceries'],
    ['idea',        'Groceries'],
    ['aldi',        'Groceries'],
    ['spar',        'Groceries'],
    ['tesco',       'Groceries'],
    ['pharmacy',    'Health'],
    ['apoteka',     'Health'],
    ['doctor',      'Health'],
    ['dentist',     'Health'],
    ['medicine',    'Health'],
    ['gym',         'Sports & Fitness'],
    ['fitness',     'Sports & Fitness'],
    ['yoga',        'Sports & Fitness'],
    ['sport',       'Sports & Fitness'],
    ['netflix',     'Entertainment'],
    ['spotify',     'Entertainment'],
    ['cinema',      'Entertainment'],
    ['movie',       'Entertainment'],
    ['concert',     'Entertainment'],
    ['steam',       'Entertainment'],
    ['electricity', 'Utilities'],
    ['internet',    'Utilities'],
    ['phone bill',  'Utilities'],
    ['mobile',      'Utilities'],
    ['rent',        'Housing'],
    ['mortgage',    'Housing'],
    ['amazon',      'Shopping'],
    ['clothes',     'Shopping'],
    ['shopping',    'Shopping'],
    ['salary',      'Salary'],
    ['payroll',     'Salary'],
    ['wage',        'Salary'],
    ['paycheck',    'Salary'],
    ['freelance',   'Freelance'],
    ['invoice',     'Freelance'],
    ['dividend',    'Investments'],
    ['interest',    'Investments'],
];

const INCOME_KEYWORDS = ['salary','payroll','wage','paycheck','freelance','dividend','received','income','refund','cashback'];

function categorizeText(text) {
    if (!text) return 'Other';
    const lower = text.toLowerCase();
    for (const [kw, cat] of CATEGORY_KEYWORDS) {
        if (lower.includes(kw)) return cat;
    }
    return 'Other';
}

/**
 * Parses "500 coffee", "+30000 salary", "-800 rent" etc.
 * Returns {amount, type, category, description} or null if invalid.
 */
function parseQuickInput(input) {
    if (!input?.trim()) return null;
    const m = input.trim().match(/^([+\-]?\d+(?:[.,]\d+)?)\s*(.*)$/);
    if (!m) return null;

    const amountStr  = m[1].replace(',', '.');
    const rest       = m[2].trim();
    const rawAmount  = parseFloat(amountStr);
    const amount     = Math.abs(rawAmount);

    let type;
    if (amountStr.startsWith('+')) {
        type = 'INCOME';
    } else if (amountStr.startsWith('-')) {
        type = 'EXPENSE';
    } else {
        const lower = rest.toLowerCase();
        type = INCOME_KEYWORDS.some(k => lower.includes(k)) ? 'INCOME' : 'EXPENSE';
    }

    return { amount, type, category: categorizeText(rest), description: rest || null };
}

// ── Persistent preferences ────────────────────────────────────────────────────

const PREFS_KEY = 'txn_prefs';

function getPrefs() {
    try { return JSON.parse(localStorage.getItem(PREFS_KEY)) || {}; } catch { return {}; }
}

function savePrefs(patch) {
    localStorage.setItem(PREFS_KEY, JSON.stringify({ ...getPrefs(), ...patch }));
}

// ── Init ──────────────────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', async () => {
    await Promise.all([loadLookups(), loadTransactions()]);
    renderCategoryFilter();
    bindEvents();
});

// ── Data loading ──────────────────────────────────────────────────────────────

async function loadTransactions() {
    try {
        allTransactions = await apiGet('/transactions') || [];
        renderTable();
    } catch (err) {
        showError('Failed to load transactions. ' + (err.message || ''));
    }
}

async function loadLookups() {
    try {
        [categories, storageTypes, incomeSources] = await Promise.all([
            apiGet('/categories').catch(() => []),
            apiGet('/storage-types').catch(() => []),
            apiGet('/income-sources').catch(() => []),
        ]);
    } catch (_) { /* dropdowns degrade gracefully */ }
}

// ── Table render ──────────────────────────────────────────────────────────────

function applyFilters() {
    const type      = document.getElementById('filterType')?.value      || '';
    const category  = document.getElementById('filterCategory')?.value  || '';
    const tag       = (document.getElementById('filterTag')?.value      || '').toLowerCase().trim();
    const startDate = document.getElementById('filterStartDate')?.value || '';
    const endDate   = document.getElementById('filterEndDate')?.value   || '';

    return allTransactions.filter(t => {
        if (type     && t.type     !== type)                          return false;
        if (category && t.category !== category)                      return false;
        if (tag      && !(t.tags || '').toLowerCase().includes(tag))  return false;
        if (startDate && t.transactionDate < startDate + 'T00:00:00') return false;
        if (endDate   && t.transactionDate > endDate   + 'T23:59:59') return false;
        return true;
    });
}

function renderTable() {
    const tbody     = document.getElementById('transactionsTable');
    if (!tbody) return;
    const filtered  = applyFilters();
    const currency  = getCurrentCurrency();

    if (!filtered.length) {
        tbody.innerHTML = `<tr><td colspan="8" class="text-center py-10 text-gray-400">No transactions found.</td></tr>`;
        return;
    }

    tbody.innerHTML = filtered
        .sort((a, b) => new Date(b.transactionDate) - new Date(a.transactionDate))
        .map(t => {
            const isExpense = t.type === 'EXPENSE';
            const amtClass  = isExpense ? 'text-red-600 dark:text-red-400' : 'text-green-600 dark:text-green-400';
            const sign      = isExpense ? '-' : '+';
            const date      = new Date(t.transactionDate).toLocaleDateString('en-US', {
                year: 'numeric', month: 'short', day: 'numeric',
            });
            const tags = (t.tags || '').split(',').map(s => s.trim()).filter(Boolean)
                .map(tag => `<span class="inline-block bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 text-xs px-1.5 py-0.5 rounded mr-1">${tag}</span>`)
                .join('');

            return `<tr class="border-b border-gray-100 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors">
                <td class="px-4 py-3 text-sm text-gray-600 dark:text-gray-300 whitespace-nowrap">${date}</td>
                <td class="px-4 py-3">
                    <span class="px-2 py-0.5 rounded-full text-xs font-medium ${isExpense ? 'bg-red-100 dark:bg-red-900/40 text-red-700 dark:text-red-300' : 'bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-300'}">
                        ${t.type}
                    </span>
                </td>
                <td class="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">${t.category || '—'}</td>
                <td class="px-4 py-3 text-sm text-gray-500 dark:text-gray-400">${t.storageType || '—'}</td>
                <td class="px-4 py-3 text-sm text-gray-700 dark:text-gray-300 max-w-xs truncate" title="${t.description || ''}">${t.description || '—'}</td>
                <td class="px-4 py-3 text-sm">${tags || '—'}</td>
                <td class="px-4 py-3 text-sm font-semibold ${amtClass} text-right tabular-nums">
                    ${sign}${formatCurrency(t.amount, t.currency)}
                </td>
                <td class="px-4 py-3 text-right whitespace-nowrap">
                    <button onclick="window._editTransaction(${t.id})"
                            class="text-xs text-indigo-600 hover:text-indigo-800 dark:text-indigo-400 px-1 py-0.5">Edit</button>
                    <button onclick="window._deleteTransaction(${t.id})"
                            class="text-xs text-red-600 hover:text-red-800 dark:text-red-400 px-1 py-0.5">Delete</button>
                </td>
            </tr>`;
        }).join('');
}

function renderCategoryFilter() {
    const sel = document.getElementById('filterCategory');
    if (!sel) return;
    const unique = [...new Set(allTransactions.map(t => t.category).filter(Boolean))].sort();
    sel.innerHTML = '<option value="">All Categories</option>'
        + unique.map(c => `<option value="${c}">${c}</option>`).join('');
}

// ── Category dropdown helpers ─────────────────────────────────────────────────

function populateCategoryDropdown(selectId, filterType = null) {
    const sel = document.getElementById(selectId);
    if (!sel) return;
    let cats = categories.filter(c => c.isActive !== false);
    if (filterType) cats = cats.filter(c => !c.type || c.type === filterType);
    sel.innerHTML = cats.map(c =>
        `<option value="${c.name}">${c.icon ? c.icon + ' ' : ''}${c.name}</option>`
    ).join('');
}

function populateStorageDropdown(selectId) {
    const sel = document.getElementById(selectId);
    if (!sel) return;
    sel.innerHTML = '<option value="">— None —</option>'
        + storageTypes.map(s => `<option value="${s.name}">${s.icon ? s.icon + ' ' : ''}${s.name}</option>`).join('');
}

function populateIncomeSourceDropdown(selectId) {
    const sel = document.getElementById(selectId);
    if (!sel) return;
    sel.innerHTML = '<option value="">— None —</option>'
        + incomeSources.map(s => `<option value="${s.name}">${s.icon ? s.icon + ' ' : ''}${s.name}</option>`).join('');
}

// ── Quick Add ─────────────────────────────────────────────────────────────────

function updateQuickHint(input) {
    const hint = document.getElementById('quickAddHint');
    if (!hint) return;
    if (!input.trim()) { hint.textContent = ''; return; }

    const parsed = parseQuickInput(input);
    if (!parsed) { hint.textContent = ''; return; }

    hint.textContent = `${parsed.type === 'INCOME' ? '↑ Income' : '↓ Expense'} · ${parsed.category} · ${parsed.amount}`;
    hint.className = `text-xs mt-1 ${parsed.type === 'INCOME' ? 'text-green-600 dark:text-green-400' : 'text-amber-600 dark:text-amber-400'}`;
}

async function handleQuickAdd(input) {
    const parsed = parseQuickInput(input.trim());
    if (!parsed) { showError('Could not parse input. Try "500 coffee" or "+30000 salary".'); return; }

    const btn = document.getElementById('quickAddBtn');
    if (btn) { btn.disabled = true; btn.classList.add('btn-loading'); }

    try {
        await apiPost('/transactions/quick', {
            amount:      parsed.amount,
            type:        parsed.type,
            category:    parsed.category !== 'Other' ? parsed.category : null,
            description: parsed.description,
        });
        showSuccess(`${parsed.type === 'INCOME' ? 'Income' : 'Expense'} added (${parsed.category})`);
        document.getElementById('quickAddInput').value = '';
        document.getElementById('quickAddHint').textContent = '';
        savePrefs({ lastCategory: parsed.category });
        await loadTransactions();
        renderCategoryFilter();
    } catch (err) {
        showError('Failed to add transaction. ' + (err.message || ''));
    } finally {
        if (btn) { btn.disabled = false; btn.classList.remove('btn-loading'); }
    }
}

// ── Modal ─────────────────────────────────────────────────────────────────────

function openAddModal() {
    editingId = null;
    document.getElementById('modalTitle').textContent = 'Add Transaction';
    resetForm();

    // Re-populate dropdowns
    const typeEl = document.getElementById('type');
    populateCategoryDropdown('category', typeEl?.value || null);
    populateStorageDropdown('storageType');
    populateIncomeSourceDropdown('incomeSource');

    // Restore last-used preferences
    const prefs = getPrefs();
    if (prefs.lastCategory) setSelectValue('category', prefs.lastCategory);
    if (prefs.lastStorage)  setSelectValue('storageType', prefs.lastStorage);

    // Default currency to user's base currency (from settings)
    const baseCurrency = getCurrentUser()?.baseCurrency || getCurrentCurrency() || 'EUR';
    setSelectValue('currency', baseCurrency);

    // Default date to now
    const now = new Date();
    now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
    const field = document.getElementById('transactionDate');
    if (field) field.value = now.toISOString().slice(0, 16);

    showModal();
    // Auto-focus amount after modal is visible
    setTimeout(() => document.getElementById('amount')?.focus(), 60);
}

async function openEditModal(id) {
    editingId = id;
    try {
        const t = allTransactions.find(x => x.id === id) || await apiGet(`/transactions/${id}`);
        document.getElementById('modalTitle').textContent = 'Edit Transaction';
        resetForm();

        populateCategoryDropdown('category', t.type);
        populateStorageDropdown('storageType');
        populateIncomeSourceDropdown('incomeSource');

        document.getElementById('type').value        = t.type        || 'EXPENSE';
        document.getElementById('amount').value      = t.amount      || '';
        setSelectValue('currency', t.currency || getCurrentUser()?.baseCurrency || 'EUR');
        setSelectValue('category', t.category        || '');
        setSelectValue('storageType', t.storageType  || '');
        setSelectValue('incomeSource', t.incomeSource|| '');
        document.getElementById('description').value    = t.description    || '';
        document.getElementById('tags').value           = t.tags           || '';
        document.getElementById('isRecurring').checked  = !!t.isRecurring;
        if (t.recurringFrequency) setSelectValue('recurringFrequency', t.recurringFrequency);
        document.getElementById('transactionDate').value = t.transactionDate
            ? t.transactionDate.slice(0, 16) : '';

        toggleIncomeSourceField();
        toggleRecurringOptions();
        showModal();
        setTimeout(() => document.getElementById('amount')?.focus(), 60);
    } catch (err) {
        showError('Failed to load transaction.');
    }
}

function closeTransactionModal() {
    document.getElementById('transactionModal').classList.add('hidden');
    document.body.style.overflow = '';
}

function showModal() {
    document.getElementById('transactionModal').classList.remove('hidden');
    document.body.style.overflow = 'hidden';
}

function resetForm() {
    document.getElementById('transactionForm').reset();
    document.getElementById('transactionId').value = '';
    document.getElementById('recurringOptions').classList.add('hidden');
    document.getElementById('incomeSourceField').classList.add('hidden');
}

function toggleIncomeSourceField() {
    const type = document.getElementById('type')?.value;
    const field = document.getElementById('incomeSourceField');
    if (field) field.classList.toggle('hidden', type !== 'INCOME');
}

function toggleRecurringOptions() {
    const checked = document.getElementById('isRecurring')?.checked;
    document.getElementById('recurringOptions')?.classList.toggle('hidden', !checked);
}

function setSelectValue(id, value) {
    const sel = document.getElementById(id);
    if (!sel || !value) return;
    const opt = [...sel.options].find(o => o.value === value);
    if (opt) sel.value = value;
}

// ── Form submit ───────────────────────────────────────────────────────────────

async function handleFormSubmit(e) {
    e.preventDefault();
    const submitBtn = e.target.querySelector('[type="submit"]');
    if (submitBtn) { submitBtn.disabled = true; submitBtn.classList.add('btn-loading'); }

    const payload = {
        type:               document.getElementById('type').value,
        amount:             parseFloat(document.getElementById('amount').value),
        currency:           document.getElementById('currency').value,
        category:           document.getElementById('category').value,
        storageType:        document.getElementById('storageType').value || null,
        incomeSource:       document.getElementById('incomeSource').value || null,
        description:        document.getElementById('description').value || null,
        tags:               document.getElementById('tags').value || null,
        transactionDate:    document.getElementById('transactionDate').value
                                ? document.getElementById('transactionDate').value + ':00'
                                : new Date().toISOString(),
        isRecurring:        document.getElementById('isRecurring').checked,
        recurringFrequency: document.getElementById('recurringFrequency').value || null,
    };

    // Save preferences for next time
    if (payload.category)    savePrefs({ lastCategory: payload.category });
    if (payload.storageType) savePrefs({ lastStorage:  payload.storageType });

    try {
        if (editingId) {
            await apiPut(`/transactions/${editingId}`, payload);
            showSuccess('Transaction updated.');
        } else {
            await apiPost('/transactions', payload);
            showSuccess('Transaction added.');
        }
        closeTransactionModal();
        await loadTransactions();
        renderCategoryFilter();
    } catch (err) {
        showError('Failed to save transaction. ' + (err.message || ''));
    } finally {
        if (submitBtn) { submitBtn.disabled = false; submitBtn.classList.remove('btn-loading'); }
    }
}

// ── Delete ────────────────────────────────────────────────────────────────────

async function deleteTransaction(id) {
    if (!confirm('Delete this transaction?')) return;
    try {
        await apiDelete(`/transactions/${id}`);
        showSuccess('Transaction deleted.');
        await loadTransactions();
        renderCategoryFilter();
    } catch (err) {
        showError('Failed to delete transaction.');
    }
}

// ── CSV Import (PRO+) ─────────────────────────────────────────────────────────

async function handleCsvImport(file) {
    if (!isProUser()) { openUpgradeModal(); return; }
    const form = new FormData();
    form.append('file', file);
    form.append('currency', getCurrentCurrency() || 'USD');
    try {
        const result = await apiUpload('/import/csv', form);
        showSuccess(`Imported ${result.imported ?? 0} transactions.`);
        if (result.skipped > 0) showInfo(`${result.skipped} rows skipped.`);
        await loadTransactions();
        renderCategoryFilter();
    } catch (err) {
        if (err.status === 403) { openUpgradeModal(); return; }
        showError('CSV import failed. ' + (err.message || ''));
    }
}

// ── Scan Receipt (PRO+) ───────────────────────────────────────────────────────

async function handleScanReceipt(file) {
    if (!isProUser()) { openUpgradeModal(); return; }

    const scanBtn = document.getElementById('scanReceiptBtn');
    if (scanBtn) { scanBtn.disabled = true; scanBtn.classList.add('btn-loading'); }

    const form = new FormData();
    form.append('image', file);
    try {
        const parsed = await apiUpload('/transactions/scan-receipt', form);
        // Populate the modal with scanned data
        if (parsed.amount)      document.getElementById('amount').value      = parsed.amount;
        if (parsed.category)    setSelectValue('category',    parsed.category);
        if (parsed.description) document.getElementById('description').value = parsed.description;
        if (parsed.currency)    setSelectValue('currency',    parsed.currency);
        showSuccess('Receipt scanned — please review and save.');
    } catch (err) {
        if (err.status === 403) { openUpgradeModal(); return; }
        showError('Scan failed. ' + (err.message || ''));
    } finally {
        if (scanBtn) { scanBtn.disabled = false; scanBtn.classList.remove('btn-loading'); }
    }
}

// ── CSV Export ────────────────────────────────────────────────────────────────

function exportCSV() {
    if (!isProUser()) { openUpgradeModal(); return; }
    const start = document.getElementById('filterStartDate')?.value || '';
    const end   = document.getElementById('filterEndDate')?.value   || '';
    let url = '/api/export/transactions/csv';
    if (start) url += `?startDate=${start}T00:00:00`;
    if (end)   url += `${start ? '&' : '?'}endDate=${end}T23:59:59`;
    window.location.href = url;
}

// ── Event binding ─────────────────────────────────────────────────────────────

function bindEvents() {
    // Quick Add bar
    const quickInput = document.getElementById('quickAddInput');
    if (quickInput) {
        quickInput.addEventListener('input',   e => updateQuickHint(e.target.value));
        quickInput.addEventListener('keydown', e => {
            if (e.key === 'Enter') { e.preventDefault(); handleQuickAdd(e.target.value); }
        });
    }
    document.getElementById('quickAddBtn')?.addEventListener('click', () => {
        handleQuickAdd(document.getElementById('quickAddInput')?.value || '');
    });

    // Toolbar buttons
    document.getElementById('addTransactionBtn')?.addEventListener('click', openAddModal);
    document.getElementById('importCsvBtn')?.addEventListener('click', () =>
        document.getElementById('csvFileInput')?.click());
    document.getElementById('csvFileInput')?.addEventListener('change', e => {
        const file = e.target.files?.[0];
        if (file) { handleCsvImport(file); e.target.value = ''; }
    });

    // Export CSV button
    document.getElementById('exportCSV')?.addEventListener('click', exportCSV);

    // Scan receipt (hidden file input inside modal)
    document.getElementById('scanReceiptBtn')?.addEventListener('click', () =>
        document.getElementById('receiptFileInput')?.click());
    document.getElementById('receiptFileInput')?.addEventListener('change', e => {
        const file = e.target.files?.[0];
        if (file) { handleScanReceipt(file); e.target.value = ''; }
    });

    // Modal close
    document.getElementById('closeModal')?.addEventListener('click',  closeTransactionModal);
    document.getElementById('cancelBtn')?.addEventListener('click',   closeTransactionModal);
    document.getElementById('transactionModal')?.addEventListener('click', e => {
        if (e.target === document.getElementById('transactionModal')) closeTransactionModal();
    });

    // Form submit
    document.getElementById('transactionForm')?.addEventListener('submit', handleFormSubmit);

    // Type field → repopulate categories + toggle income source
    document.getElementById('type')?.addEventListener('change', () => {
        populateCategoryDropdown('category', document.getElementById('type').value);
        toggleIncomeSourceField();
    });

    // Recurring checkbox
    document.getElementById('isRecurring')?.addEventListener('change', toggleRecurringOptions);

    // Filters
    document.getElementById('applyFilters')?.addEventListener('click', renderTable);
    document.getElementById('clearFilters')?.addEventListener('click', () => {
        ['filterType','filterCategory','filterTag','filterStartDate','filterEndDate']
            .forEach(id => { const el = document.getElementById(id); if (el) el.value = ''; });
        renderTable();
    });
}

// ── Window bridge (for inline onclick attrs) ──────────────────────────────────

window._editTransaction       = openEditModal;
window._deleteTransaction     = deleteTransaction;
window.openAddTransactionModal = openAddModal;
window.closeTransactionModal   = closeTransactionModal;
