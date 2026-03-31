/**
 * core/auth.js — Plan status and upgrade flow helpers.
 */
import { apiGet, apiPost }           from './api.js';
import { getPlanState, setPlanState, isProUser } from './state.js';
import { showError }                 from '../ui/toast.js';

export { isProUser };

export async function fetchPlanState() {
    if (getPlanState()) return getPlanState();
    try {
        const cached = sessionStorage.getItem('_planState');
        if (cached) { setPlanState(JSON.parse(cached)); return getPlanState(); }
        const data = await apiGet('/payments/status');
        setPlanState(data || { plan: 'FREE', status: '', active: false });
        try { sessionStorage.setItem('_planState', JSON.stringify(getPlanState())); } catch (_) {}
    } catch (_) {
        setPlanState({ plan: 'FREE', status: '', active: false });
    }
    return getPlanState();
}

export function openUpgradeModal() {
    const modal = document.getElementById('upgrade-modal');
    if (modal) { modal.classList.remove('hidden'); document.body.style.overflow = 'hidden'; }
}

export function closeUpgradeModal() {
    const modal = document.getElementById('upgrade-modal');
    if (modal) { modal.classList.add('hidden'); document.body.style.overflow = ''; }
}

export async function handleUpgradeClick(btn) {
    if (btn) { btn.disabled = true; btn.classList.add('btn-loading'); }
    try {
        const res = await apiPost('/payments/checkout', {});
        if (res?.url) {
            sessionStorage.removeItem('_planState');
            window.location.href = res.url;
        } else {
            showError('Could not start checkout. Please try again.');
        }
    } catch (_) {
        showError('Could not start checkout. Please check Stripe configuration.');
    } finally {
        if (btn) { btn.disabled = false; btn.classList.remove('btn-loading'); }
    }
}

/**
 * Applies a lock overlay to `el` for FREE users.
 * No-ops for PRO users.
 */
export function featureGate(el, message = 'Upgrade to Pro to unlock this feature') {
    if (!el || isProUser()) return;
    el.classList.add('feature-gated');
    el.style.position = 'relative';
    const overlay = document.createElement('div');
    overlay.className = 'feature-gate-overlay';
    overlay.innerHTML = `
        <div class="feature-gate-content">
            <span class="feature-gate-lock">🔒</span>
            <p class="feature-gate-message">${message}</p>
            <button class="feature-gate-btn" onclick="openUpgradeModal()">Upgrade to Pro</button>
        </div>`;
    el.appendChild(overlay);
}
