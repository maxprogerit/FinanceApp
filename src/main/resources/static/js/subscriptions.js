// Subscriptions Page JavaScript
// NOTE: formatCurrency, formatDate, formatDateTime, apiGet are provided by app.js

async function loadSubscriptions() {
    try {
        const [all, upcoming] = await Promise.all([
            apiGet('/subscriptions'),
            apiGet('/subscriptions/upcoming?days=7')
        ]);

        renderSummary(all, upcoming);
        renderUpcoming(upcoming);
        renderTable(all);
    } catch (e) {
        showError('Failed to load subscriptions.');
    }
}

function renderSummary(all, upcoming) {
    // Monthly total (sum of MONTHLY subscriptions; others are approximated)
    let monthlyTotal = 0;
    all.forEach(sub => {
        const freq = sub.frequency || 'MONTHLY';
        const amt = parseFloat(sub.amount) || 0;
        if (freq === 'MONTHLY') monthlyTotal += amt;
        else if (freq === 'WEEKLY') monthlyTotal += amt * 4.33;
        else if (freq === 'BIWEEKLY') monthlyTotal += amt * 2.17;
        else if (freq === 'QUARTERLY') monthlyTotal += amt / 3;
        else if (freq === 'YEARLY') monthlyTotal += amt / 12;
    });

    const totalEl = document.getElementById('monthlyTotal');
    const countEl = document.getElementById('subCount');
    const upcomingEl = document.getElementById('upcomingCount');
    if (totalEl) totalEl.textContent = formatCurrency(monthlyTotal);
    if (countEl) countEl.textContent = all.length;
    if (upcomingEl) upcomingEl.textContent = upcoming.length;
}

function renderUpcoming(upcoming) {
    const list = document.getElementById('upcomingList');
    if (!list) return;

    if (upcoming.length === 0) {
        list.innerHTML = '<p class="text-sm text-gray-500 dark:text-gray-400">No subscriptions due in the next 7 days.</p>';
        return;
    }

    list.innerHTML = upcoming.map(sub => {
        const nextDate = sub.nextExpectedCharge ? formatDate(sub.nextExpectedCharge) : '—';
        const daysLeft = sub.nextExpectedCharge
            ? Math.ceil((new Date(sub.nextExpectedCharge) - Date.now()) / 86400000)
            : null;
        const urgency = daysLeft !== null && daysLeft <= 1 ? 'bg-red-50 dark:bg-red-900 border-red-200' : 'bg-orange-50 dark:bg-orange-900 border-orange-200';
        return `
        <div class="flex justify-between items-center p-3 rounded-lg border ${urgency} mb-2">
            <div>
                <p class="font-medium text-sm text-gray-900 dark:text-white">${sub.name}</p>
                <p class="text-xs text-gray-500 dark:text-gray-400">${sub.category} · ${sub.frequency}</p>
            </div>
            <div class="text-right">
                <p class="font-semibold text-sm text-orange-600 dark:text-orange-400">${formatCurrency(sub.amount, sub.currency)}</p>
                <p class="text-xs text-gray-500 dark:text-gray-400">${nextDate}${daysLeft !== null ? ` (${daysLeft}d)` : ''}</p>
            </div>
        </div>`;
    }).join('');
}

function renderTable(all) {
    const tbody = document.getElementById('subscriptionsTable');
    if (!tbody) return;

    if (all.length === 0) {
        tbody.innerHTML = `<tr><td colspan="6" class="px-6 py-8 text-center text-gray-500 dark:text-gray-400">
            No recurring patterns detected. Mark transactions as recurring or add transactions with the same description monthly.
        </td></tr>`;
        return;
    }

    tbody.innerHTML = all.map(sub => `
        <tr class="hover:bg-gray-50 dark:hover:bg-gray-800">
            <td class="px-6 py-4 text-sm font-medium text-gray-900 dark:text-white">${sub.name}</td>
            <td class="px-6 py-4 text-sm text-gray-600 dark:text-gray-300">${sub.category}</td>
            <td class="px-6 py-4 text-sm font-medium text-gray-900 dark:text-white">${formatCurrency(sub.amount, sub.currency)}</td>
            <td class="px-6 py-4 text-sm text-gray-600 dark:text-gray-300">${sub.frequency}</td>
            <td class="px-6 py-4 text-sm text-gray-600 dark:text-gray-300">${sub.lastCharge ? formatDate(sub.lastCharge) : '—'}</td>
            <td class="px-6 py-4 text-sm ${isOverdue(sub.nextExpectedCharge) ? 'text-red-500 font-medium' : 'text-gray-600 dark:text-gray-300'}">
                ${sub.nextExpectedCharge ? formatDate(sub.nextExpectedCharge) : '—'}
            </td>
        </tr>`).join('');
}

function isOverdue(dateStr) {
    if (!dateStr) return false;
    return new Date(dateStr) < new Date();
}

document.addEventListener('DOMContentLoaded', () => {
    loadSubscriptions();
});
