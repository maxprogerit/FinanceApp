// Transactions Page JavaScript
// NOTE: formatCurrency, formatDateTime, apiGet, apiPost, apiPut, apiDelete,
//       setLoading, showSuccess, showError, API are provided by app.js

let currentTransactionId = null;
let allTransactions = [];
let categories = [];
let storageTypes = [];
let incomeSources = [];

function formatDateForInput(dateString) {
    return new Date(dateString).toISOString().slice(0, 16);
}

async function loadCategories() {
    try {
        categories = await apiGet('/categories/active');
        populateCategorySelects();
    } catch (error) {
        console.error('Error loading categories:', error);
    }
}

function populateCategorySelects() {
    const categorySelect = document.getElementById('category');
    const filterCategorySelect = document.getElementById('filterCategory');

    if (categorySelect) {
        categorySelect.innerHTML = '<option value="">Select Category</option>';
        categories.forEach(cat => {
            categorySelect.innerHTML += `<option value="${cat.name}">${cat.icon} ${cat.name}</option>`;
        });
    }

    if (filterCategorySelect) {
        filterCategorySelect.innerHTML = '<option value="">All Categories</option>';
        categories.forEach(cat => {
            filterCategorySelect.innerHTML += `<option value="${cat.name}">${cat.icon} ${cat.name}</option>`;
        });
    }
}

async function loadStorageTypes() {
    try {
        storageTypes = await apiGet('/storage-types');
        populateStorageTypeSelect();
    } catch (error) {
        console.error('Error loading storage types:', error);
    }
}

function populateStorageTypeSelect(selectedValue = '') {
    const select = document.getElementById('storageType');
    if (!select) return;
    const prev = selectedValue || select.value;
    select.innerHTML = '<option value="">— None / Not specified —</option>';
    storageTypes.forEach(st => {
        const opt = document.createElement('option');
        opt.value = st.name;
        opt.textContent = `${st.icon || ''} ${st.name}`.trim();
        if (st.name === prev) opt.selected = true;
        select.appendChild(opt);
    });
}

async function loadIncomeSources() {
    try {
        incomeSources = await apiGet('/income-sources');
        populateIncomeSourceSelect();
    } catch (error) {
        console.error('Error loading income sources:', error);
    }
}

function populateIncomeSourceSelect(selectedValue = '') {
    const select = document.getElementById('incomeSource');
    if (!select) return;
    const prev = selectedValue || select.value;
    select.innerHTML = '<option value="">— None / Not specified —</option>';
    incomeSources.forEach(src => {
        const opt = document.createElement('option');
        opt.value = src.name;
        opt.textContent = `${src.icon || ''} ${src.name}`.trim();
        if (src.name === prev) opt.selected = true;
        select.appendChild(opt);
    });
}

function toggleIncomeSourceField() {
    const type = document.getElementById('type').value;
    const field = document.getElementById('incomeSourceField');
    if (field) field.classList.toggle('hidden', type !== 'INCOME');
}

async function loadTransactions() {
    const tableBody = document.getElementById('transactionsTable');
    tableBody.innerHTML = `<tr><td colspan="7" class="px-6 py-8 text-center text-gray-400">Loading...</td></tr>`;
    try {
        allTransactions = await apiGet('/transactions');
        allTransactions.sort((a, b) => new Date(b.transactionDate) - new Date(a.transactionDate));
        displayTransactions(allTransactions);
    } catch (error) {
        showError('Failed to load transactions.');
    }
}

