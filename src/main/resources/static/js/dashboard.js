// dashboard.js — Smart Finance Dashboard
// Depends on: app.js (formatCurrency, apiGet, apiPatch, formatDate, showError, emptyStateHTML)

let categoryChartInstance = null;

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

        const storageValues = Object.values(storageData || {});
        const totalBalance  = storageValues.reduce((sum, v) => sum + (v > 0 ? v : 0), 0);

        const monthlyIncome   = Number(analytics.totalIncome   || 0);
        const monthlyExpenses = Number(analytics.totalExpenses || 0);
        const netFlow         = Number(analytics.netSavings    || 0);

        _set('totalBalance',    formatCurrency(totalBalance));
        _set('monthlyIncome',   formatCurrency(monthlyIncome));
        _set('monthlyExpenses', formatCurrency(monthlyExpenses));

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

        loadCategoryChart(analytics.expensesByCategory || {});
        loadRecentTransactions();
        loadHealthScore();
        loadInsights();
    } catch (e) {
        showError('Failed to load dashboard data.');
    }
}

function _set(id, value) {
    const el = document.getElementById(id);
    if (el) el.textContent = value;
}

// ── Recent Transactions ────────────────────────────────────────────────────────
async function loadRecentTransactions() {
    const container = document.getElementById('recentTransactions');
    if (!container) return;
    try {
        const txs  = await apiGet('/transactions');
        const list = (Array.isArray(txs) ? txs : (txs.content || [])).slice(0, 8);
        if (!list.length) {
            container.innerHTML = emptyStateHTML('💸', 'No transactions yet. Add your first one!');
            return;
        }
        container.innerHTML = list.map(tx => {
            const isIncome = tx.type === 'INCOME';
            const sign     = isIncome ? '+' : '-';
            const color    = isIncome
                ? 'text-emerald-600 dark:text-emerald-400'
                : 'text-red-600 dark:text-red-400';
            const date = tx.transactionDate
                ? new Date(tx.transactionDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
                : '—';
            return `
            <div class="flex items-center gap-3 py-3 first:pt-0">
                <div class="w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 ${isIncome ? 'bg-emerald-100 dark:bg-emerald-900' : 'bg-red-100 dark:bg-red-900'}">
                    <span class="text-sm">${isIncome ? '↑' : '↓'}</span>
                </div>
                <div class="flex-1 min-w-0">
                    <p class="text-sm font-medium text-gray-900 dark:text-white truncate">${tx.description || tx.category}</p>
                    <p class="text-xs text-gray-400 dark:text-gray-500">${tx.category} · ${date}</p>
                </div>
                <span class="text-sm font-semibold ${color} flex-shrink-0">${sign}${formatCurrency(tx.amount, tx.currency || 'USD')}</span>
            </div>`;
        }).join('');
    } catch (e) {
        container.innerHTML = `<p class="text-sm text-gray-400 text-center py-4">Could not load transactions.</p>`;
    }
}

// ── Spending by Category doughnut ──────────────────────────────────────────────
function loadCategoryChart(expensesByCategory) {
    const wrap = document.getElementById('categoryChartWrap');
    if (!wrap) return;

    const labels = Object.keys(expensesByCategory);
    const values = Object.values(expensesByCategory).map(Number);

    if (!labels.length) {
        wrap.innerHTML = emptyStateHTML('📊', 'No expense data yet this month.');
        return;
    }

    if (!document.getElementById('categoryChart')) {
        wrap.innerHTML = '<canvas id="categoryChart" style="max-height:220px;"></canvas>';
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
                borderWidth: 3,
                borderColor: isDarkMode() ? '#111827' : '#ffffff',
                hoverBorderWidth: 0,
                hoverOffset: 6,
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            cutout: '65%',
            animation: { animateRotate: true, duration: 600 },
            plugins: {
                legend: {
                    position: 'right',
                    labels: { color: ticks, font: { size: 11 }, boxWidth: 10, padding: 8 }
                },
                tooltip: {
                    backgroundColor: isDarkMode() ? '#1F2937' : '#fff',
                    borderColor: isDarkMode() ? '#374151' : '#E5E7EB',
                    borderWidth: 1,
                    titleColor: isDarkMode() ? '#F9FAFB' : '#111827',
                    bodyColor: isDarkMode() ? '#D1D5DB' : '#374151',
                    callbacks: {
                        label: c => ` ${c.label}: ${formatCurrency(c.raw)}`
                    }
                }
            }
        }
    });
}

