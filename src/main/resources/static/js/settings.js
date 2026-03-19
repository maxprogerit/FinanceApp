// Settings JavaScript — Smart Finance Dashboard
// NOTE: SUPPORTED_CURRENCIES, convertAmount, setCurrency, currentCurrency,
//       STATIC_RATES, fetchExchangeRates, showSuccess, showError,
//       apiGet, apiPost, apiPut, apiDelete are provided by app.js

document.addEventListener('DOMContentLoaded', async () => {
    loadSettings();
    updateDarkModeToggle();
    buildConverterSelects();
    initExportDates();
    await loadAndDisplayRates();
    loadStorageTypes();
    loadIncomeSources();
    loadCategorizationRules();
    loadForecast();
    loadPlanStatus();
});

// ── Persistence ───────────────────────────────────────────────────────────────
function loadSettings() {
    const select = document.getElementById('baseCurrency');
    if (select) select.value = currentCurrency;
}

function saveSettings() {
    const newCurrency = document.getElementById('baseCurrency').value;
    setCurrency(newCurrency);
    localStorage.setItem('currentCurrency', newCurrency);
    showSuccess(`Settings saved. Display currency set to ${newCurrency}.`);

    // Sync the nav selector if it exists on this page
    const navSelect = document.getElementById('globalCurrencySelect');
    if (navSelect) navSelect.value = newCurrency;
}

// ── Dark mode toggle ──────────────────────────────────────────────────────────
function updateDarkModeToggle() {
    const isDark  = document.documentElement.classList.contains('dark');
    const btn     = document.getElementById('darkModeToggle');
    const slider  = document.getElementById('darkModeSlider');
    if (!btn || !slider) return;
    if (isDark) {
        btn.classList.add('bg-indigo-600');
        btn.classList.remove('bg-gray-300');
        slider.classList.add('translate-x-7');
        slider.classList.remove('translate-x-1');
    } else {
        btn.classList.remove('bg-indigo-600');
        btn.classList.add('bg-gray-300');
        slider.classList.remove('translate-x-7');
        slider.classList.add('translate-x-1');
    }
}

function toggleDarkMode() {
    document.documentElement.classList.toggle('dark');
    const isDark = document.documentElement.classList.contains('dark');
    localStorage.setItem('theme', isDark ? 'dark' : 'light');
    updateDarkModeToggle();
}

// ── Exchange rates display ────────────────────────────────────────────────────
async function loadAndDisplayRates() {
    const grid   = document.getElementById('exchangeRatesGrid');
    const status = document.getElementById('ratesStatus');

    // Fetch real-time rates (app.js utility)
    await fetchExchangeRates();

    // Merge static + live
    const merged = Object.assign({}, STATIC_RATES, window.exchangeRates);
    const display = Object.entries(merged)
        .filter(([k]) => k !== 'USD')
        .sort(([a], [b]) => a.localeCompare(b));

    grid.innerHTML = display.map(([code, rate]) => {
        const isLive = window.exchangeRates && window.exchangeRates[code] !== undefined;
        return `
        <div class="bg-gray-50 dark:bg-gray-700 rounded-lg p-3">
            <p class="text-gray-500 dark:text-gray-400 text-xs font-medium">${code}
                ${isLive ? '<span class="text-green-500 ml-1">●</span>' : '<span class="text-yellow-500 ml-1">●</span>'}
            </p>
            <p class="font-semibold text-gray-900 dark:text-white">${parseFloat(rate).toFixed(4)}</p>
        </div>`;
    }).join('');

    const isLive = window.exchangeRates && Object.keys(window.exchangeRates).length > 0;
    status.textContent = isLive ? 'Live ● just updated' : 'Static rates (offline)';
    status.className = `text-xs ${isLive ? 'text-green-500' : 'text-yellow-500'}`;
}

// ── Currency Converter ────────────────────────────────────────────────────────
function buildConverterSelects() {
    const currencies = SUPPORTED_CURRENCIES;
    const fromSelect = document.getElementById('converterFrom');
    const toSelect   = document.getElementById('converterTo');
    if (!fromSelect || !toSelect) return;

    [fromSelect, toSelect].forEach((sel, idx) => {
        currencies.forEach(c => {
            const opt = document.createElement('option');
            opt.value = c;
            opt.textContent = c;
            // Default: From=USD, To=currentCurrency
            if (idx === 0 && c === 'USD') opt.selected = true;
            if (idx === 1 && c === currentCurrency) opt.selected = true;
            sel.appendChild(opt);
        });
    });
}

