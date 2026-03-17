// Settings JavaScript — Smart Finance Dashboard
// NOTE: showSuccess is provided by app.js

document.addEventListener('DOMContentLoaded', () => {
    loadSettings();
    updateDarkModeToggle();
});

function loadSettings() {
    const currency = localStorage.getItem('baseCurrency') || 'USD';
    const select = document.getElementById('baseCurrency');
    if (select) select.value = currency;
}

function updateDarkModeToggle() {
    const isDark = document.documentElement.classList.contains('dark');
    const btn = document.getElementById('darkModeToggle');
    const slider = document.getElementById('darkModeSlider');
    if (!btn || !slider) return;
    if (isDark) {
        btn.classList.add('bg-indigo-600');
        btn.classList.remove('bg-gray-300');
        slider.classList.add('translate-x-7');
        slider.classList.remove('translate-x-1');
    } else {
        btn.classList.remove('bg-indigo-600');
        btn.classList.add('bg-gray-300');
        slider.classList.remove('translate-x-7');
        slider.classList.add('translate-x-1');
    }
}

function toggleDarkMode() {
    const html = document.documentElement;
    html.classList.toggle('dark');
    const isDark = html.classList.contains('dark');
    localStorage.setItem('theme', isDark ? 'dark' : 'light');
    updateDarkModeToggle();
}

function saveSettings() {
    const currency = document.getElementById('baseCurrency').value;
    localStorage.setItem('baseCurrency', currency);
    showSuccess('Settings saved.');
}
