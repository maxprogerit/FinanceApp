// analytics.js — Analytics page
// Depends on: app.js (formatCurrency, formatDate, daysUntil, apiGet/Post/Put/Patch/Delete, showSuccess, showError, setLoading, emptyStateHTML)

// ── Chart instances ────────────────────────────────────────────────────────────
let trendsChart      = null;
let storageChart     = null;
let incomeSourceChart = null;
let currentRange     = 'month';

// ── Color palettes ─────────────────────────────────────────────────────────────
const PALETTE = ['#6366F1','#10B981','#F59E0B','#EF4444','#8B5CF6','#06B6D4','#84CC16','#F97316','#EC4899','#64748B'];
const INC_COLOR = 'rgb(16,185,129)';   // green
const EXP_COLOR = 'rgb(239,68,68)';    // red

function isDark() { return document.documentElement.classList.contains('dark'); }
function chartColors() {
    return {
        grid:  isDark() ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.05)',
        ticks: isDark() ? '#9CA3AF' : '#6B7280',
        tooltip: {
            bg:    isDark() ? '#1F2937' : '#fff',
            border:isDark() ? '#374151' : '#E5E7EB',
            title: isDark() ? '#F9FAFB' : '#111827',
            body:  isDark() ? '#D1D5DB' : '#374151',
        }
    };
}

// ── Tabs ───────────────────────────────────────────────────────────────────────
const TAB_LOADERS = {
    overview:      loadOverview,
    budgets:       loadBudgets,
    goals:         loadGoals,
    subscriptions: loadSubscriptions,
    debts:         loadDebts,
};
const loadedTabs = new Set();

