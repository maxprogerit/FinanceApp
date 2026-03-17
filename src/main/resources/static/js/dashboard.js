// Dashboard JavaScript — Smart Finance Dashboard
// NOTE: formatCurrency, formatDate, apiGet, apiPatch, showError, API are provided by app.js

let incomeExpensesChartInstance = null;
let categoryChartInstance = null;

async function loadDashboard() {
    try {
        const [analytics, portfolio] = await Promise.all([
            apiGet('/analytics/dashboard'),
            apiGet('/investments/portfolio/summary').catch(() => ({}))
        ]);

        document.getElementById('totalIncome').textContent    = formatCurrency(analytics.totalIncome);
        document.getElementById('totalExpenses').textContent  = formatCurrency(analytics.totalExpenses);
        document.getElementById('netSavings').textContent     = formatCurrency(analytics.netSavings);
        document.getElementById('portfolioValue').textContent = formatCurrency(portfolio.totalPortfolioValue || 0);

        loadIncomeExpensesChart();
        loadCategoryChart(analytics.expensesByCategory || {});
        loadInsights();
        loadBudgetStatus(analytics.activeBudgets || []);
        loadNotifications();
        loadRecentTransactions();
    } catch (e) {
        showError('Failed to load dashboard data.');
    }
}

async function loadIncomeExpensesChart() {
    try {
        const data = await apiGet('/analytics/trends?months=6');
        const monthly = data.monthlyData || [];

        const ctx = document.getElementById('incomeExpensesChart').getContext('2d');
        if (incomeExpensesChartInstance) incomeExpensesChartInstance.destroy();

        incomeExpensesChartInstance = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: monthly.map(d => d.month),
                datasets: [
                    {
                        label: 'Income',
                        data: monthly.map(d => d.income),
                        backgroundColor: 'rgba(34, 197, 94, 0.8)',
                        borderColor: 'rgb(34, 197, 94)',
                        borderWidth: 1,
                        borderRadius: 4
                    },
                    {
                        label: 'Expenses',
                        data: monthly.map(d => d.expenses),
                        backgroundColor: 'rgba(239, 68, 68, 0.8)',
                        borderColor: 'rgb(239, 68, 68)',
                        borderWidth: 1,
                        borderRadius: 4
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: true,
                plugins: { legend: { position: 'bottom' } },
                scales: { y: { beginAtZero: true } }
            }
        });
    } catch (e) {
        console.error('Error loading trends chart:', e);
    }
}

function loadCategoryChart(expensesByCategory) {
    const labels = Object.keys(expensesByCategory);
    const values = Object.values(expensesByCategory);

    const ctx = document.getElementById('categoryChart').getContext('2d');
    if (categoryChartInstance) categoryChartInstance.destroy();

    if (labels.length === 0) {
        ctx.canvas.parentNode.innerHTML = `
            <div class="empty-state">
                <div class="empty-icon">📊</div>
                <p>No spending data yet. Add some expenses!</p>
            </div>`;
        return;
    }

    const COLORS = [
        '#6366F1','#10B981','#F59E0B','#EF4444','#8B5CF6',
        '#06B6D4','#84CC16','#F97316','#EC4899','#64748B'
    ];

    categoryChartInstance = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels,
            datasets: [{
                data: values,
                backgroundColor: COLORS.slice(0, labels.length),
                borderWidth: 2,
                borderColor: '#fff'
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: true,
            plugins: {
                legend: { position: 'right' },
                tooltip: {
                    callbacks: { label: c => ` ${formatCurrency(c.raw)}` }
                }
            }
        }
    });
}

async function loadInsights() {
    const container = document.getElementById('insightsContainer');
    try {
        const insights = await apiGet('/analytics/insights');

        if (insights.length === 0) {
            container.innerHTML = `
                <div class="empty-state">
                    <p>No insights yet. Add some transactions to get personalised tips!</p>
                </div>`;
            return;
        }

        container.innerHTML = insights.map(insight => `
            <div class="flex items-start gap-3 p-3 bg-blue-50 dark:bg-blue-900 rounded-lg
                        border border-blue-100 dark:border-blue-800 fade-in">
                <span class="text-blue-500 text-lg flex-shrink-0">💡</span>
                <p class="text-sm text-gray-800 dark:text-gray-200">${insight}</p>
            </div>`).join('');
    } catch (e) {
        container.innerHTML = `<p class="text-sm text-gray-500 dark:text-gray-400">Could not load insights.</p>`;
    }
}