function doConvert() {
    const amountEl = document.getElementById('converterAmount');
    const from     = document.getElementById('converterFrom').value;
    const to       = document.getElementById('converterTo').value;
    const resultEl = document.getElementById('converterResult');
    const outputEl = document.getElementById('converterOutput');
    const rateEl   = document.getElementById('converterRate');

    const amount = parseFloat(amountEl.value);
    if (!amount || isNaN(amount) || amount <= 0) {
        resultEl.classList.add('hidden');
        return;
    }

    const converted = convertAmount(amount, from, to);

    // Format result using Intl directly (no double-conversion)
    let formatted;
    try {
        formatted = new Intl.NumberFormat('en-US', { style: 'currency', currency: to }).format(converted);
    } catch (_) {
        formatted = `${to} ${converted.toFixed(2)}`;
    }

    const merged = Object.assign({}, STATIC_RATES, window.exchangeRates);
    const fromRate = merged[from] ?? 1;
    const toRate   = merged[to]   ?? 1;
    const unitRate = toRate / fromRate;

    outputEl.textContent = `${amount} ${from} = ${formatted}`;
    rateEl.textContent   = `1 ${from} = ${unitRate.toFixed(6)} ${to}`;
    resultEl.classList.remove('hidden');
}

// ── Budget recalculation ──────────────────────────────────────────────────────
async function recalculateBudgets() {
    try {
        const result = await apiPost('/budgets/recalculate', {});
        showSuccess(result.message || 'Budgets recalculated.');
    } catch (err) {
        showError('Failed to recalculate budgets.');
    }
}

// ── Storage Types Management ──────────────────────────────────────────────────
async function loadStorageTypes() {
    const list = document.getElementById('storageTypesList');
    if (!list) return;
    try {
        const items = await apiGet('/storage-types');
        if (items.length === 0) {
            list.innerHTML = '<p class="text-gray-400 text-sm">No storage types yet.</p>';
            return;
        }
        list.innerHTML = items.map(st => `
            <div class="flex items-center justify-between p-3 bg-gray-50 dark:bg-gray-700 rounded-lg">
                <div class="flex items-center gap-2">
                    <span class="text-lg">${st.icon || '📦'}</span>
                    <span class="font-medium text-sm text-gray-900 dark:text-white">${st.name}</span>
                    ${st.builtIn ? '<span class="text-xs text-gray-400 dark:text-gray-500">(built-in)</span>' : ''}
                </div>
                <div class="flex gap-2">
                    ${!st.builtIn ? `<button onclick="editStorageType(${st.id},'${escQ(st.name)}','${escQ(st.icon || '')}')"
                        class="text-xs text-indigo-600 hover:text-indigo-800 dark:text-indigo-400">Edit</button>` : ''}
                    ${!st.builtIn ? `<button onclick="deleteStorageType(${st.id},'${escQ(st.name)}')"
                        class="text-xs text-red-600 hover:text-red-800 dark:text-red-400">Delete</button>` : ''}
                </div>
            </div>`).join('');
    } catch (e) {
        list.innerHTML = '<p class="text-red-400 text-sm">Failed to load storage types.</p>';
    }
}

function showAddStorageTypeModal() {
    document.getElementById('storageTypeModalTitle').textContent = 'Add Storage Type';
    document.getElementById('storageTypeId').value = '';
    document.getElementById('storageTypeName').value = '';
    document.getElementById('storageTypeIcon').value = '';
    document.getElementById('storageTypeModal').classList.remove('hidden');
}

function editStorageType(id, name, icon) {
    document.getElementById('storageTypeModalTitle').textContent = 'Edit Storage Type';
    document.getElementById('storageTypeId').value = id;
    document.getElementById('storageTypeName').value = name;
    document.getElementById('storageTypeIcon').value = icon;
    document.getElementById('storageTypeModal').classList.remove('hidden');
}

function closeStorageTypeModal() {
    document.getElementById('storageTypeModal').classList.add('hidden');
}

async function saveStorageType() {
    const id   = document.getElementById('storageTypeId').value;
    const name = document.getElementById('storageTypeName').value.trim();
    const icon = document.getElementById('storageTypeIcon').value.trim();
    if (!name) { showError('Name is required.'); return; }
    try {
        if (id) {
            await apiPut(`/storage-types/${id}`, { name, icon });
            showSuccess('Storage type updated.');
        } else {
            await apiPost('/storage-types', { name, icon });
            showSuccess('Storage type added.');
        }
        closeStorageTypeModal();
        loadStorageTypes();
    } catch (e) {
        showError(e.message || 'Failed to save storage type.');
    }
}

