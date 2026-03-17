// Reports JavaScript — Smart Finance Dashboard
// NOTE: formatCurrency, apiGet, showError, API are provided by app.js

document.addEventListener('DOMContentLoaded', () => {
    const now = new Date();
    const firstDay = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().split('T')[0];
    const today = now.toISOString().split('T')[0];
    document.getElementById('exportFrom').value = firstDay;
    document.getElementById('exportTo').value = today;
    document.getElementById('reportMonth').value = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;

    loadMonthlySummary();
    loadForecast();
});

async function loadMonthlySummary() {
    try {
        const data = await apiGet('/analytics/trends?months=12');
        renderSummaryTable(data.monthlyData || []);
    } catch (err) {
        document.getElementById('monthlySummaryBody').innerHTML =
            `<tr><td colspan="5" class="text-center py-4 text-gray-400">Unable to load summary.</td></tr>`;
    }
}

function renderSummaryTable(rows) {
    const tbody = document.getElementById('monthlySummaryBody');
    if (!rows.length) {
        tbody.innerHTML = `<tr><td colspan="5" class="text-center py-8 text-gray-500 dark:text-gray-400">No data available yet.</td></tr>`;
        return;
    }
    tbody.innerHTML = rows.map(row => {
        const net = parseFloat(row.income || 0) - parseFloat(row.expenses || 0);
        const netClass = net >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400';
        return `<tr class="border-b border-gray-100 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800">
            <td class="py-3 px-2 font-medium text-gray-900 dark:text-white">${row.month}</td>
            <td class="py-3 px-2 text-right text-green-600 dark:text-green-400">${formatCurrency(row.income)}</td>
            <td class="py-3 px-2 text-right text-red-600 dark:text-red-400">${formatCurrency(row.expenses)}</td>
            <td class="py-3 px-2 text-right font-semibold ${netClass}">${net >= 0 ? '+' : ''}${formatCurrency(net)}</td>
            <td class="py-3 px-2 text-right text-gray-500 dark:text-gray-400">${row.transactionCount || '—'}</td>
        </tr>`;
    }).join('');
}

async function loadForecast() {
    try {
        const data = await apiGet('/forecast/spending');
        renderForecast(data);
    } catch (err) {
        document.getElementById('forecastContainer').innerHTML =
            `<p class="text-gray-500 dark:text-gray-400">Unable to load forecast.</p>`;
    }
}

function renderForecast(data) {
    const container = document.getElementById('forecastContainer');
    if (!data || Object.keys(data).length === 0) {
        container.innerHTML = `<p class="text-gray-500 dark:text-gray-400">Not enough data to generate a forecast yet. Add more transactions over multiple months.</p>`;
        return;
    }
    const riskLow = (data.riskLevel || '').toLowerCase() !== 'high';
    container.innerHTML = `
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div class="bg-blue-50 dark:bg-blue-900/20 rounded-lg p-4">
                <p class="text-sm text-blue-600 dark:text-blue-400 font-medium">Predicted Next Month Spending</p>
                <p class="text-2xl font-bold text-blue-700 dark:text-blue-300 mt-1">${formatCurrency(data.predictedSpending || 0)}</p>
            </div>
            <div class="bg-green-50 dark:bg-green-900/20 rounded-lg p-4">
                <p class="text-sm text-green-600 dark:text-green-400 font-medium">Average Monthly Spending</p>
                <p class="text-2xl font-bold text-green-700 dark:text-green-300 mt-1">${formatCurrency(data.averageSpending || 0)}</p>
            </div>
            <div class="${riskLow ? 'bg-yellow-50 dark:bg-yellow-900/20' : 'bg-red-50 dark:bg-red-900/20'} rounded-lg p-4">
                <p class="text-sm font-medium ${riskLow ? 'text-yellow-600 dark:text-yellow-400' : 'text-red-600 dark:text-red-400'}">Budget Risk Level</p>
                <p class="text-2xl font-bold mt-1 ${riskLow ? 'text-yellow-700 dark:text-yellow-300' : 'text-red-700 dark:text-red-300'}">${data.riskLevel || 'LOW'}</p>
            </div>
        </div>
        ${data.message ? `<p class="mt-3 text-sm text-gray-600 dark:text-gray-400">${data.message}</p>` : ''}
    `;
}

async function exportCSV() {
    const from = document.getElementById('exportFrom').value;
    const to = document.getElementById('exportTo').value;
    if (!from || !to) {
        showError('Please select a date range.');
        return;
    }
    window.location.href = `${API}/export/transactions/csv?startDate=${encodeURIComponent(from + 'T00:00:00')}&endDate=${encodeURIComponent(to + 'T23:59:59')}`;
}

async function exportPDF() {
    const monthVal = document.getElementById('reportMonth').value;
    if (!monthVal) {
        showError('Please select a month.');
        return;
    }
    const [year, month] = monthVal.split('-');
    window.location.href = `${API}/export/report/pdf?year=${year}&month=${month}`;
}
