// dashboard.js — Smart Finance Dashboard
// Depends on: app.js (formatCurrency, apiGet, apiPatch, formatDate, showError, emptyStateHTML, API)

let incomeExpensesChartInstance = null;
let categoryChartInstance       = null;
let storageChartInstance        = null;
let incomeSourceChartInstance   = null;
let currentRange                = 'month';

// ── Chart color palette ────────────────────────────────────────────────────────
const PALETTE = [
    '#6366F1','#10B981','#F59E0B','#EF4444','#8B5CF6',
    '#06B6D4','#84CC16','#F97316','#EC4899','#64748B'
];

function isDarkMode() {
    return document.documentElement.classList.contains('dark');
}

function chartColors() {
    const dark = isDarkMode();
    return {
        grid:  dark ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.05)',
        ticks: dark ? '#9CA3AF' : '#6B7280',
    };
}

// ── Main dashboard load ────────────────────────────────────────────────────────
async function loadDashboard() {
    try {
        const [analytics, storageData] = await Promise.all([
            apiGet('/analytics/dashboard'),
            apiGet('/analytics/storage-distribution').catch(() => ({})),
        ]);

        // Total Balance = sum of all net storage values (ignore negative balances for total)
        const storageValues = Object.values(storageData || {});
        const totalBalance  = storageValues.reduce((sum, v) => sum + (v > 0 ? v : 0), 0);

        const monthlyIncome    = Number(analytics.totalIncome   || 0);
        const monthlyExpenses  = Number(analytics.totalExpenses || 0);
        const netFlow          = Number(analytics.netSavings    || 0);

        _setEl('totalBalance',    formatCurrency(totalBalance));
        _setEl('monthlyIncome',   formatCurrency(monthlyIncome));
        _setEl('monthlyExpenses', formatCurrency(monthlyExpenses));

        const netFlowEl    = document.getElementById('netFlow');
        const netFlowLabel = document.getElementById('netFlowLabel');
        if (netFlowEl) {
            netFlowEl.textContent = (netFlow >= 0 ? '+' : '') + formatCurrency(netFlow);
            netFlowEl.className = 'text-2xl font-bold ' + (netFlow >= 0
                ? 'text-emerald-600 dark:text-emerald-400'
                : 'text-red-600 dark:text-red-400');
        }
        if (netFlowLabel) {
            netFlowLabel.textContent = netFlow >= 0 ? 'Positive cash flow' : 'Spending more than earning';
        }

        // Render all sections
        loadTrendsChart(currentRange);
        loadCategoryChart(analytics.expensesByCategory || {});
        loadStorageChart(storageData || {});
        loadInsights();
        loadBudgetStatus(analytics.activeBudgets || []);
        loadHealthScore();
        loadIncomeSourceChart();
    } catch (e) {
        showError('Failed to load dashboard data.');
    }
}

function _setEl(id, value) {
    const el = document.getElementById(id);
    if (el) el.textContent = value;
}