function loadBudgetStatus(budgets) {
    const container = document.getElementById('budgetStatus');

    if (!budgets || budgets.length === 0) {
        container.innerHTML = `
            <div class="empty-state">
                <p>No active budgets. <a href="/budgets" class="text-indigo-600 hover:underline">Create a budget</a> to start tracking spending!</p>
            </div>`;
        return;
    }

    container.innerHTML = budgets.map(b => {
        const pct         = b.percentageUsed || 0;
        const barColor    = pct >= 100 ? 'bg-red-500' : pct >= 80 ? 'bg-yellow-500' : 'bg-green-500';
        const statusLabel = pct >= 100 ? '🔴 Over Budget' : pct >= 80 ? '🟡 Warning' : '🟢 On Track';

        return `
        <div class="border-b border-gray-100 dark:border-gray-700 last:border-0 pb-4 last:pb-0">
            <div class="flex justify-between items-center mb-1">
                <span class="font-medium text-sm text-gray-900 dark:text-white">${b.category}</span>
                <span class="text-xs text-gray-500 dark:text-gray-400">
                    ${formatCurrency(b.spentAmount)} / ${formatCurrency(b.limitAmount)}
                </span>
            </div>
            <div class="progress-bar">
                <div class="${barColor} progress-fill" style="width:${Math.min(pct, 100)}%"></div>
            </div>
            <div class="flex justify-between mt-1">
                <span class="text-xs text-gray-400 dark:text-gray-500">${pct.toFixed(1)}% used</span>
                <span class="text-xs font-medium">${statusLabel}</span>
            </div>
        </div>`;
    }).join('');
}

async function loadRecentTransactions() {
    const container = document.getElementById('recentTransactions');
    if (!container) return;
    try {
        const all = await apiGet('/transactions');
        const recent = all
            .sort((a, b) => new Date(b.transactionDate) - new Date(a.transactionDate))
            .slice(0, 5);

        if (!recent.length) {
            container.innerHTML = `<p class="text-sm text-gray-500 dark:text-gray-400 py-2">No recent transactions.</p>`;
            return;
        }

        container.innerHTML = recent.map(t => {
            const isIncome = t.type === 'INCOME';
            const sign  = isIncome ? '+' : '−';
            const color = isIncome ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400';
            return `
            <div class="flex justify-between items-center py-2 border-b border-gray-100 dark:border-gray-700 last:border-0">
                <div>
                    <p class="text-sm font-medium text-gray-900 dark:text-white">${t.category}</p>
                    <p class="text-xs text-gray-500 dark:text-gray-400">${formatDate(t.transactionDate)}</p>
                </div>
                <span class="text-sm font-semibold ${color}">
                    ${sign}${formatCurrency(t.amount, t.currency)}
                </span>
            </div>`;
        }).join('');
    } catch (e) {
        console.error('Error loading recent transactions:', e);
    }
}

async function loadNotifications() {
    try {
        const alerts = await apiGet('/alerts/recent');

        const unread = alerts.filter(a => !a.isRead).length;
        const badge  = document.getElementById('notificationBadge');
        if (unread > 0) { badge.textContent = unread; badge.classList.remove('hidden'); }
        else            { badge.classList.add('hidden'); }

        const list = document.getElementById('notificationList');
        if (!list) return;
        if (!alerts.length) {
            list.innerHTML = `<p class="text-sm text-gray-500 dark:text-gray-400 text-center py-4">No notifications</p>`;
            return;
        }

        list.innerHTML = alerts.map(a => `
            <div class="p-3 rounded-lg ${a.isRead ? 'bg-gray-50 dark:bg-gray-700' : 'bg-blue-50 dark:bg-blue-900'} mb-2">
                <div class="flex justify-between items-start">
                    <div class="flex-1">
                        <p class="font-medium text-sm text-gray-900 dark:text-white">${a.title}</p>
                        <p class="text-xs text-gray-600 dark:text-gray-400 mt-1">${a.message}</p>
                        <p class="text-xs text-gray-400 mt-1">${formatDate(a.createdAt)}</p>
                    </div>
                    ${!a.isRead ? '<span class="status-dot status-active ml-2 flex-shrink-0 mt-1"></span>' : ''}
                </div>
            </div>`).join('');
    } catch (e) {
        console.error('Error loading notifications:', e);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    const notificationBtn   = document.getElementById('notificationBtn');
    const notificationPanel = document.getElementById('notificationPanel');
    const markAllRead       = document.getElementById('markAllRead');

    if (notificationBtn && notificationPanel) {
        notificationBtn.addEventListener('click', e => {
            e.stopPropagation();
            notificationPanel.classList.toggle('hidden');
        });
        document.addEventListener('click', e => {
            if (!notificationPanel.contains(e.target) && !notificationBtn.contains(e.target)) {
                notificationPanel.classList.add('hidden');
            }
        });
    }

    if (markAllRead) {
        markAllRead.addEventListener('click', async () => {
            try {
                await apiPatch('/alerts/mark-all-read', {});
                loadNotifications();
            } catch (e) {
                console.error('Error marking alerts read:', e);
            }
        });
    }

    loadDashboard();
    setInterval(loadDashboard, 300_000);
});