function switchTab(tabName) {
    document.querySelectorAll('.analytics-tab').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.tab === tabName);
    });
    document.querySelectorAll('.analytics-pane').forEach(pane => {
        pane.classList.toggle('hidden', pane.id !== `tab-${tabName}`);
    });
    if (!loadedTabs.has(tabName)) {
        loadedTabs.add(tabName);
        TAB_LOADERS[tabName]?.();
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// OVERVIEW
// ══════════════════════════════════════════════════════════════════════════════
async function loadOverview() {
    loadTrendsChart('month');
    loadStorageChart();
    loadIncomeSourceChart();
    loadCategoryTable();
    loadMonthlySummary();
}

// Income vs Expenses smooth line chart
async function loadTrendsChart(range) {
    currentRange = range;
    const { grid, ticks, tooltip } = chartColors();
    try {
        let labels, incomeData, expensesData;

        if (range === 'year') {
            const data    = await apiGet('/analytics/trends?months=12');
            const monthly = data.monthlyData || [];
            labels       = monthly.map(d => {
                const [y, m] = d.month.split('-');
                return new Date(y, m - 1).toLocaleDateString('en-US', { month: 'short', year: '2-digit' });
            });
            incomeData   = monthly.map(d => Number(d.income));
            expensesData = monthly.map(d => Number(d.expenses));
        } else if (range === 'day') {
            const data  = await apiGet('/analytics/trends/daily?days=1');
            const daily = data.dailyData || [];
            labels       = ['Today'];
            incomeData   = [daily.reduce((s, d) => s + Number(d.income), 0)];
            expensesData = [daily.reduce((s, d) => s + Number(d.expenses), 0)];
        } else {
            const days  = range === 'week' ? 7 : 30;
            const data  = await apiGet(`/analytics/trends/daily?days=${days}`);
            const daily = data.dailyData || [];
            labels       = daily.map(d => {
                const date = new Date(d.day + 'T00:00:00');
                return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
            });
            incomeData   = daily.map(d => Number(d.income));
            expensesData = daily.map(d => Number(d.expenses));
        }

        const canvas = document.getElementById('incomeExpensesChart');
        if (!canvas) return;
        if (trendsChart) trendsChart.destroy();

        trendsChart = new Chart(canvas.getContext('2d'), {
            type: 'line',
            data: {
                labels,
                datasets: [
                    {
                        label: 'Income',
                        data: incomeData,
                        borderColor: INC_COLOR,
                        backgroundColor: 'rgba(16,185,129,0.1)',
                        borderWidth: 2.5,
                        pointRadius: labels.length <= 14 ? 4 : 2,
                        pointHoverRadius: 6,
                        pointBackgroundColor: INC_COLOR,
                        tension: 0.4,
                        fill: true,
                    },
                    {
                        label: 'Expenses',
                        data: expensesData,
                        borderColor: EXP_COLOR,
                        backgroundColor: 'rgba(239,68,68,0.08)',
                        borderWidth: 2.5,
                        pointRadius: labels.length <= 14 ? 4 : 2,
                        pointHoverRadius: 6,
                        pointBackgroundColor: EXP_COLOR,
                        tension: 0.4,
                        fill: true,
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: { intersect: false, mode: 'index' },
                animation: { duration: 500 },
                plugins: {
                    legend: { position: 'bottom', labels: { color: ticks, usePointStyle: true, pointStyleWidth: 10, padding: 16 } },
                    tooltip: {
                        backgroundColor: tooltip.bg,
                        borderColor: tooltip.border,
                        borderWidth: 1,
                        titleColor: tooltip.title,
                        bodyColor: tooltip.body,
                        padding: 10,
                        callbacks: { label: c => ` ${c.dataset.label}: ${formatCurrency(c.raw)}` }
                    }
                },
                scales: {
                    x: { grid: { color: grid }, ticks: { color: ticks, maxTicksLimit: 10 } },
                    y: { beginAtZero: true, grid: { color: grid }, ticks: { color: ticks, callback: v => formatCurrency(v) } }
                }
            }
        });
    } catch (e) { console.error('Trends chart error:', e); }
}

async function loadStorageChart() {
    const wrap = document.getElementById('storageChartWrap');
    if (!wrap) return;
    try {
        const data    = await apiGet('/analytics/storage-distribution');
        const entries = Object.entries(data || {}).filter(([, v]) => v > 0);
        if (!entries.length) { wrap.innerHTML = emptyStateHTML('💳', 'No storage data yet.'); return; }
        if (!document.getElementById('storageChart')) wrap.innerHTML = '<canvas id="storageChart" style="max-height:220px;"></canvas>';

        const { ticks } = chartColors();
        if (storageChart) storageChart.destroy();
        storageChart = new Chart(document.getElementById('storageChart').getContext('2d'), {
            type: 'doughnut',
            data: {
                labels: entries.map(([k]) => k),
                datasets: [{ data: entries.map(([, v]) => v), backgroundColor: PALETTE.slice(0, entries.length), borderWidth: 3, borderColor: isDark() ? '#111827' : '#fff', hoverOffset: 6 }]
            },
            options: {
                responsive: true, maintainAspectRatio: false, cutout: '60%',
                animation: { duration: 600 },
                plugins: {
                    legend: { position: 'right', labels: { color: ticks, font: { size: 11 }, boxWidth: 10, padding: 8 } },
                    tooltip: { callbacks: { label: c => ` ${c.label}: ${formatCurrency(c.raw)}` } }
                }
            }
        });
    } catch (e) { wrap.innerHTML = emptyStateHTML('💳', 'Could not load storage data.'); }
}

async function loadIncomeSourceChart(month, year) {
    const wrap = document.getElementById('incomeSourceChartWrap');
    if (!wrap) return;
    const now = new Date();
    const m = month || now.getMonth() + 1;
    const y = year  || now.getFullYear();
    try {
        const data    = await apiGet(`/analytics/income-sources?month=${m}&year=${y}`);
        const entries = Object.entries(data || {}).filter(([, v]) => v > 0);
        if (!entries.length) { wrap.innerHTML = emptyStateHTML('📈', 'No income source data for this period.'); return; }
        if (!document.getElementById('incomeSourceChart')) wrap.innerHTML = '<canvas id="incomeSourceChart" style="max-height:220px;"></canvas>';

        const { ticks } = chartColors();
        const total = entries.reduce((s, [, v]) => s + v, 0);
        if (incomeSourceChart) incomeSourceChart.destroy();
        incomeSourceChart = new Chart(document.getElementById('incomeSourceChart').getContext('2d'), {
            type: 'doughnut',
            data: {
                labels: entries.map(([k]) => k),
                datasets: [{ data: entries.map(([, v]) => v), backgroundColor: PALETTE.slice(0, entries.length), borderWidth: 3, borderColor: isDark() ? '#111827' : '#fff', hoverOffset: 6 }]
            },
            options: {
                responsive: true, maintainAspectRatio: false, cutout: '60%',
                animation: { duration: 600 },
                plugins: {
                    legend: { position: 'right', labels: { color: ticks, font: { size: 11 }, boxWidth: 10, padding: 8 } },
                    tooltip: { callbacks: { label: c => { const pct = total > 0 ? (c.raw / total * 100).toFixed(1) : 0; return ` ${c.label}: ${formatCurrency(c.raw)} (${pct}%)`; } } }
                }
            }
        });
    } catch (e) { wrap.innerHTML = emptyStateHTML('📈', 'Could not load income source data.'); }
}

async function loadCategoryTable() {
    const tbody = document.getElementById('categoryTableBody');
    if (!tbody) return;
    try {
        const data  = await apiGet('/analytics/dashboard');
        const cats  = data.expensesByCategory || {};
        const total = Object.values(cats).reduce((a, b) => a + b, 0);
        const rows  = Object.entries(cats).sort((a, b) => b[1] - a[1]);
        if (!rows.length) {
            tbody.innerHTML = `<tr><td colspan="3" class="py-8 text-center text-gray-400">No expense data yet.</td></tr>`;
            return;
        }
        tbody.innerHTML = rows.map(([cat, amt]) => `
            <tr class="border-b border-gray-100 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800">
                <td class="py-3 px-2 font-medium text-gray-900 dark:text-white">${cat}</td>
                <td class="py-3 px-2 text-right text-gray-700 dark:text-gray-300">${formatCurrency(amt)}</td>
                <td class="py-3 px-2 text-right text-gray-500 dark:text-gray-400">${total > 0 ? ((amt / total) * 100).toFixed(1) : 0}%</td>
            </tr>`).join('');
    } catch (e) {
        tbody.innerHTML = `<tr><td colspan="3" class="py-8 text-center text-gray-400">Could not load data.</td></tr>`;
    }
}

async function loadMonthlySummary() {
    const tbody = document.getElementById('monthlySummaryBody');
    if (!tbody) return;
    try {
        const data = await apiGet('/analytics/trends?months=12');
        const rows = data.monthlyData || [];
        if (!rows.length) {
            tbody.innerHTML = `<tr><td colspan="5" class="py-8 text-center text-gray-400">No data yet.</td></tr>`;
            return;
        }
        tbody.innerHTML = rows.map(row => {
            const net      = parseFloat(row.income || 0) - parseFloat(row.expenses || 0);
            const netClass = net >= 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-red-600 dark:text-red-400';
            return `<tr class="border-b border-gray-100 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800">
                <td class="py-3 px-2 font-medium text-gray-900 dark:text-white">${row.month}</td>
                <td class="py-3 px-2 text-right text-emerald-600 dark:text-emerald-400">${formatCurrency(row.income)}</td>
                <td class="py-3 px-2 text-right text-red-600 dark:text-red-400">${formatCurrency(row.expenses)}</td>
                <td class="py-3 px-2 text-right font-semibold ${netClass}">${net >= 0 ? '+' : ''}${formatCurrency(net)}</td>
                <td class="py-3 px-2 text-right text-gray-500 dark:text-gray-400">${row.transactionCount || '—'}</td>
            </tr>`;
        }).join('');
    } catch (e) {
        tbody.innerHTML = `<tr><td colspan="5" class="py-8 text-center text-gray-400">Could not load summary.</td></tr>`;
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// BUDGETS
// ══════════════════════════════════════════════════════════════════════════════
function firstOfMonth() { const n = new Date(); return new Date(n.getFullYear(), n.getMonth(), 1).toISOString().split('T')[0]; }
function lastOfMonth()  { const n = new Date(); return new Date(n.getFullYear(), n.getMonth() + 1, 0).toISOString().split('T')[0]; }

async function loadBudgets() {
    try {
        const budgets = await apiGet('/budgets');
        renderBudgets(budgets);
    } catch (e) { showError('Failed to load budgets.'); }
}

function renderBudgets(budgets) {
    const container = document.getElementById('budgetList');
    if (!container) return;
    let totalBudget = 0, totalSpent = 0;
    budgets.forEach(b => { totalBudget += parseFloat(b.limitAmount || 0); totalSpent += parseFloat(b.spentAmount || 0); });
    const set = (id, v) => { const el = document.getElementById(id); if (el) el.textContent = v; };
    set('totalBudget',    formatCurrency(totalBudget));
    set('totalSpent',     formatCurrency(totalSpent));
    set('totalRemaining', formatCurrency(Math.max(totalBudget - totalSpent, 0)));

    if (!budgets.length) {
        container.innerHTML = `<div class="card text-center py-12"><p class="text-4xl mb-3">💰</p><p class="text-gray-500 dark:text-gray-400">No budgets yet. Create one to track spending limits.</p></div>`;
        return;
    }
    container.innerHTML = budgets.map(b => {
        const limit = parseFloat(b.limitAmount || 0), spent = parseFloat(b.spentAmount || 0);
        const pct   = limit > 0 ? Math.min((spent / limit) * 100, 100) : 0;
        const isOver = spent > limit;
        const barColor = isOver ? 'bg-red-500' : pct > 80 ? 'bg-yellow-500' : 'bg-emerald-500';
        const badge    = isOver
            ? '<span class="px-2 py-0.5 rounded-full text-xs bg-red-100 dark:bg-red-900 text-red-700 dark:text-red-300">Over Budget</span>'
            : pct > 80
            ? '<span class="px-2 py-0.5 rounded-full text-xs bg-yellow-100 dark:bg-yellow-900 text-yellow-700 dark:text-yellow-300">Warning</span>'
            : '<span class="px-2 py-0.5 rounded-full text-xs bg-emerald-100 dark:bg-emerald-900 text-emerald-700 dark:text-emerald-300">On Track</span>';
        const cur = b.currency || 'USD';
        return `
        <div class="card">
            <div class="flex justify-between items-start mb-3 flex-wrap gap-2">
                <div class="flex items-center gap-2 flex-wrap">
                    <span class="px-3 py-1 rounded-full text-sm font-medium bg-indigo-100 dark:bg-indigo-900 text-indigo-800 dark:text-indigo-200">${b.category}</span>
                    ${badge}
                </div>
                <div class="flex gap-2">
                    <button onclick="editBudget(${b.id})" class="text-xs text-indigo-600 hover:text-indigo-800 dark:text-indigo-400 px-2 py-1 border border-indigo-300 dark:border-indigo-700 rounded">Edit</button>
                    <button onclick="deleteBudget(${b.id})" class="text-xs text-red-600 hover:text-red-800 dark:text-red-400 px-2 py-1 border border-red-300 dark:border-red-700 rounded">Delete</button>
                </div>
            </div>
            <div class="flex justify-between text-sm mb-2">
                <span class="text-gray-600 dark:text-gray-400">Spent: <strong class="text-gray-900 dark:text-white">${formatCurrency(spent, cur)}</strong></span>
                <span class="text-gray-600 dark:text-gray-400">Limit: <strong class="text-gray-900 dark:text-white">${formatCurrency(limit, cur)}</strong></span>
            </div>
            <div class="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2.5 mb-2 overflow-hidden">
                <div class="${barColor} h-2.5 rounded-full transition-all duration-500" style="width:${pct}%"></div>
            </div>
            <div class="flex justify-between text-xs text-gray-500 dark:text-gray-400">
                <span>${isOver ? 'Over by ' + formatCurrency(spent - limit, cur) : 'Remaining: ' + formatCurrency(limit - spent, cur)}</span>
                <span>${pct.toFixed(1)}%</span>
            </div>
        </div>`;
    }).join('');
}

async function loadCategoriesForBudget(selected = '') {
    const sel = document.getElementById('budgetCategory');
    try {
        const cats = await apiGet('/categories/active');
        const expense = cats.filter(c => !c.type || c.type === 'EXPENSE');
        sel.innerHTML = '<option value="">Select category</option>';
        expense.forEach(c => {
            const o = document.createElement('option');
            o.value = c.name; o.textContent = `${c.icon || ''} ${c.name}`.trim();
            if (c.name === selected) o.selected = true;
            sel.appendChild(o);
        });
    } catch (_) { sel.innerHTML = '<option value="">Could not load — type manually</option>'; }
}

function openBudgetModal(budget = null) {
    document.getElementById('budgetModalTitle').textContent = budget ? 'Edit Budget' : 'Add Budget';
    document.getElementById('budgetId').value        = budget?.id || '';
    document.getElementById('budgetLimit').value     = budget?.limitAmount || '';
    document.getElementById('budgetStartDate').value = budget?.startDate || firstOfMonth();
    document.getElementById('budgetEndDate').value   = budget?.endDate   || lastOfMonth();
    loadCategoriesForBudget(budget?.category || '');
    document.getElementById('budgetModal').classList.remove('hidden');
}

function closeBudgetModal() {
    document.getElementById('budgetModal').classList.add('hidden');
    document.getElementById('budgetForm').reset();
}

async function editBudget(id) {
    try { openBudgetModal(await apiGet(`/budgets/${id}`)); } catch (e) { showError('Failed to load budget.'); }
}

async function deleteBudget(id) {
    if (!confirm('Delete this budget?')) return;
    try { await apiDelete(`/budgets/${id}`); showSuccess('Budget deleted.'); loadBudgets(); } catch (e) { showError('Failed to delete.'); }
}

// ══════════════════════════════════════════════════════════════════════════════
// GOALS
// ══════════════════════════════════════════════════════════════════════════════
let contributeGoalId = null;

async function loadGoals() {
    try {
        const goals = await apiGet('/goals');
        renderGoals(goals);
        const completed  = goals.filter(g => parseFloat(g.currentAmount || 0) >= parseFloat(g.targetAmount || 1));
        const totalSaved = goals.reduce((s, g) => s + parseFloat(g.currentAmount || 0), 0);
        const set = (id, v) => { const el = document.getElementById(id); if (el) el.textContent = v; };
        set('totalGoals', goals.length);
        set('completedGoals', completed.length);
        set('totalSaved', formatCurrency(totalSaved));
    } catch (e) { showError('Failed to load goals.'); }
}

function renderGoals(goals) {
    const container = document.getElementById('goalsList');
    if (!container) return;
    if (!goals.length) {
        container.innerHTML = `<div class="card text-center py-12 col-span-full"><p class="text-4xl mb-3">🎯</p><p class="text-gray-500 dark:text-gray-400">No goals yet. Create your first financial goal!</p></div>`;
        return;
    }
    container.innerHTML = goals.map(g => {
        const target = parseFloat(g.targetAmount || 0), current = parseFloat(g.currentAmount || 0);
        const pct        = target > 0 ? Math.min((current / target) * 100, 100) : 0;
        const isComplete = current >= target;
        const days       = daysUntil(g.targetDate);
        const barColor   = isComplete ? 'bg-emerald-500' : pct > 75 ? 'bg-blue-500' : pct > 40 ? 'bg-indigo-500' : 'bg-purple-500';
        const daysText   = days === null ? '' : days < 0
            ? `<span class="text-red-500 text-xs">⚠ ${Math.abs(days)} days overdue</span>`
            : `<span class="text-gray-500 dark:text-gray-400 text-xs">${days} days left</span>`;
        return `
        <div class="card">
            <div class="flex justify-between items-start mb-3">
                <div>
                    <h4 class="font-semibold text-gray-900 dark:text-white">${g.goalName || g.name}</h4>
                    ${g.description ? `<p class="text-xs text-gray-500 dark:text-gray-400">${g.description}</p>` : ''}
                </div>
                <div class="flex items-center gap-1">
                    ${isComplete ? '<span class="text-emerald-500">✅</span>' : ''}
                    <button onclick="editGoal(${g.id})" class="text-xs text-indigo-600 px-2 py-1 border border-indigo-300 dark:border-indigo-700 rounded">Edit</button>
                    <button onclick="deleteGoal(${g.id})" class="text-xs text-red-600 px-2 py-1 border border-red-300 dark:border-red-700 rounded">Delete</button>
                </div>
            </div>
            <div class="flex justify-between text-sm mb-2">
                <span class="text-gray-600 dark:text-gray-400">Saved: <strong class="text-gray-900 dark:text-white">${formatCurrency(current)}</strong></span>
                <span class="text-gray-600 dark:text-gray-400">Target: <strong class="text-gray-900 dark:text-white">${formatCurrency(target)}</strong></span>
            </div>
            <div class="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2.5 mb-2 overflow-hidden">
                <div class="${barColor} h-2.5 rounded-full transition-all duration-500" style="width:${pct}%"></div>
            </div>
            <div class="flex justify-between items-center">
                <span class="text-sm font-semibold text-gray-700 dark:text-gray-300">${pct.toFixed(1)}%</span>
                ${daysText}
            </div>
            ${!isComplete ? `<div class="mt-3 pt-3 border-t border-gray-100 dark:border-gray-700">
                <button onclick="openContributeModal(${g.id})" class="text-sm text-indigo-600 dark:text-indigo-400 font-medium hover:underline">+ Add Contribution</button>
            </div>` : ''}
        </div>`;
    }).join('');
}

function openGoalModal(goal = null) {
    document.getElementById('goalModalTitle').textContent = goal ? 'Edit Goal' : 'New Goal';
    document.getElementById('goalId').value          = goal?.id || '';
    document.getElementById('goalName').value        = goal?.goalName || goal?.name || '';
    document.getElementById('goalDescription').value = goal?.description || '';
    document.getElementById('goalTarget').value      = goal?.targetAmount || '';
    document.getElementById('goalCurrent').value     = goal?.currentAmount || '0';
    document.getElementById('goalDeadline').value    = (goal?.targetDate)?.split('T')[0] || '';
    document.getElementById('goalModal').classList.remove('hidden');
}

function closeGoalModal() {
    document.getElementById('goalModal').classList.add('hidden');
    document.getElementById('goalForm').reset();
}

async function editGoal(id) {
    try { openGoalModal(await apiGet(`/goals/${id}`)); } catch (e) { showError('Failed to load goal.'); }
}

async function deleteGoal(id) {
    if (!confirm('Delete this goal?')) return;
    try { await apiDelete(`/goals/${id}`); showSuccess('Goal deleted.'); loadGoals(); } catch (e) { showError('Failed to delete.'); }
}

function openContributeModal(goalId) {
    contributeGoalId = goalId;
    document.getElementById('contributeGoalId').value = goalId;
    document.getElementById('contributeAmount').value = '';
    document.getElementById('contributeModal').classList.remove('hidden');
}

function closeContributeModal() {
    document.getElementById('contributeModal').classList.add('hidden');
    contributeGoalId = null;
}

async function submitContribution() {
    const amount = parseFloat(document.getElementById('contributeAmount').value);
    if (!amount || amount <= 0) { showError('Enter a valid amount.'); return; }
    try {
        await apiPatch(`/goals/${contributeGoalId}/add?amount=${amount}`, {});
        showSuccess('Contribution added!');
        closeContributeModal();
        loadGoals();
    } catch (e) { showError('Failed to add contribution.'); }
}

// ══════════════════════════════════════════════════════════════════════════════
// SUBSCRIPTIONS
// ══════════════════════════════════════════════════════════════════════════════
async function loadSubscriptions() {
    try {
        const [all, upcoming] = await Promise.all([
            apiGet('/recurring-payments'),
            apiGet('/recurring-payments/upcoming?days=7'),
        ]);
        let monthly = 0;
        all.forEach(sub => {
            const freq = sub.frequency || 'MONTHLY', amt = parseFloat(sub.amount) || 0;
            if (freq === 'MONTHLY')     monthly += amt;
            else if (freq === 'WEEKLY')    monthly += amt * 4.33;
            else if (freq === 'BIWEEKLY')  monthly += amt * 2.17;
            else if (freq === 'QUARTERLY') monthly += amt / 3;
            else if (freq === 'YEARLY')    monthly += amt / 12;
        });
        const set = (id, v) => { const el = document.getElementById(id); if (el) el.textContent = v; };
        set('monthlyTotal', formatCurrency(monthly));
        set('subCount', all.length);
        set('upcomingCount', upcoming.length);
        renderUpcoming(upcoming);
        renderSubscriptionsTable(all);
    } catch (e) { showError('Failed to load subscriptions.'); }
}

function renderUpcoming(upcoming) {
    const list = document.getElementById('upcomingList');
    if (!list) return;
    if (!upcoming.length) { list.innerHTML = '<p class="text-sm text-gray-500 dark:text-gray-400">No subscriptions due in the next 7 days.</p>'; return; }
    list.innerHTML = upcoming.map(sub => {
        const nextDate = sub.nextExpectedCharge ? formatDate(sub.nextExpectedCharge) : '—';
        const daysLeft = sub.nextExpectedCharge ? Math.ceil((new Date(sub.nextExpectedCharge) - Date.now()) / 86400000) : null;
        const cls = daysLeft !== null && daysLeft <= 1 ? 'bg-red-50 dark:bg-red-900/20 border-red-200 dark:border-red-800' : 'bg-orange-50 dark:bg-orange-900/20 border-orange-200 dark:border-orange-800';
        return `
        <div class="flex justify-between items-center p-3 rounded-lg border ${cls} mb-2">
            <div><p class="font-medium text-sm text-gray-900 dark:text-white">${sub.name}</p>
            <p class="text-xs text-gray-500 dark:text-gray-400">${sub.category} · ${sub.frequency}</p></div>
            <div class="text-right">
                <p class="font-semibold text-sm text-orange-600 dark:text-orange-400">${formatCurrency(sub.amount, sub.currency)}</p>
                <p class="text-xs text-gray-500 dark:text-gray-400">${nextDate}${daysLeft !== null ? ` (${daysLeft}d)` : ''}</p>
            </div>
        </div>`;
    }).join('');
}

function renderSubscriptionsTable(all) {
    const tbody = document.getElementById('subscriptionsTable');
    if (!tbody) return;
    if (!all.length) {
        tbody.innerHTML = `<tr><td colspan="6" class="px-4 py-8 text-center text-gray-500 dark:text-gray-400 text-sm">No recurring patterns detected. Mark transactions as recurring or add the same description monthly.</td></tr>`;
        return;
    }
    tbody.innerHTML = all.map(sub => `
        <tr class="hover:bg-gray-50 dark:hover:bg-gray-800 border-b border-gray-100 dark:border-gray-700">
            <td class="px-4 py-3 text-sm font-medium text-gray-900 dark:text-white">${sub.name}</td>
            <td class="px-4 py-3 text-sm text-gray-600 dark:text-gray-300">${sub.category}</td>
            <td class="px-4 py-3 text-sm font-medium text-gray-900 dark:text-white text-right">${formatCurrency(sub.amount, sub.currency)}</td>
            <td class="px-4 py-3 text-sm text-gray-600 dark:text-gray-300">${sub.frequency}</td>
            <td class="px-4 py-3 text-sm text-gray-600 dark:text-gray-300 text-right">${sub.lastCharge ? formatDate(sub.lastCharge) : '—'}</td>
            <td class="px-4 py-3 text-sm text-right ${sub.nextExpectedCharge && new Date(sub.nextExpectedCharge) < new Date() ? 'text-red-500 font-medium' : 'text-gray-600 dark:text-gray-300'}">${sub.nextExpectedCharge ? formatDate(sub.nextExpectedCharge) : '—'}</td>
        </tr>`).join('');
}

// ══════════════════════════════════════════════════════════════════════════════
// DEBTS
// ══════════════════════════════════════════════════════════════════════════════
let allDebts = [], editingDebtId = null;

async function loadDebts() {
    try {
        const [debts, summary] = await Promise.all([apiGet('/debts'), apiGet('/debts/summary')]);
        allDebts = debts;
        const set = (id, v) => { const el = document.getElementById(id); if (el) el.textContent = v; };
        set('totalIOwe',    formatCurrency(summary.totalIOwe   || 0));
        set('totalTheyOwe', formatCurrency(summary.totalTheyOwe || 0));
        renderDebtTables(debts);
    } catch (e) { showError('Failed to load debts.'); }
}

function renderDebtTables(debts) {
    const iOwe = debts.filter(d => d.direction === 'I_OWE');
    const theyOwe = debts.filter(d => d.direction === 'THEY_OWE');
    document.getElementById('iOweTable').innerHTML    = renderDebtRows(iOwe);
    document.getElementById('theyOweTable').innerHTML = renderDebtRows(theyOwe);
}

function renderDebtRows(debts) {
    if (!debts.length) return `<tr><td colspan="6" class="px-4 py-8 text-center text-gray-400">No entries.</td></tr>`;
    return debts.map(d => {
        const overdue = d.dueDate && new Date(d.dueDate) < new Date() && d.status === 'ACTIVE';
        const badge   = d.status === 'SETTLED'
            ? '<span class="px-2 py-0.5 rounded-full text-xs bg-emerald-100 dark:bg-emerald-900 text-emerald-700 dark:text-emerald-300">Settled</span>'
            : '<span class="px-2 py-0.5 rounded-full text-xs bg-yellow-100 dark:bg-yellow-900 text-yellow-700 dark:text-yellow-300">Active</span>';
        return `
        <tr class="hover:bg-gray-50 dark:hover:bg-gray-800 border-b border-gray-100 dark:border-gray-700 ${d.status === 'SETTLED' ? 'opacity-60' : ''}">
            <td class="px-4 py-3 text-sm font-medium text-gray-900 dark:text-white">${d.name}</td>
            <td class="px-4 py-3 text-sm font-semibold text-gray-900 dark:text-white text-right">${formatCurrency(d.amount, d.currency)}</td>
            <td class="px-4 py-3 text-sm ${overdue ? 'text-red-600 font-medium' : 'text-gray-600 dark:text-gray-300'}">${d.dueDate ? formatDate(d.dueDate) : '—'}${overdue ? ' ⚠️' : ''}</td>
            <td class="px-4 py-3 text-sm text-gray-600 dark:text-gray-300">${d.description || '—'}</td>
            <td class="px-4 py-3 text-sm text-center">${badge}</td>
            <td class="px-4 py-3 text-sm text-right space-x-2">
                ${d.status === 'ACTIVE' ? `<button onclick="editDebt(${d.id})" class="text-indigo-600 hover:text-indigo-900 dark:text-indigo-400">Edit</button>
                <button onclick="settleDebt(${d.id})" class="text-emerald-600 hover:text-emerald-900 dark:text-emerald-400">Settle</button>` : ''}
                <button onclick="deleteDebt(${d.id})" class="text-red-600 hover:text-red-900 dark:text-red-400">Delete</button>
            </td>
        </tr>`;
    }).join('');
}

function showDebtModal(isEdit = false) {
    document.getElementById('debtModalTitle').textContent = isEdit ? 'Edit Debt' : 'Add Debt';
    updateNameLabel();
    document.getElementById('debtModal').classList.remove('hidden');
}

function hideDebtModal() {
    document.getElementById('debtModal').classList.add('hidden');
    document.getElementById('debtForm').reset();
    editingDebtId = null;
}

function updateNameLabel() {
    const dir = document.querySelector('input[name="direction"]:checked')?.value;
    const lbl = document.getElementById('nameLabel');
    if (lbl) lbl.textContent = dir === 'THEY_OWE' ? 'Owed By' : 'Owed To';
}

function editDebt(id) {
    const d = allDebts.find(x => x.id === id); if (!d) return;
    editingDebtId = id;
    document.getElementById('debtId').value          = d.id;
    document.querySelector(`input[name="direction"][value="${d.direction}"]`).checked = true;
    document.getElementById('debtName').value        = d.name;
    document.getElementById('debtAmount').value      = d.amount;
    document.getElementById('debtCurrency').value    = d.currency;
    document.getElementById('debtDueDate').value     = d.dueDate || '';
    document.getElementById('debtDescription').value = d.description || '';
    updateNameLabel();
    showDebtModal(true);
}

async function settleDebt(id) {
    if (!confirm('Mark as settled?')) return;
    try { await apiPatch(`/debts/${id}/settle`, {}); showSuccess('Debt settled.'); loadDebts(); } catch (e) { showError('Failed.'); }
}

async function deleteDebt(id) {
    if (!confirm('Delete this debt?')) return;
    try { await apiDelete(`/debts/${id}`); showSuccess('Debt deleted.'); loadDebts(); } catch (e) { showError('Failed.'); }
}

async function saveDebt(event) {
    event.preventDefault();
    const btn = event.target.querySelector('[type="submit"]');
    setLoading(btn, true);
    const debt = {
        direction:   document.querySelector('input[name="direction"]:checked').value,
        name:        document.getElementById('debtName').value.trim(),
        amount:      parseFloat(document.getElementById('debtAmount').value),
        currency:    document.getElementById('debtCurrency').value,
        dueDate:     document.getElementById('debtDueDate').value || null,
        description: document.getElementById('debtDescription').value.trim() || null,
        status:      'ACTIVE',
    };
    try {
        if (editingDebtId) { await apiPut(`/debts/${editingDebtId}`, debt); showSuccess('Debt updated.'); }
        else               { await apiPost('/debts', debt); showSuccess('Debt added.'); }
        hideDebtModal(); loadDebts();
    } catch (e) { showError('Failed to save debt.'); }
    finally { setLoading(btn, false); }
}

// ══════════════════════════════════════════════════════════════════════════════
// BOOT
// ══════════════════════════════════════════════════════════════════════════════
document.addEventListener('DOMContentLoaded', () => {

    // Tab switching
    document.querySelectorAll('.analytics-tab').forEach(btn => {
        btn.addEventListener('click', () => switchTab(btn.dataset.tab));
    });

    // Range buttons (overview tab)
    document.querySelectorAll('#rangeButtons .range-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('#rangeButtons .range-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            if (trendsChart) { trendsChart.destroy(); trendsChart = null; }
            loadTrendsChart(btn.dataset.range);
        });
    });

    // Income source month/year selects
    const monthSel = document.getElementById('incomeSourceMonth');
    const yearSel  = document.getElementById('incomeSourceYear');
    if (yearSel) {
        const now = new Date();
        for (let y = now.getFullYear(); y >= now.getFullYear() - 3; y--) {
            const o = document.createElement('option');
            o.value = y; o.textContent = y;
            if (y === now.getFullYear()) o.selected = true;
            yearSel.appendChild(o);
        }
    }
    if (monthSel) {
        monthSel.value = new Date().getMonth() + 1;
        const onChange = () => {
            if (incomeSourceChart) { incomeSourceChart.destroy(); incomeSourceChart = null; }
            const wrap = document.getElementById('incomeSourceChartWrap');
            if (wrap) wrap.innerHTML = '<canvas id="incomeSourceChart" style="max-height:220px;"></canvas>';
            loadIncomeSourceChart(+monthSel.value, +(yearSel?.value));
        };
        monthSel.addEventListener('change', onChange);
        if (yearSel) yearSel.addEventListener('change', onChange);
    }

    // Budget form
    document.getElementById('budgetForm')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        const btn = e.target.querySelector('[type="submit"]');
        setLoading(btn, true);
        const id = document.getElementById('budgetId').value;
        const budget = {
            category:    document.getElementById('budgetCategory').value,
            limitAmount: parseFloat(document.getElementById('budgetLimit').value),
            startDate:   document.getElementById('budgetStartDate').value,
            endDate:     document.getElementById('budgetEndDate').value,
            period:     'MONTHLY',
            currency:    currentCurrency || 'USD',
        };
        try {
            if (id) { await apiPut(`/budgets/${id}`, budget); showSuccess('Budget updated.'); }
            else    { await apiPost('/budgets', budget); showSuccess('Budget created.'); }
            closeBudgetModal(); loadBudgets();
        } catch (_) { showError('Failed to save budget.'); }
        finally { setLoading(btn, false); }
    });

    // Goal form
    const minDate = new Date(); minDate.setDate(minDate.getDate() + 1);
    const deadlineInput = document.getElementById('goalDeadline');
    if (deadlineInput) deadlineInput.min = minDate.toISOString().split('T')[0];

    document.getElementById('goalForm')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        const btn = e.target.querySelector('[type="submit"]');
        setLoading(btn, true);
        const id = document.getElementById('goalId').value;
        const goal = {
            goalName:     document.getElementById('goalName').value,
            description:  document.getElementById('goalDescription').value,
            targetAmount: parseFloat(document.getElementById('goalTarget').value),
            currentAmount:parseFloat(document.getElementById('goalCurrent').value || 0),
            targetDate:   document.getElementById('goalDeadline').value,
            startDate:    new Date().toISOString().split('T')[0],
            currency:    'USD',
            status:      'IN_PROGRESS',
        };
        try {
            if (id) { await apiPut(`/goals/${id}`, goal); showSuccess('Goal updated.'); }
            else    { await apiPost('/goals', goal); showSuccess('Goal created.'); }
            closeGoalModal(); loadGoals();
        } catch (_) { showError('Failed to save goal.'); }
        finally { setLoading(btn, false); }
    });

    // Debt events
    document.getElementById('addDebtBtn')?.addEventListener('click', () => showDebtModal(false));
    document.getElementById('closeDebtModal')?.addEventListener('click', hideDebtModal);
    document.getElementById('cancelDebtBtn')?.addEventListener('click', hideDebtModal);
    document.getElementById('debtForm')?.addEventListener('submit', saveDebt);
    document.querySelectorAll('input[name="direction"]').forEach(r => r.addEventListener('change', updateNameLabel));
    document.getElementById('debtModal')?.addEventListener('click', e => { if (e.target.id === 'debtModal') hideDebtModal(); });

    // Load first tab
    switchTab('overview');
});