// ── Income vs Expenses line chart ──────────────────────────────────────────────
async function loadTrendsChart(range) {
    try {
        let labels, incomeData, expensesData;

        if (range === 'year') {
            const data    = await apiGet('/analytics/trends?months=12');
            const monthly = data.monthlyData || [];
            labels        = monthly.map(d => {
                const [y, m] = d.month.split('-');
                return new Date(y, m - 1).toLocaleDateString('en-US', { month: 'short', year: '2-digit' });
            });
            incomeData   = monthly.map(d => Number(d.income));
            expensesData = monthly.map(d => Number(d.expenses));
        } else {
            const days    = range === 'week' ? 7 : 30;
            const data    = await apiGet(`/analytics/trends/daily?days=${days}`);
            const daily   = data.dailyData || [];
            labels        = daily.map(d => {
                const date = new Date(d.day + 'T00:00:00');
                return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
            });
            incomeData   = daily.map(d => Number(d.income));
            expensesData = daily.map(d => Number(d.expenses));
        }

        const canvas = document.getElementById('incomeExpensesChart');
        if (!canvas) return;
        const ctx = canvas.getContext('2d');
        if (incomeExpensesChartInstance) incomeExpensesChartInstance.destroy();

        const { grid, ticks } = chartColors();

        incomeExpensesChartInstance = new Chart(ctx, {
            type: 'line',
            data: {
                labels,
                datasets: [
                    {
                        label: 'Income',
                        data: incomeData,
                        borderColor: 'rgb(16, 185, 129)',
                        backgroundColor: 'rgba(16, 185, 129, 0.12)',
                        borderWidth: 2.5,
                        pointRadius: labels.length <= 12 ? 4 : 2,
                        pointHoverRadius: 6,
                        pointBackgroundColor: 'rgb(16, 185, 129)',
                        tension: 0.4,
                        fill: true,
                    },
                    {
                        label: 'Expenses',
                        data: expensesData,
                        borderColor: 'rgb(239, 68, 68)',
                        backgroundColor: 'rgba(239, 68, 68, 0.10)',
                        borderWidth: 2.5,
                        pointRadius: labels.length <= 12 ? 4 : 2,
                        pointHoverRadius: 6,
                        pointBackgroundColor: 'rgb(239, 68, 68)',
                        tension: 0.4,
                        fill: true,
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: { intersect: false, mode: 'index' },
                plugins: {
                    legend: {
                        position: 'bottom',
                        labels: { color: ticks, usePointStyle: true, pointStyleWidth: 10, padding: 16 }
                    },
                    tooltip: {
                        backgroundColor: isDarkMode() ? '#1F2937' : '#fff',
                        borderColor: isDarkMode() ? '#374151' : '#E5E7EB',
                        borderWidth: 1,
                        titleColor: isDarkMode() ? '#F9FAFB' : '#111827',
                        bodyColor: isDarkMode() ? '#D1D5DB' : '#374151',
                        padding: 10,
                        callbacks: {
                            label: c => ` ${c.dataset.label}: ${formatCurrency(c.raw)}`
                        }
                    }
                },
                scales: {
                    x: {
                        grid: { color: grid },
                        ticks: { color: ticks, maxTicksLimit: 8 }
                    },
                    y: {
                        beginAtZero: true,
                        grid: { color: grid },
                        ticks: {
                            color: ticks,
                            callback: v => formatCurrency(v)
                        }
                    }
                }
            }
        });
    } catch (e) {
        console.error('Error loading trends chart:', e);
    }
}

// ── Spending by category doughnut ──────────────────────────────────────────────
function loadCategoryChart(expensesByCategory) {
    const wrap = document.getElementById('categoryChartWrap');
    if (!wrap) return;

    const labels = Object.keys(expensesByCategory);
    const values = Object.values(expensesByCategory).map(Number);

    if (labels.length === 0) {
        wrap.innerHTML = emptyStateHTML('📊', 'No expense data yet this month.');
        return;
    }

    // Restore canvas if it was replaced
    if (!document.getElementById('categoryChart')) {
        wrap.innerHTML = '<canvas id="categoryChart" style="max-height:200px;"></canvas>';
    }
    const ctx = document.getElementById('categoryChart').getContext('2d');
    if (categoryChartInstance) categoryChartInstance.destroy();

    const { ticks } = chartColors();
    categoryChartInstance = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels,
            datasets: [{
                data: values,
                backgroundColor: PALETTE.slice(0, labels.length),
                borderWidth: 2,
                borderColor: isDarkMode() ? '#1F2937' : '#fff',
                hoverBorderWidth: 0,
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            cutout: '60%',
            plugins: {
                legend: {
                    position: 'right',
                    labels: { color: ticks, font: { size: 11 }, boxWidth: 12, padding: 8 }
                },
                tooltip: {
                    callbacks: {
                        label: c => ` ${c.label}: ${formatCurrency(c.raw)}`
                    }
                }
            }
        }
    });
}

// ── Storage distribution pie ───────────────────────────────────────────────────
function loadStorageChart(data) {
    const wrap = document.getElementById('storageChartWrap');
    if (!wrap) return;

    const entries = Object.entries(data).filter(([, v]) => v > 0);
    if (entries.length === 0) {
        wrap.innerHTML = emptyStateHTML('💳', 'No storage data yet. Add transactions with a storage type!');
        return;
    }

    if (!document.getElementById('storageChart')) {
        wrap.innerHTML = '<canvas id="storageChart" style="max-height:200px;"></canvas>';
    }
    const ctx = document.getElementById('storageChart').getContext('2d');
    if (storageChartInstance) storageChartInstance.destroy();

    const { ticks } = chartColors();
    storageChartInstance = new Chart(ctx, {
        type: 'pie',
        data: {
            labels: entries.map(([k]) => k),
            datasets: [{
                data: entries.map(([, v]) => v),
                backgroundColor: PALETTE.slice(0, entries.length),
                borderWidth: 2,
                borderColor: isDarkMode() ? '#1F2937' : '#fff',
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    position: 'right',
                    labels: { color: ticks, font: { size: 11 }, boxWidth: 12, padding: 8 }
                },
                tooltip: {
                    callbacks: {
                        label: c => ` ${c.label}: ${formatCurrency(c.raw)}`
                    }
                }
            }
        }
    });
}

// ── Income sources pie ─────────────────────────────────────────────────────────
async function loadIncomeSourceChart(month, year) {
    const wrap = document.getElementById('incomeSourceChartWrap');
    if (!wrap) return;

    const now = new Date();
    const m   = month || now.getMonth() + 1;
    const y   = year  || now.getFullYear();

    try {
        const data    = await apiGet(`/analytics/income-sources?month=${m}&year=${y}`);
        const entries = Object.entries(data).filter(([, v]) => v > 0);

        if (entries.length === 0) {
            wrap.innerHTML = emptyStateHTML('📈', 'No income source data for this period.');
            return;
        }

        if (!document.getElementById('incomeSourceChart')) {
            wrap.innerHTML = '<canvas id="incomeSourceChart" style="max-height:200px;"></canvas>';
        }
        const ctx = document.getElementById('incomeSourceChart').getContext('2d');
        if (incomeSourceChartInstance) incomeSourceChartInstance.destroy();

        const total   = entries.reduce((s, [, v]) => s + v, 0);
        const { ticks } = chartColors();
        const COLORS  = ['#10B981','#6366F1','#F59E0B','#EF4444','#8B5CF6','#06B6D4','#84CC16','#F97316'];

        incomeSourceChartInstance = new Chart(ctx, {
            type: 'pie',
            data: {
                labels: entries.map(([k]) => k),
                datasets: [{
                    data: entries.map(([, v]) => v),
                    backgroundColor: COLORS.slice(0, entries.length),
                    borderWidth: 2,
                    borderColor: isDarkMode() ? '#1F2937' : '#fff',
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        position: 'right',
                        labels: { color: ticks, font: { size: 11 }, boxWidth: 12, padding: 8 }
                    },
                    tooltip: {
                        callbacks: {
                            label: c => {
                                const pct = total > 0 ? (c.raw / total * 100).toFixed(1) : 0;
                                return ` ${c.label}: ${formatCurrency(c.raw)} (${pct}%)`;
                            }
                        }
                    }
                }
            }
        });
    } catch (e) {
        console.error('Error loading income source chart:', e);
        wrap.innerHTML = emptyStateHTML('📈', 'Could not load income source data.');
    }
}

// ── Financial Health Score ─────────────────────────────────────────────────────
async function loadHealthScore() {
    const scoreEl    = document.getElementById('healthScoreValue');
    const gradeEl    = document.getElementById('healthScoreGrade');
    const breakdownEl = document.getElementById('healthScoreBreakdown');
    const adviceEl   = document.getElementById('healthScoreAdvice');
    const circleEl   = document.getElementById('healthScoreCircle');
    if (!scoreEl) return;

    try {
        const hs = await apiGet('/analytics/health-score');

        const score = hs.score ?? 0;
        const grade = hs.grade ?? '—';

        scoreEl.textContent = score;
        gradeEl.textContent = 'Grade ' + grade;

        const gradeRing = {
            A: 'ring-emerald-400 dark:ring-emerald-500',
            B: 'ring-blue-400 dark:ring-blue-500',
            C: 'ring-yellow-400 dark:ring-yellow-500',
            D: 'ring-orange-400 dark:ring-orange-500',
            F: 'ring-red-400 dark:ring-red-500',
        };
        const gradeText = {
            A: 'text-emerald-600 dark:text-emerald-400',
            B: 'text-blue-600 dark:text-blue-400',
            C: 'text-yellow-600 dark:text-yellow-400',
            D: 'text-orange-600 dark:text-orange-400',
            F: 'text-red-600 dark:text-red-400',
        };

        if (circleEl) {
            circleEl.className = `inline-flex flex-col items-center justify-center w-24 h-24 rounded-full bg-gray-50 dark:bg-gray-800 ring-4 ${gradeRing[grade] || 'ring-gray-300'}`;
        }
        if (scoreEl) scoreEl.className = `text-3xl font-bold leading-none ${gradeText[grade] || 'text-gray-800 dark:text-white'}`;
        if (adviceEl) adviceEl.textContent = hs.advice || '';

        const b = hs.breakdown || {};
        const bars = [
            { label: 'Savings Rate',     score: b.savingsRate     || 0, max: 30 },
            { label: 'Budget Adherence', score: b.budgetAdherence || 0, max: 20 },
            { label: 'Spending Trend',   score: b.spendingTrend   || 0, max: 20 },
            { label: 'Investments',      score: b.hasInvestments  || 0, max: 15 },
            { label: 'Emergency Reserve',score: b.emergencyReserve|| 0, max: 15 },
        ];

        breakdownEl.innerHTML = bars.map(bar => {
            const pct      = Math.round((bar.score / bar.max) * 100);
            const barColor = pct >= 80 ? 'bg-emerald-500' : pct >= 50 ? 'bg-yellow-500' : 'bg-red-500';
            return `
            <div>
                <div class="flex justify-between text-xs text-gray-600 dark:text-gray-400 mb-1">
                    <span>${bar.label}</span>
                    <span class="font-medium">${bar.score}/${bar.max}</span>
                </div>
                <div class="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-1.5 overflow-hidden">
                    <div class="${barColor} h-1.5 rounded-full transition-all duration-700" style="width:${pct}%"></div>
                </div>
            </div>`;
        }).join('');

    } catch (e) {
        console.error('Error loading health score:', e);
        if (scoreEl)     scoreEl.textContent = '—';
        if (gradeEl)     gradeEl.textContent  = '—';
        if (breakdownEl) breakdownEl.innerHTML = `
            <p class="text-sm text-gray-500 dark:text-gray-400">
                Not enough data yet. Add more transactions to see your health score.
            </p>`;
    }
}

// ── AI Financial Insights ──────────────────────────────────────────────────────
async function loadInsights() {
    const container = document.getElementById('insightsContainer');
    if (!container) return;
    try {
        const insights = await apiGet('/analytics/insights');

        if (!insights || insights.length === 0) {
            container.innerHTML = `
                <div class="text-center py-4">
                    <div class="text-3xl mb-2">💡</div>
                    <p class="text-sm text-gray-500 dark:text-gray-400">Not enough data yet. Add more transactions to see personalised insights.</p>
                </div>`;
            return;
        }

        container.innerHTML = insights.map(insight => `
            <div class="flex items-start gap-3 p-3 rounded-lg bg-indigo-50 dark:bg-indigo-950 border border-indigo-100 dark:border-indigo-900">
                <p class="text-sm text-gray-800 dark:text-gray-200 leading-relaxed">${insight}</p>
            </div>`).join('');
    } catch (e) {
        console.error('Error loading insights:', e);
        container.innerHTML = `
            <p class="text-sm text-gray-500 dark:text-gray-400">Could not load insights. Check your connection and try refreshing.</p>`;
    }
}

// ── Budget Status ──────────────────────────────────────────────────────────────
function loadBudgetStatus(budgets) {
    const container = document.getElementById('budgetStatus');
    if (!container) return;

    if (!budgets || budgets.length === 0) {
        container.innerHTML = `
            <div class="text-center py-4">
                <p class="text-sm text-gray-500 dark:text-gray-400">No active budgets. <a href="/budgets" class="text-indigo-600 dark:text-indigo-400 hover:underline">Create a budget</a> to start tracking spending.</p>
            </div>`;
        return;
    }

    container.innerHTML = budgets.map(b => {
        const pct        = b.percentageUsed || 0;
        const barColor   = pct >= 100 ? 'bg-red-500' : pct >= 80 ? 'bg-yellow-500' : 'bg-emerald-500';
        const labelColor = pct >= 100 ? 'text-red-600 dark:text-red-400' : pct >= 80 ? 'text-yellow-600 dark:text-yellow-400' : 'text-emerald-600 dark:text-emerald-400';
        const statusIcon = pct >= 100 ? '🔴' : pct >= 80 ? '🟡' : '🟢';
        const bCurrency  = b.currency || 'USD';
        return `
        <div class="border-b border-gray-100 dark:border-gray-700 last:border-0 pb-3 last:pb-0">
            <div class="flex justify-between items-center mb-1.5">
                <span class="text-sm font-medium text-gray-900 dark:text-white">${b.category}</span>
                <span class="text-xs text-gray-500 dark:text-gray-400">
                    ${formatCurrency(b.spentAmount, bCurrency)} / ${formatCurrency(b.limitAmount, bCurrency)}
                </span>
            </div>
            <div class="progress-bar mb-1">
                <div class="${barColor} progress-fill" style="width:${Math.min(pct, 100)}%"></div>
            </div>
            <div class="flex justify-between">
                <span class="text-xs text-gray-400 dark:text-gray-500">${pct.toFixed(1)}% used</span>
                <span class="text-xs font-medium ${labelColor}">${statusIcon} ${pct >= 100 ? 'Over budget' : pct >= 80 ? 'Warning' : 'On track'}</span>
            </div>
        </div>`;
    }).join('');
}

// ── Notifications ──────────────────────────────────────────────────────────────
async function loadNotifications() {
    try {
        const alerts = await apiGet('/alerts/recent').catch(() => []);
        const unread = alerts.filter(a => !a.isRead).length;
        const badge  = document.getElementById('notificationBadge');
        if (badge) {
            if (unread > 0) { badge.textContent = unread > 9 ? '9+' : unread; badge.classList.remove('hidden'); }
            else            { badge.classList.add('hidden'); }
        }

        const list = document.getElementById('notificationList');
        if (!list) return;
        if (!alerts.length) {
            list.innerHTML = `<p class="text-xs text-gray-500 dark:text-gray-400 text-center py-4">No notifications</p>`;
            return;
        }
        list.innerHTML = alerts.map(a => `
            <div class="p-2.5 rounded-lg ${a.isRead ? 'bg-gray-50 dark:bg-gray-700' : 'bg-blue-50 dark:bg-blue-950'} mb-1.5">
                <div class="flex justify-between items-start gap-2">
                    <div class="flex-1">
                        <p class="text-xs font-semibold text-gray-900 dark:text-white">${a.title}</p>
                        <p class="text-xs text-gray-600 dark:text-gray-400 mt-0.5">${a.message}</p>
                    </div>
                    ${!a.isRead ? '<span class="status-dot status-active flex-shrink-0 mt-1"></span>' : ''}
                </div>
            </div>`).join('');
    } catch (e) {
        console.error('Error loading notifications:', e);
    }
}

// ── Boot ───────────────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    // Notification panel toggle
    const notifBtn   = document.getElementById('notificationBtn');
    const notifPanel = document.getElementById('notificationPanel');
    const markAllBtn = document.getElementById('markAllRead');

    if (notifBtn && notifPanel) {
        notifBtn.addEventListener('click', e => {
            e.stopPropagation();
            notifPanel.classList.toggle('hidden');
        });
        document.addEventListener('click', e => {
            if (!notifPanel.contains(e.target) && !notifBtn.contains(e.target)) {
                notifPanel.classList.add('hidden');
            }
        });
    }
    if (markAllBtn) {
        markAllBtn.addEventListener('click', async () => {
            try { await apiPatch('/alerts/mark-all-read', {}); loadNotifications(); }
            catch (e) { console.error('Mark read error:', e); }
        });
    }

    // Time range buttons
    document.querySelectorAll('#rangeButtons .range-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('#rangeButtons .range-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            currentRange = btn.dataset.range;
            loadTrendsChart(currentRange);
        });
    });

    // Income source month/year selects
    const monthSel = document.getElementById('incomeSourceMonth');
    const yearSel  = document.getElementById('incomeSourceYear');
    if (yearSel) {
        const now = new Date();
        for (let y = now.getFullYear(); y >= now.getFullYear() - 3; y--) {
            const opt = document.createElement('option');
            opt.value = y; opt.textContent = y;
            if (y === now.getFullYear()) opt.selected = true;
            yearSel.appendChild(opt);
        }
    }
    if (monthSel) {
        monthSel.value = new Date().getMonth() + 1;
        const onChange = () => loadIncomeSourceChart(+monthSel.value, +(yearSel?.value));
        monthSel.addEventListener('change', onChange);
        if (yearSel) yearSel.addEventListener('change', onChange);
    }

    // Initial load + auto-refresh
    loadDashboard().then(() => loadNotifications());
    setInterval(() => { loadDashboard(); loadNotifications(); }, 300_000);
});
