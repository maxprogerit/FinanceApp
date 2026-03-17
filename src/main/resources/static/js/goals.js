// Goals JavaScript — Smart Finance Dashboard
// NOTE: formatCurrency, formatDate, daysUntil, apiGet, apiPost, apiPut, apiDelete, apiPatch,
//       showSuccess, showError are provided by app.js

let contributeGoalId = null;

document.addEventListener('DOMContentLoaded', () => {
    const minDate = new Date();
    minDate.setDate(minDate.getDate() + 1);
    document.getElementById('goalDeadline').min = minDate.toISOString().split('T')[0];
    loadGoals();

    document.getElementById('goalForm').addEventListener('submit', async (e) => {
        e.preventDefault();
        const submitBtn = e.target.querySelector('[type="submit"]');
        setLoading(submitBtn, true);

        const id = document.getElementById('goalId').value;
        const goal = {
            goalName: document.getElementById('goalName').value,
            description: document.getElementById('goalDescription').value,
            targetAmount: parseFloat(document.getElementById('goalTarget').value),
            currentAmount: parseFloat(document.getElementById('goalCurrent').value || 0),
            targetDate: document.getElementById('goalDeadline').value,
            startDate: new Date().toISOString().split('T')[0],
            currency: 'USD',
            status: 'IN_PROGRESS'
        };

        try {
            if (id) {
                await apiPut(`/goals/${id}`, goal);
                showSuccess('Goal updated.');
            } else {
                await apiPost('/goals', goal);
                showSuccess('Goal created.');
            }
            closeGoalModal();
            loadGoals();
        } catch (err) {
            showError('Failed to save goal.');
        } finally {
            setLoading(submitBtn, false);
        }
    });
});

async function loadGoals() {
    try {
        const goals = await apiGet('/goals');
        renderGoals(goals);
        updateSummary(goals);
    } catch (err) {
        showError('Failed to load goals.');
    }
}

function updateSummary(goals) {
    document.getElementById('totalGoals').textContent = goals.length;
    const completed = goals.filter(g => parseFloat(g.currentAmount || 0) >= parseFloat(g.targetAmount || 1));
    document.getElementById('completedGoals').textContent = completed.length;
    const totalSaved = goals.reduce((s, g) => s + parseFloat(g.currentAmount || 0), 0);
    document.getElementById('totalSaved').textContent = formatCurrency(totalSaved);
}

function renderGoals(goals) {
    const container = document.getElementById('goalsList');
    if (!goals.length) {
        container.innerHTML = `<div class="card text-center text-gray-500 dark:text-gray-400 py-12 col-span-full">
            <p class="text-5xl mb-4">🎯</p>
            <p class="text-lg">No goals yet. Create your first financial goal!</p>
        </div>`;
        return;
    }

    container.innerHTML = goals.map(g => {
        const target = parseFloat(g.targetAmount || 0);
        const current = parseFloat(g.currentAmount || 0);
        const pct = target > 0 ? Math.min((current / target) * 100, 100) : 0;
        const isComplete = current >= target;
        const days = daysUntil(g.targetDate || g.deadline);
        const barColor = isComplete ? 'bg-green-500' : pct > 75 ? 'bg-blue-500' : pct > 40 ? 'bg-indigo-500' : 'bg-purple-500';
        const daysText = days === null ? '' : days < 0
            ? `<span class="text-red-500 text-xs">⚠ ${Math.abs(days)} days overdue</span>`
            : `<span class="text-gray-500 dark:text-gray-400 text-xs">${days} days left</span>`;

        return `<div class="card">
            <div class="flex justify-between items-start mb-3">
                <div>
                    <h4 class="font-semibold text-gray-900 dark:text-white text-lg">${g.goalName || g.name}</h4>
                    ${g.description ? `<p class="text-sm text-gray-500 dark:text-gray-400">${g.description}</p>` : ''}
                </div>
                <div class="flex items-center space-x-1">
                    ${isComplete ? '<span class="text-green-500 text-xl">✅</span>' : ''}
                    <button onclick="editGoal(${g.id})" class="text-xs text-indigo-600 hover:text-indigo-800 px-2 py-1 border border-indigo-300 rounded">Edit</button>
                    <button onclick="deleteGoal(${g.id})" class="text-xs text-red-600 hover:text-red-800 px-2 py-1 border border-red-300 rounded">Delete</button>
                </div>
            </div>

            <div class="flex justify-between text-sm mb-2">
                <span class="text-gray-600 dark:text-gray-400">Saved: <strong class="text-gray-900 dark:text-white">${formatCurrency(current)}</strong></span>
                <span class="text-gray-600 dark:text-gray-400">Target: <strong class="text-gray-900 dark:text-white">${formatCurrency(target)}</strong></span>
            </div>

            <div class="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-3 mb-2">
                <div class="${barColor} h-3 rounded-full transition-all duration-500" style="width: ${pct}%"></div>
            </div>

            <div class="flex justify-between items-center">
                <span class="text-sm font-semibold text-gray-700 dark:text-gray-300">${pct.toFixed(1)}% complete</span>
                ${daysText}
            </div>

            ${g.targetDate || g.deadline ? `<p class="text-xs text-gray-400 mt-2">Target date: ${formatDate(g.targetDate || g.deadline)}</p>` : ''}

            ${!isComplete ? `<div class="mt-3 pt-3 border-t border-gray-100 dark:border-gray-700">
                <button onclick="openContributeModal(${g.id})" class="text-sm text-indigo-600 hover:text-indigo-700 dark:text-indigo-400 font-medium">+ Add Contribution</button>
            </div>` : ''}
        </div>`;
    }).join('');
}

function openGoalModal(goal = null) {
    document.getElementById('goalModalTitle').textContent = goal ? 'Edit Goal' : 'New Goal';
    document.getElementById('goalId').value = goal?.id || '';
    document.getElementById('goalName').value = goal?.goalName || goal?.name || '';
    document.getElementById('goalDescription').value = goal?.description || '';
    document.getElementById('goalTarget').value = goal?.targetAmount || '';
    document.getElementById('goalCurrent').value = goal?.currentAmount || '0';
    document.getElementById('goalDeadline').value = (goal?.targetDate || goal?.deadline)?.split('T')[0] || '';
    document.getElementById('goalModal').classList.remove('hidden');
}

function closeGoalModal() {
    document.getElementById('goalModal').classList.add('hidden');
    document.getElementById('goalForm').reset();
}

async function editGoal(id) {
    try {
        const goal = await apiGet(`/goals/${id}`);
        openGoalModal(goal);
    } catch (err) {
        showError('Failed to load goal.');
    }
}

async function deleteGoal(id) {
    if (!confirm('Delete this goal?')) return;
    try {
        await apiDelete(`/goals/${id}`);
        showSuccess('Goal deleted.');
        loadGoals();
    } catch (err) {
        showError('Failed to delete goal.');
    }
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
    if (!amount || amount <= 0) {
        showError('Please enter a valid amount.');
        return;
    }
    try {
        await apiPatch(`/goals/${contributeGoalId}/add?amount=${amount}`, {});
        showSuccess('Contribution added!');
        closeContributeModal();
        loadGoals();
    } catch (err) {
        showError('Failed to add contribution.');
    }
}