function displayTransactions(transactions) {
    const tableBody = document.getElementById('transactionsTable');
    tableBody.innerHTML = '';

    if (transactions.length === 0) {
        tableBody.innerHTML = `
            <tr>
                <td colspan="7" class="px-6 py-8 text-center text-gray-500 dark:text-gray-400">
                    No transactions found. Click "Add Transaction" to get started!
                </td>
            </tr>
        `;
        return;
    }

    transactions.forEach(transaction => {
        const row = document.createElement('tr');
        row.className = 'hover:bg-gray-50 dark:hover:bg-gray-800';

        const typeClass = transaction.type === 'INCOME' ? 'text-green-600' : 'text-red-600';
        const typeSign = transaction.type === 'INCOME' ? '+' : '-';
        const storageLabel = transaction.storageType
            ? `<span class="text-xs bg-blue-100 dark:bg-blue-900 text-blue-700 dark:text-blue-300 px-1.5 py-0.5 rounded">${transaction.storageType}</span>`
            : '<span class="text-gray-400 text-xs">—</span>';

        row.innerHTML = `
            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-900 dark:text-gray-300">
                ${formatDateTime(transaction.transactionDate)}
            </td>
            <td class="px-6 py-4 whitespace-nowrap">
                <span class="badge ${transaction.type === 'INCOME' ? 'badge-success' : 'badge-danger'}">
                    ${transaction.type}
                </span>
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-900 dark:text-gray-300">
                ${transaction.category}
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm">
                ${storageLabel}
            </td>
            <td class="px-6 py-4 text-sm text-gray-900 dark:text-gray-300">
                ${transaction.description || '-'}
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm font-medium ${typeClass}">
                ${typeSign}${formatCurrency(transaction.amount, transaction.currency)}
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                <button onclick="editTransaction(${transaction.id})" class="text-indigo-600 hover:text-indigo-900 dark:text-indigo-400 mr-3">Edit</button>
                <button onclick="deleteTransaction(${transaction.id})" class="text-red-600 hover:text-red-900 dark:text-red-400">Delete</button>
            </td>
        `;

        tableBody.appendChild(row);
    });
}

function showModal(isEdit = false) {
    document.getElementById('modalTitle').textContent = isEdit ? 'Edit Transaction' : 'Add Transaction';
    document.getElementById('transactionModal').classList.remove('hidden');

    if (!isEdit) {
        document.getElementById('transactionForm').reset();
        document.getElementById('transactionId').value = '';
        document.getElementById('transactionDate').value = new Date().toISOString().slice(0, 16);
        populateStorageTypeSelect('');
        populateIncomeSourceSelect('');
        toggleIncomeSourceField();
    }
}

function hideModal() {
    document.getElementById('transactionModal').classList.add('hidden');
    currentTransactionId = null;
}

async function editTransaction(id) {
    try {
        const transaction = await apiGet(`/transactions/${id}`);
        currentTransactionId = id;

        document.getElementById('transactionId').value = transaction.id;
        document.getElementById('type').value = transaction.type;
        document.getElementById('amount').value = transaction.amount;
        document.getElementById('currency').value = transaction.currency;
        document.getElementById('category').value = transaction.category;
        document.getElementById('description').value = transaction.description || '';
        document.getElementById('transactionDate').value = formatDateForInput(transaction.transactionDate);
        document.getElementById('isRecurring').checked = transaction.isRecurring;

        populateStorageTypeSelect(transaction.storageType || '');
        populateIncomeSourceSelect(transaction.incomeSource || '');
        toggleIncomeSourceField();

        if (transaction.isRecurring) {
            document.getElementById('recurringOptions').classList.remove('hidden');
            document.getElementById('recurringFrequency').value = transaction.recurringFrequency;
        }

        showModal(true);
    } catch (error) {
        showError('Failed to load transaction.');
    }
}

async function deleteTransaction(id) {
    if (!confirm('Are you sure you want to delete this transaction?')) return;
    try {
        await apiDelete(`/transactions/${id}`);
        showSuccess('Transaction deleted.');
        loadTransactions();
    } catch (error) {
        showError('Failed to delete transaction.');
    }
}

