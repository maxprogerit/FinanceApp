// Investments JavaScript — Smart Finance Dashboard
// NOTE: formatCurrency, formatDate, apiGet, apiPost, apiPut, apiDelete,
//       showSuccess, showError are provided by app.js

let portfolioChart = null;

document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('investmentDate').value = new Date().toISOString().split('T')[0];
    loadInvestments();

    document.getElementById('investmentForm').addEventListener('submit', async (e) => {
        e.preventDefault();
        const submitBtn = e.target.querySelector('[type="submit"]');
        setLoading(submitBtn, true);

        const id = document.getElementById('investmentId').value;
        const investment = {
            symbol: document.getElementById('investmentSymbol').value.toUpperCase(),
            assetName: document.getElementById('investmentName').value,
            assetType: document.getElementById('investmentType').value,
            quantity: parseFloat(document.getElementById('investmentShares').value),
            purchasePrice: parseFloat(document.getElementById('investmentPrice').value),
            purchaseDate: document.getElementById('investmentDate').value + 'T00:00:00',
            currency: 'USD'
        };

        try {
            if (id) {
                await apiPut(`/investments/${id}`, investment);
                showSuccess('Investment updated.');
            } else {
                await apiPost('/investments', investment);
                showSuccess('Investment added.');
            }
            closeInvestmentModal();
            loadInvestments();
        } catch (err) {
            showError('Failed to save investment.');
        } finally {
            setLoading(submitBtn, false);
        }
    });
});

async function loadInvestments() {
    try {
        const [investments, summary] = await Promise.all([
            apiGet('/investments'),
            apiGet('/investments/portfolio/summary')
        ]);
        renderSummary(summary);
        renderTable(investments);
        renderChart(investments);
    } catch (err) {
        showError('Failed to load investments.');
    }
}

function renderSummary(s) {
    document.getElementById('portfolioTotal').textContent = formatCurrency(s.totalPortfolioValue);
    const gl = parseFloat(s.totalProfitLoss || 0);
    const glEl = document.getElementById('portfolioGainLoss');
    glEl.textContent = (gl >= 0 ? '+' : '') + formatCurrency(gl);
    glEl.closest('.card').classList.toggle('from-red-400', gl < 0);
    glEl.closest('.card').classList.toggle('to-red-600', gl < 0);
    document.getElementById('portfolioInvested').textContent = formatCurrency(s.totalInvestmentValue);
    const pct = parseFloat(s.profitLossPercentage || 0);
    document.getElementById('portfolioReturn').textContent = (pct >= 0 ? '+' : '') + pct.toFixed(2) + '%';
}

function renderTable(investments) {
    const tbody = document.getElementById('investmentTableBody');
    if (!investments.length) {
        tbody.innerHTML = `<tr><td colspan="9" class="text-center py-8 text-gray-500 dark:text-gray-400">No investments yet. Add your first holding.</td></tr>`;
        return;
    }

    tbody.innerHTML = investments.map(inv => {
        const costBasis = parseFloat(inv.purchasePrice || 0);
        const currentPrice = parseFloat(inv.currentPrice || costBasis);
        const shares = parseFloat(inv.quantity || 0);
        const value = currentPrice * shares;
        const invested = costBasis * shares;
        const gl = value - invested;
        const glClass = gl >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400';

        return `<tr class="border-b border-gray-100 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800">
            <td class="py-3 px-2 font-semibold text-gray-900 dark:text-white">${inv.symbol}</td>
            <td class="py-3 px-2 text-gray-700 dark:text-gray-300">${inv.assetName}</td>
            <td class="py-3 px-2"><span class="px-2 py-0.5 rounded text-xs bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200">${inv.assetType || inv.type}</span></td>
            <td class="py-3 px-2 text-right">${shares.toFixed(4)}</td>
            <td class="py-3 px-2 text-right">${formatCurrency(costBasis)}</td>
            <td class="py-3 px-2 text-right">${formatCurrency(currentPrice)}</td>
            <td class="py-3 px-2 text-right font-medium">${formatCurrency(value)}</td>
            <td class="py-3 px-2 text-right font-medium ${glClass}">${gl >= 0 ? '+' : ''}${formatCurrency(gl)}</td>
            <td class="py-3 px-2 text-center">
                <button onclick="editInvestment(${inv.id})" class="text-xs text-indigo-600 hover:text-indigo-800 px-1">Edit</button>
                <button onclick="deleteInvestment(${inv.id})" class="text-xs text-red-600 hover:text-red-800 px-1">Delete</button>
            </td>
        </tr>`;
    }).join('');
}

function renderChart(investments) {
    if (!investments.length) return;
    const ctx = document.getElementById('portfolioChart').getContext('2d');

    const grouped = {};
    investments.forEach(inv => {
        const type = inv.assetType || inv.type || 'OTHER';
        const value = parseFloat(inv.currentPrice || inv.purchasePrice || 0) * parseFloat(inv.quantity || 0);
        grouped[type] = (grouped[type] || 0) + value;
    });

    if (portfolioChart) portfolioChart.destroy();
    portfolioChart = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: Object.keys(grouped),
            datasets: [{
                data: Object.values(grouped),
                backgroundColor: ['#6366f1', '#22c55e', '#f59e0b', '#ef4444', '#8b5cf6'],
                borderWidth: 2
            }]
        },
        options: { responsive: true, plugins: { legend: { position: 'bottom' } } }
    });
}

function openInvestmentModal(inv = null) {
    document.getElementById('investmentModalTitle').textContent = inv ? 'Edit Investment' : 'Add Investment';
    document.getElementById('investmentId').value = inv?.id || '';
    document.getElementById('investmentSymbol').value = inv?.symbol || '';
    document.getElementById('investmentName').value = inv?.assetName || '';
    document.getElementById('investmentType').value = inv?.assetType || 'STOCK';
    document.getElementById('investmentShares').value = inv?.quantity || '';
    document.getElementById('investmentPrice').value = inv?.purchasePrice || '';
    document.getElementById('investmentDate').value = inv?.purchaseDate?.split('T')[0] || new Date().toISOString().split('T')[0];
    document.getElementById('investmentModal').classList.remove('hidden');
}

function closeInvestmentModal() {
    document.getElementById('investmentModal').classList.add('hidden');
    document.getElementById('investmentForm').reset();
}

async function editInvestment(id) {
    try {
        const inv = await apiGet(`/investments/${id}`);
        openInvestmentModal(inv);
    } catch (err) {
        showError('Failed to load investment.');
    }
}

async function deleteInvestment(id) {
    if (!confirm('Delete this investment?')) return;
    try {
        await apiDelete(`/investments/${id}`);
        showSuccess('Investment deleted.');
        loadInvestments();
    } catch (err) {
        showError('Failed to delete investment.');
    }
}
