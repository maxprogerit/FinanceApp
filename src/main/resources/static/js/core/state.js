/**
 * core/state.js — Global application state with reactive currency changes.
 * Provides getters/setters so consumers always read the latest value.
 */

// ── Currency ──────────────────────────────────────────────────────────────────

export const STATIC_RATES = {
    USD: 1.0,    EUR: 0.9234, GBP: 0.7891, JPY: 149.52,
    CAD: 1.3612, AUD: 1.5243, CHF: 0.8965, CNY: 7.2410,
    INR: 83.12,  BRL: 4.9720, RSD: 107.80,
};

export const SUPPORTED_CURRENCIES = [
    'USD', 'EUR', 'GBP', 'JPY', 'CAD', 'AUD', 'CHF', 'CNY', 'INR', 'BRL', 'RSD',
];

let _currentCurrency = localStorage.getItem('currentCurrency') || 'USD';
let _exchangeRates   = {};

export function getCurrentCurrency() { return _currentCurrency; }
export function getExchangeRates()   { return _exchangeRates; }

export function setCurrentCurrency(code) {
    _currentCurrency = code;
    localStorage.setItem('currentCurrency', code);
    window.dispatchEvent(new CustomEvent('currencyChange', { detail: { currency: code } }));
}

export function setExchangeRates(rates) {
    _exchangeRates = rates;
    window.dispatchEvent(new CustomEvent('ratesLoaded', { detail: rates }));
}

export function convertAmount(amount, from = 'USD', to = _currentCurrency) {
    if (!from || !to || from === to) return amount ?? 0;
    const rates  = { ...STATIC_RATES, ..._exchangeRates };
    const inUSD  = (amount ?? 0) / (rates[from] ?? 1);
    return inUSD * (rates[to] ?? 1);
}

export function formatCurrency(amount, storedCurrency = 'USD') {
    const display = _currentCurrency || 'USD';
    const source  = storedCurrency   || 'USD';
    const value   = source !== display ? convertAmount(amount ?? 0, source, display) : (amount ?? 0);
    try {
        return new Intl.NumberFormat('en-US', { style: 'currency', currency: display }).format(value);
    } catch (_) {
        return `${display} ${value.toFixed(2)}`;
    }
}

// ── Plan state ─────────────────────────────────────────────────────────────────

let _planState   = null; // { active, plan, status, username, trialEnd?, currentPeriodEnd? }
let _currentUser = null; // { username, email, plan, premiumActive, baseCurrency, theme, role }

export function getPlanState()   { return _planState; }
export function getCurrentUser() { return _currentUser; }
export function setPlanState(s)  { _planState = s; }
export function setCurrentUser(u){ _currentUser = u; }

export function isProUser() {
    if (!_planState) return false;
    return _planState.plan === 'PRO' &&
           (_planState.status === 'active' || _planState.status === 'trialing');
}