async function saveTransaction(event) {
    event.preventDefault();
    const submitBtn = event.target.querySelector('[type="submit"]');
    setLoading(submitBtn, true);

    const typeVal = document.getElementById('type').value;
    const transaction = {
        type: typeVal,
        amount: parseFloat(document.getElementById('amount').value),
        currency: document.getElementById('currency').value,
        category: document.getElementById('category').value,
        description: document.getElementById('description').value,
        transactionDate: new Date(document.getElementById('transactionDate').value).toISOString(),
        isRecurring: document.getElementById('isRecurring').checked,
        recurringFrequency: document.getElementById('isRecurring').checked
            ? document.getElementById('recurringFrequency').value
            : null,
        storageType: document.getElementById('storageType').value || null,
        incomeSource: (typeVal === 'INCOME')
            ? (document.getElementById('incomeSource').value || null)
            : null
    };

    try {
        const id = document.getElementById('transactionId').value;
        if (id) {
            await apiPut(`/transactions/${id}`, transaction);
            showSuccess('Transaction updated.');
        } else {
            await apiPost('/transactions', transaction);
            showSuccess('Transaction added.');
        }
        hideModal();
        loadTransactions();
    } catch (error) {
        showError('Failed to save transaction.');
    } finally {
        setLoading(submitBtn, false);
    }
}

function applyFilters() {
    let filtered = [...allTransactions];
    const type = document.getElementById('filterType').value;
    const category = document.getElementById('filterCategory').value;
    const startDate = document.getElementById('filterStartDate').value;
    const endDate = document.getElementById('filterEndDate').value;

    if (type) filtered = filtered.filter(t => t.type === type);
    if (category) filtered = filtered.filter(t => t.category === category);
    if (startDate) filtered = filtered.filter(t => new Date(t.transactionDate) >= new Date(startDate));
    if (endDate) {
        const endDateTime = new Date(endDate);
        endDateTime.setHours(23, 59, 59);
        filtered = filtered.filter(t => new Date(t.transactionDate) <= endDateTime);
    }
    displayTransactions(filtered);
}

function clearFilters() {
    document.getElementById('filterType').value = '';
    document.getElementById('filterCategory').value = '';
    document.getElementById('filterStartDate').value = '';
    document.getElementById('filterEndDate').value = '';
    displayTransactions(allTransactions);
}

// Raw fetch — needs blob, not JSON
async function exportToCSV() {
    const startDate = document.getElementById('filterStartDate').value;
    const endDate = document.getElementById('filterEndDate').value;

    if (!startDate || !endDate) {
        showError('Please select start and end dates for export.');
        return;
    }

    try {
        const response = await fetch(
            `${API}/export/transactions/csv?startDate=${new Date(startDate).toISOString()}&endDate=${new Date(endDate).toISOString()}`
        );
        if (!response.ok) throw new Error('Export failed');
        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'transactions.csv';
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);
        showSuccess('CSV exported successfully.');
    } catch (error) {
        showError('Failed to export CSV.');
    }
}

document.addEventListener('DOMContentLoaded', () => {
    loadCategories();
    loadStorageTypes();
    loadIncomeSources();
    loadTransactions();

    document.getElementById('addTransactionBtn').addEventListener('click', () => showModal(false));
    document.getElementById('closeModal').addEventListener('click', hideModal);
    document.getElementById('cancelBtn').addEventListener('click', hideModal);
    document.getElementById('transactionForm').addEventListener('submit', saveTransaction);

    document.getElementById('type').addEventListener('change', toggleIncomeSourceField);

    document.getElementById('isRecurring').addEventListener('change', (e) => {
        document.getElementById('recurringOptions').classList.toggle('hidden', !e.target.checked);
    });

    document.getElementById('applyFilters').addEventListener('click', applyFilters);
    document.getElementById('clearFilters').addEventListener('click', clearFilters);
    document.getElementById('exportCSV').addEventListener('click', exportToCSV);

    document.getElementById('transactionModal').addEventListener('click', (e) => {
        if (e.target.id === 'transactionModal') hideModal();
    });
});
