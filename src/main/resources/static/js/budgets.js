// Budgets JavaScript — Smart Finance Dashboard
// NOTE: formatCurrency, apiGet, apiPost, apiPut, apiDelete,
//       showSuccess, showError are provided by app.js

function firstDayOfMonth() {
    const now = new Date();
    return new Date(now.getFullYear(), now.getMonth(), 1).toISOString().split('T')[0];
}

function lastDayOfMonth() {
    const now = new Date();
    return new Date(now.getFullYear(), now.getMonth() + 1, 0).toISOString().split('T')[0];
}

document.addEventListener('DOMContentLoaded', () => {
    loadBudgets();

    document.getElementById('budgetForm').addEventListener('submit', async (e) => {
        e.preventDefault();
        const submitBtn = e.target.querySelector('[type="submit"]');
        setLoading(submitBtn, true);

        const id = document.getElementById('budgetId').value;
        const budget = {
            category: document.getElementById('budgetCategory').value,
            limitAmount: parseFloat(document.getElementById('budgetLimit').value),
            startDate: document.getElementById('budgetStartDate').value,
            endDate: document.getElementById('budgetEndDate').value,
            period: 'MONTHLY',
            currency: 'USD'
        };

        try {
            if (id) {
                await apiPut(`/budgets/${id}`, budget);
                showSuccess('Budget updated.');
            } else {
                await apiPost('/budgets', budget);
                showSuccess('Budget created.');
            }
            closeBudgetModal();
            loadBudgets();
        } catch (err) {
            showError('Failed to save budget.');
        } finally {
            setLoading(submitBtn, false);
        }
    });
});

async function loadBudgets() {
    try {
        const budgets = await apiGet('/budgets');
        renderBudgets(budgets);
    } catch (err) {
        showError('Failed to load budgets.');
    }
}

function renderBudgets(budgets) {
    const container = document.getElementById('budgetList');
    let totalBudget = 0, totalSpent = 0;

    if (!budgets.length) {
        container.innerHTML = `<div class="card text-center text-gray-500 dark:text-gray-400 py-12">
            <p class="text-lg">No budgets yet. Create one to start tracking your spending limits.</p>
        </div>`;
        updateSummary(0, 0);
        return;
    }

    budgets.forEach(b => {
        totalBudget += parseFloat(b.limitAmount || 0);
        totalSpent += parseFloat(b.spentAmount || 0);
    });
    updateSummary(totalBudget, totalSpent);

    container.innerHTML = budgets.map(b => {
        const limit = parseFloat(b.limitAmount || 0);
        const spent = parseFloat(b.spentAmount || 0);
        const pct = limit > 0 ? Math.min((spent / limit) * 100, 100) : 0;
        const remaining = limit - spent;
        const isOver = spent > limit;
        const barColor = isOver ? 'bg-red-500' : pct > 80 ? 'bg-yellow-500' : 'bg-green-500';
        const dateRange = b.startDate ? `${b.startDate} → ${b.endDate}` : '';

        return `
        <div class="card">
            <div class="flex justify-between items-start mb-3">
                <div>
                    <span class="inline-flex items-center px-3 py-1 rounded-full text-sm font-medium bg-indigo-100 dark:bg-indigo-900 text-indigo-800 dark:text-indigo-200">
                        ${b.category}
                    </span>
                    <span class="ml-2 text-xs text-gray-500 dark:text-gray-400">${dateRange}</span>
                </div>
                <div class="flex space-x-2">
                    <button onclick="editBudget(${b.id})" class="text-xs text-indigo-600 hover:text-indigo-800 dark:text-indigo-400 px-2 py-1 border border-indigo-300 rounded">Edit</button>
                    <button onclick="deleteBudget(${b.id})" class="text-xs text-red-600 hover:text-red-800 dark:text-red-400 px-2 py-1 border border-red-300 rounded">Delete</button>
                </div>
            </div>
            <div class="flex justify-between text-sm mb-2">
                <span class="text-gray-600 dark:text-gray-400">Spent: <strong class="text-gray-900 dark:text-white">${formatCurrency(spent)}</strong></span>
                <span class="text-gray-600 dark:text-gray-400">Limit: <strong class="text-gray-900 dark:text-white">${formatCurrency(limit)}</strong></span>
            </div>
            <div class="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-3 mb-2">
                <div class="${barColor} h-3 rounded-full transition-all duration-500" style="width: ${pct}%"></div>
            </div>
            <div class="flex justify-between text-xs">
                <span class="${isOver ? 'text-red-600 font-semibold' : 'text-gray-500 dark:text-gray-400'}">
                    ${isOver ? 'Over budget by ' + formatCurrency(spent - limit) : 'Remaining: ' + formatCurrency(remaining)}
                </span>
                <span class="text-gray-500 dark:text-gray-400">${pct.toFixed(1)}%</span>
            </div>
        </div>`;
    }).join('');
}

function updateSummary(totalBudget, totalSpent) {
    document.getElementById('totalBudget').textContent = formatCurrency(totalBudget);
    document.getElementById('totalSpent').textContent = formatCurrency(totalSpent);
    document.getElementById('totalRemaining').textContent = formatCurrency(Math.max(totalBudget - totalSpent, 0));
}

function openBudgetModal(budget = null) {
    document.getElementById('budgetModalTitle').textContent = budget ? 'Edit Budget' : 'Add Budget';
    document.getElementById('budgetId').value = budget?.id || '';
    document.getElementById('budgetCategory').value = budget?.category || '';
    document.getElementById('budgetLimit').value = budget?.limitAmount || '';
    document.getElementById('budgetStartDate').value = budget?.startDate || firstDayOfMonth();
    document.getElementById('budgetEndDate').value = budget?.endDate || lastDayOfMonth();
    document.getElementById('budgetModal').classList.remove('hidden');
}

function closeBudgetModal() {
    document.getElementById('budgetModal').classList.add('hidden');
    document.getElementById('budgetForm').reset();
}

async function editBudget(id) {
    try {
        const budget = await apiGet(`/budgets/${id}`);
        openBudgetModal(budget);
    } catch (err) {
        showError('Failed to load budget.');
    }
}

async function deleteBudget(id) {
    if (!confirm('Delete this budget?')) return;
    try {
        await apiDelete(`/budgets/${id}`);
        showSuccess('Budget deleted.');
        loadBudgets();
    } catch (err) {
        showError('Failed to delete budget.');
    }
}
