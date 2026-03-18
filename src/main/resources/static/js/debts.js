// Debts Page JavaScript
// NOTE: formatCurrency, formatDate, apiGet, apiPost, apiPut, apiDelete, apiPatch are provided by app.js

let allDebts = [];
let editingDebtId = null;

async function loadDebts() {
    try {
        const [debts, summary] = await Promise.all([
            apiGet('/debts'),
            apiGet('/debts/summary')
        ]);
        allDebts = debts;
        renderSummary(summary);
        renderTables(debts);
    } catch (e) {
        showError('Failed to load debts.');
    }
}

function renderSummary(summary) {
    const iOweEl = document.getElementById('totalIOwe');
    const theyOweEl = document.getElementById('totalTheyOwe');
    if (iOweEl) iOweEl.textContent = formatCurrency(summary.totalIOwe || 0);
    if (theyOweEl) theyOweEl.textContent = formatCurrency(summary.totalTheyOwe || 0);
}

function renderTables(debts) {
    const iOweTbody = document.getElementById('iOweTable');
    const theyOweTbody = document.getElementById('theyOweTable');

    const iOwe = debts.filter(d => d.direction === 'I_OWE');
    const theyOwe = debts.filter(d => d.direction === 'THEY_OWE');

    iOweTbody.innerHTML = renderRows(iOwe, 'To');
    theyOweTbody.innerHTML = renderRows(theyOwe, 'From');
}

function renderRows(debts, label) {
    if (debts.length === 0) {
        return `<tr><td colspan="6" class="px-6 py-8 text-center text-gray-500 dark:text-gray-400">No entries.</td></tr>`;
    }
    return debts.map(d => {
        const isOverdue = d.dueDate && new Date(d.dueDate) < new Date() && d.status === 'ACTIVE';
        const dueDateText = d.dueDate ? formatDate(d.dueDate) : '—';
        const dueDateClass = isOverdue ? 'text-red-600 font-medium' : 'text-gray-600 dark:text-gray-300';
        const statusBadge = d.status === 'SETTLED'
            ? '<span class="badge badge-success text-xs">Settled</span>'
            : '<span class="badge badge-warning text-xs">Active</span>';

        return `
        <tr class="hover:bg-gray-50 dark:hover:bg-gray-800 ${d.status === 'SETTLED' ? 'opacity-60' : ''}">
            <td class="px-6 py-4 text-sm font-medium text-gray-900 dark:text-white">${d.name}</td>
            <td class="px-6 py-4 text-sm font-semibold text-gray-900 dark:text-white">${formatCurrency(d.amount, d.currency)}</td>
            <td class="px-6 py-4 text-sm ${dueDateClass}">${dueDateText}${isOverdue ? ' ⚠️' : ''}</td>
            <td class="px-6 py-4 text-sm text-gray-600 dark:text-gray-300">${d.description || '—'}</td>
            <td class="px-6 py-4 text-sm">${statusBadge}</td>
            <td class="px-6 py-4 text-right text-sm font-medium space-x-2">
                ${d.status === 'ACTIVE' ? `
                    <button onclick="editDebt(${d.id})" class="text-indigo-600 hover:text-indigo-900 dark:text-indigo-400">Edit</button>
                    <button onclick="settleDebt(${d.id})" class="text-green-600 hover:text-green-900 dark:text-green-400">Settle</button>
                ` : ''}
                <button onclick="deleteDebt(${d.id})" class="text-red-600 hover:text-red-900 dark:text-red-400">Delete</button>
            </td>
        </tr>`;
    }).join('');
}

function showDebtModal(isEdit = false) {
    document.getElementById('debtModalTitle').textContent = isEdit ? 'Edit Debt' : 'Add Debt';
    document.getElementById('debtModal').classList.remove('hidden');
    updateNameLabel();
}

function hideDebtModal() {
    document.getElementById('debtModal').classList.add('hidden');
    document.getElementById('debtForm').reset();
    editingDebtId = null;
}

function updateNameLabel() {
    const direction = document.querySelector('input[name="direction"]:checked')?.value;
    const label = document.getElementById('nameLabel');
    if (label) label.textContent = direction === 'THEY_OWE' ? 'Owed By' : 'Owed To';
}

function editDebt(id) {
    const debt = allDebts.find(d => d.id === id);
    if (!debt) return;
    editingDebtId = id;

    document.getElementById('debtId').value = debt.id;
    document.querySelector(`input[name="direction"][value="${debt.direction}"]`).checked = true;
    document.getElementById('debtName').value = debt.name;
    document.getElementById('debtAmount').value = debt.amount;
    document.getElementById('debtCurrency').value = debt.currency;
    document.getElementById('debtDueDate').value = debt.dueDate || '';
    document.getElementById('debtDescription').value = debt.description || '';
    updateNameLabel();
    showDebtModal(true);
}

async function settleDebt(id) {
    if (!confirm('Mark this debt as settled?')) return;
    try {
        await apiPatch(`/debts/${id}/settle`, {});
        showSuccess('Debt settled.');
        loadDebts();
    } catch (e) {
        showError('Failed to settle debt.');
    }
}

async function deleteDebt(id) {
    if (!confirm('Delete this debt entry?')) return;
    try {
        await apiDelete(`/debts/${id}`);
        showSuccess('Debt deleted.');
        loadDebts();
    } catch (e) {
        showError('Failed to delete debt.');
    }
}

async function saveDebt(event) {
    event.preventDefault();
    const submitBtn = event.target.querySelector('[type="submit"]');
    setLoading(submitBtn, true);

    const debt = {
        direction: document.querySelector('input[name="direction"]:checked').value,
        name: document.getElementById('debtName').value.trim(),
        amount: parseFloat(document.getElementById('debtAmount').value),
        currency: document.getElementById('debtCurrency').value,
        dueDate: document.getElementById('debtDueDate').value || null,
        description: document.getElementById('debtDescription').value.trim() || null,
        status: 'ACTIVE'
    };

    try {
        if (editingDebtId) {
            await apiPut(`/debts/${editingDebtId}`, debt);
            showSuccess('Debt updated.');
        } else {
            await apiPost('/debts', debt);
            showSuccess('Debt added.');
        }
        hideDebtModal();
        loadDebts();
    } catch (e) {
        showError('Failed to save debt.');
    } finally {
        setLoading(submitBtn, false);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    loadDebts();

    document.getElementById('addDebtBtn').addEventListener('click', () => showDebtModal(false));
    document.getElementById('closeDebtModal').addEventListener('click', hideDebtModal);
    document.getElementById('cancelDebtBtn').addEventListener('click', hideDebtModal);
    document.getElementById('debtForm').addEventListener('submit', saveDebt);

    document.querySelectorAll('input[name="direction"]').forEach(r => {
        r.addEventListener('change', updateNameLabel);
    });

    document.getElementById('debtModal').addEventListener('click', e => {
        if (e.target.id === 'debtModal') hideDebtModal();
    });
});