// ── Financial Health Score ─────────────────────────────────────────────────────
async function loadHealthScore() {
    const scoreEl     = document.getElementById('healthScoreValue');
    const gradeEl     = document.getElementById('healthScoreGrade');
    const breakdownEl = document.getElementById('healthScoreBreakdown');
    const adviceEl    = document.getElementById('healthScoreAdvice');
    const circleEl    = document.getElementById('healthScoreCircle');
    if (!scoreEl) return;

    try {
        const hs    = await apiGet('/analytics/health-score');
        const score = hs.score ?? 0;
        const grade = hs.grade ?? '—';

        scoreEl.textContent = score;
        gradeEl.textContent = 'Grade ' + grade;

        const gradeRing = { A: 'ring-emerald-400', B: 'ring-blue-400', C: 'ring-yellow-400', D: 'ring-orange-400', F: 'ring-red-400' };
        const gradeText = { A: 'text-emerald-600 dark:text-emerald-400', B: 'text-blue-600 dark:text-blue-400', C: 'text-yellow-600 dark:text-yellow-400', D: 'text-orange-600 dark:text-orange-400', F: 'text-red-600 dark:text-red-400' };

        if (circleEl) circleEl.className = `inline-flex flex-col items-center justify-center w-24 h-24 rounded-full bg-gray-50 dark:bg-gray-800 ring-4 ${gradeRing[grade] || 'ring-gray-300'} flex-shrink-0`;
        if (scoreEl) scoreEl.className = `text-3xl font-bold leading-none ${gradeText[grade] || 'text-gray-800 dark:text-white'}`;
        if (adviceEl) adviceEl.textContent = hs.advice || '';

        const b    = hs.breakdown || {};
        const bars = [
            { label: 'Savings Rate',      score: b.savingsRate      || 0, max: 30 },
            { label: 'Budget Adherence',  score: b.budgetAdherence  || 0, max: 20 },
            { label: 'Spending Trend',    score: b.spendingTrend    || 0, max: 20 },
            { label: 'Investments',       score: b.hasInvestments   || 0, max: 15 },
            { label: 'Emergency Reserve', score: b.emergencyReserve || 0, max: 15 },
        ];

        if (breakdownEl) breakdownEl.innerHTML = bars.map(bar => {
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
        if (scoreEl)     scoreEl.textContent  = '—';
        if (gradeEl)     gradeEl.textContent   = '—';
        if (breakdownEl) breakdownEl.innerHTML = `<p class="text-xs text-gray-500 dark:text-gray-400">Add more transactions to see your health score.</p>`;
    }
}

// ── Insights ───────────────────────────────────────────────────────────────────
async function loadInsights() {
    const container = document.getElementById('insightsContainer');
    if (!container) return;
    try {
        const insights = await apiGet('/analytics/insights');
        if (!insights || !insights.length) {
            container.innerHTML = `<div class="text-center py-4"><p class="text-sm text-gray-500 dark:text-gray-400">Add more transactions to see personalised insights.</p></div>`;
            return;
        }
        container.innerHTML = insights.map(insight => `
            <div class="flex items-start gap-3 p-3 rounded-lg bg-indigo-50 dark:bg-indigo-950 border border-indigo-100 dark:border-indigo-900">
                <p class="text-sm text-gray-800 dark:text-gray-200 leading-relaxed">${insight}</p>
            </div>`).join('');
    } catch (e) {
        container.innerHTML = `<p class="text-sm text-gray-500 dark:text-gray-400 text-center">Could not load insights.</p>`;
    }
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
    } catch (e) {}
}

// ── Boot ───────────────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    const notifBtn   = document.getElementById('notificationBtn');
    const notifPanel = document.getElementById('notificationPanel');
    const markAllBtn = document.getElementById('markAllRead');

    if (notifBtn && notifPanel) {
        notifBtn.addEventListener('click', e => { e.stopPropagation(); notifPanel.classList.toggle('hidden'); });
        document.addEventListener('click', e => {
            if (!notifPanel.contains(e.target) && !notifBtn.contains(e.target)) notifPanel.classList.add('hidden');
        });
    }
    if (markAllBtn) {
        markAllBtn.addEventListener('click', async () => {
            try { await apiPatch('/alerts/mark-all-read', {}); loadNotifications(); } catch (e) {}
        });
    }

    loadDashboard().then(() => loadNotifications());
    setInterval(() => { loadDashboard(); loadNotifications(); }, 300_000);
});