async function deleteStorageType(id, name) {
    if (!confirm(`Delete storage type "${name}"?`)) return;
    try {
        await apiDelete(`/storage-types/${id}`);
        showSuccess(`"${name}" deleted.`);
        loadStorageTypes();
    } catch (e) {
        showError(e.message || 'Cannot delete — it may be in use.');
    }
}

// ── Income Sources Management ─────────────────────────────────────────────────
async function loadIncomeSources() {
    const list = document.getElementById('incomeSourcesList');
    if (!list) return;
    try {
        const items = await apiGet('/income-sources');
        if (items.length === 0) {
            list.innerHTML = '<p class="text-gray-400 text-sm">No income sources yet.</p>';
            return;
        }
        list.innerHTML = items.map(src => `
            <div class="flex items-center justify-between p-3 bg-gray-50 dark:bg-gray-700 rounded-lg">
                <div class="flex items-center gap-2">
                    <span class="text-lg">${src.icon || '💰'}</span>
                    <span class="font-medium text-sm text-gray-900 dark:text-white">${src.name}</span>
                    ${src.builtIn ? '<span class="text-xs text-gray-400 dark:text-gray-500">(built-in)</span>' : ''}
                </div>
                <div class="flex gap-2">
                    ${!src.builtIn ? `<button onclick="editIncomeSource(${src.id},'${escQ(src.name)}','${escQ(src.icon || '')}')"
                        class="text-xs text-indigo-600 hover:text-indigo-800 dark:text-indigo-400">Edit</button>` : ''}
                    ${!src.builtIn ? `<button onclick="deleteIncomeSource(${src.id},'${escQ(src.name)}')"
                        class="text-xs text-red-600 hover:text-red-800 dark:text-red-400">Delete</button>` : ''}
                </div>
            </div>`).join('');
    } catch (e) {
        list.innerHTML = '<p class="text-red-400 text-sm">Failed to load income sources.</p>';
    }
}

function showAddIncomeSourceModal() {
    document.getElementById('incomeSourceModalTitle').textContent = 'Add Income Source';
    document.getElementById('incomeSourceId').value = '';
    document.getElementById('incomeSourceName').value = '';
    document.getElementById('incomeSourceIcon').value = '';
    document.getElementById('incomeSourceModal').classList.remove('hidden');
}

function editIncomeSource(id, name, icon) {
    document.getElementById('incomeSourceModalTitle').textContent = 'Edit Income Source';
    document.getElementById('incomeSourceId').value = id;
    document.getElementById('incomeSourceName').value = name;
    document.getElementById('incomeSourceIcon').value = icon;
    document.getElementById('incomeSourceModal').classList.remove('hidden');
}

function closeIncomeSourceModal() {
    document.getElementById('incomeSourceModal').classList.add('hidden');
}

async function saveIncomeSource() {
    const id   = document.getElementById('incomeSourceId').value;
    const name = document.getElementById('incomeSourceName').value.trim();
    const icon = document.getElementById('incomeSourceIcon').value.trim();
    if (!name) { showError('Name is required.'); return; }
    try {
        if (id) {
            await apiPut(`/income-sources/${id}`, { name, icon });
            showSuccess('Income source updated.');
        } else {
            await apiPost('/income-sources', { name, icon });
            showSuccess('Income source added.');
        }
        closeIncomeSourceModal();
        loadIncomeSources();
    } catch (e) {
        showError(e.message || 'Failed to save income source.');
    }
}

async function deleteIncomeSource(id, name) {
    if (!confirm(`Delete income source "${name}"?`)) return;
    try {
        await apiDelete(`/income-sources/${id}`);
        showSuccess(`"${name}" deleted.`);
        loadIncomeSources();
    } catch (e) {
        showError(e.message || 'Cannot delete — it may be in use.');
    }
}

// Escape single-quotes for inline onclick attributes
function escQ(str) { return str.replace(/'/g, "\\'"); }

// ── Categorization Rules Management ──────────────────────────────────────────
async function loadCategorizationRules() {
    const list = document.getElementById('categorizationRulesList');
    if (!list) return;
    try {
        const rules = await apiGet('/categorization-rules');
        if (rules.length === 0) {
            list.innerHTML = '<p class="text-gray-400 text-sm">No rules yet. Add a rule to auto-categorize imported transactions.</p>';
            return;
        }
        list.innerHTML = rules.map(r => `
            <div class="flex items-center justify-between p-3 bg-gray-50 dark:bg-gray-700 rounded-lg">
                <div class="flex items-center gap-3 flex-1 min-w-0">
                    <span class="font-mono text-sm bg-indigo-100 dark:bg-indigo-900 text-indigo-700 dark:text-indigo-300 px-2 py-0.5 rounded">${r.pattern}</span>
                    <span class="text-gray-400 text-xs">→</span>
                    <span class="text-sm font-medium text-gray-900 dark:text-white">${r.category}</span>
                    ${r.transactionType ? `<span class="text-xs text-gray-400">(${r.transactionType})</span>` : ''}
                    <span class="text-xs text-gray-400">priority: ${r.priority}</span>
                </div>
                <div class="flex gap-2 flex-shrink-0 ml-2">
                    <button onclick="editRule(${r.id},'${escQ(r.pattern)}','${escQ(r.category)}','${r.transactionType || ''}',${r.priority})"
                        class="text-xs text-indigo-600 hover:text-indigo-800 dark:text-indigo-400">Edit</button>
                    <button onclick="deleteRule(${r.id},'${escQ(r.pattern)}')"
                        class="text-xs text-red-600 hover:text-red-800 dark:text-red-400">Delete</button>
                </div>
            </div>`).join('');
    } catch (e) {
        list.innerHTML = '<p class="text-red-400 text-sm">Failed to load rules.</p>';
    }
}

async function loadRuleCategories() {
    const select = document.getElementById('ruleCategory');
    if (!select) return;
    try {
        const cats = await apiGet('/categories/active');
        select.innerHTML = cats.map(c => `<option value="${c.name}">${c.icon} ${c.name}</option>`).join('');
    } catch (e) {
        select.innerHTML = '<option value="Other">Other</option>';
    }
}

function showAddRuleModal() {
    document.getElementById('ruleModalTitle').textContent = 'Add Rule';
    document.getElementById('ruleId').value = '';
    document.getElementById('rulePattern').value = '';
    document.getElementById('ruleType').value = '';
    document.getElementById('rulePriority').value = '0';
    loadRuleCategories();
    document.getElementById('ruleModal').classList.remove('hidden');
}

function editRule(id, pattern, category, type, priority) {
    document.getElementById('ruleModalTitle').textContent = 'Edit Rule';
    document.getElementById('ruleId').value = id;
    document.getElementById('rulePattern').value = pattern;
    document.getElementById('ruleType').value = type || '';
    document.getElementById('rulePriority').value = priority;
    loadRuleCategories().then(() => {
        document.getElementById('ruleCategory').value = category;
    });
    document.getElementById('ruleModal').classList.remove('hidden');
}

function closeRuleModal() {
    document.getElementById('ruleModal').classList.add('hidden');
}

async function saveRule() {
    const id = document.getElementById('ruleId').value;
    const pattern = document.getElementById('rulePattern').value.trim();
    const category = document.getElementById('ruleCategory').value;
    const transactionType = document.getElementById('ruleType').value || null;
    const priority = parseInt(document.getElementById('rulePriority').value) || 0;
    if (!pattern) { showError('Pattern is required.'); return; }
    if (!category) { showError('Category is required.'); return; }
    try {
        if (id) {
            await apiPut(`/categorization-rules/${id}`, { pattern, category, transactionType, priority });
            showSuccess('Rule updated.');
        } else {
            await apiPost('/categorization-rules', { pattern, category, transactionType, priority });
            showSuccess('Rule added.');
        }
        closeRuleModal();
        loadCategorizationRules();
    } catch (e) {
        showError(e.message || 'Failed to save rule.');
    }
}

async function deleteRule(id, pattern) {
    if (!confirm(`Delete rule for pattern "${pattern}"?`)) return;
    try {
        await apiDelete(`/categorization-rules/${id}`);
        showSuccess(`Rule deleted.`);
        loadCategorizationRules();
    } catch (e) {
        showError(e.message || 'Failed to delete rule.');
    }
}

// ── Export & Reports ──────────────────────────────────────────────────────────
function initExportDates() {
    const now      = new Date();
    const firstDay = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().split('T')[0];
    const today    = now.toISOString().split('T')[0];
    const monthVal = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
    const fromEl   = document.getElementById('exportFrom');
    const toEl     = document.getElementById('exportTo');
    const mEl      = document.getElementById('reportMonth');
    if (fromEl) fromEl.value = firstDay;
    if (toEl)   toEl.value   = today;
    if (mEl)    mEl.value    = monthVal;
}

function exportCSV() {
    const from = document.getElementById('exportFrom')?.value;
    const to   = document.getElementById('exportTo')?.value;
    if (!from || !to) { showError('Please select a date range.'); return; }
    window.location.href = `${API}/export/transactions/csv?startDate=${encodeURIComponent(from + 'T00:00:00')}&endDate=${encodeURIComponent(to + 'T23:59:59')}`;
}

function exportPDF() {
    const monthVal = document.getElementById('reportMonth')?.value;
    if (!monthVal) { showError('Please select a month.'); return; }
    const [year, month] = monthVal.split('-');
    window.location.href = `${API}/export/report/pdf?year=${year}&month=${month}`;
}

async function loadForecast() {
    const container = document.getElementById('forecastContainer');
    if (!container) return;
    try {
        const data = await apiGet('/forecast/spending');
        if (!data || Object.keys(data).length === 0) {
            container.innerHTML = `<p class="text-sm text-gray-500 dark:text-gray-400">Not enough data yet. Add more transactions over multiple months.</p>`;
            return;
        }
        const riskLow = (data.riskLevel || '').toLowerCase() !== 'high';
        container.innerHTML = `
            <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div class="bg-blue-50 dark:bg-blue-900/20 rounded-lg p-4">
                    <p class="text-xs text-blue-600 dark:text-blue-400 font-medium">Predicted Spending</p>
                    <p class="text-xl font-bold text-blue-700 dark:text-blue-300 mt-1">${formatCurrency(data.predictedSpending || 0)}</p>
                </div>
                <div class="bg-emerald-50 dark:bg-emerald-900/20 rounded-lg p-4">
                    <p class="text-xs text-emerald-600 dark:text-emerald-400 font-medium">Monthly Average</p>
                    <p class="text-xl font-bold text-emerald-700 dark:text-emerald-300 mt-1">${formatCurrency(data.averageSpending || 0)}</p>
                </div>
                <div class="${riskLow ? 'bg-yellow-50 dark:bg-yellow-900/20' : 'bg-red-50 dark:bg-red-900/20'} rounded-lg p-4">
                    <p class="text-xs font-medium ${riskLow ? 'text-yellow-600 dark:text-yellow-400' : 'text-red-600 dark:text-red-400'}">Budget Risk</p>
                    <p class="text-xl font-bold mt-1 ${riskLow ? 'text-yellow-700 dark:text-yellow-300' : 'text-red-700 dark:text-red-300'}">${data.riskLevel || 'LOW'}</p>
                </div>
            </div>
            ${data.message ? `<p class="mt-3 text-xs text-gray-600 dark:text-gray-400">${data.message}</p>` : ''}`;
    } catch (_) {
        container.innerHTML = `<p class="text-sm text-gray-500 dark:text-gray-400">Could not load forecast.</p>`;
    }
}

// ── Subscription plan status ──────────────────────────────────────────────────
async function loadPlanStatus() {
    try {
        const status = await apiGet('/payments/status');
        const planEl = document.getElementById('planStatus');
        if (!planEl || !status) return;
        const plan     = status.plan || 'FREE';
        const subStatus = status.status || '';
        const isPro    = plan === 'PRO' && (subStatus === 'active' || subStatus === 'trialing');
        planEl.innerHTML = isPro
            ? `<span class="px-2 py-0.5 rounded-full text-xs font-semibold bg-indigo-100 dark:bg-indigo-900 text-indigo-700 dark:text-indigo-300">PRO</span>
               <span class="text-xs text-gray-500 dark:text-gray-400">${subStatus === 'trialing' ? 'Trial active' : 'Active subscription'}</span>`
            : `<span class="px-2 py-0.5 rounded-full text-xs font-semibold bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300">FREE</span>
               <span class="text-xs text-gray-500 dark:text-gray-400">Upgrade for CSV import and advanced features</span>`;
    } catch (_) { /* silent — payments may not be configured */ }
}

async function handleUpgrade(e) {
    e.preventDefault();
    try {
        const res = await apiPost('/payments/checkout', {});
        if (res?.url) window.location.href = res.url;
    } catch (_) { showError('Could not start checkout. Please check Stripe configuration.'); }
}
