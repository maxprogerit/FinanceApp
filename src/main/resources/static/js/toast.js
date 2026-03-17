/**
 * Toast Notification System — Smart Finance Dashboard
 * Usage: showToast('message')            → success
 *        showToast('message', 'error')   → error
 *        showToast('message', 'warning') → warning
 *        showToast('message', 'info')    → info
 */

(function () {
    // Create container once
    function getContainer() {
        let c = document.getElementById('toast-container');
        if (!c) {
            c = document.createElement('div');
            c.id = 'toast-container';
            document.body.appendChild(c);
        }
        return c;
    }

    const ICONS = {
        success: '✅',
        error:   '❌',
        warning: '⚠️',
        info:    'ℹ️'
    };

    window.showToast = function (message, type = 'success', duration = 4000) {
        const container = getContainer();

        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        toast.innerHTML = `
            <span class="toast-icon">${ICONS[type] || ICONS.info}</span>
            <span class="toast-msg">${message}</span>
            <button class="toast-close" aria-label="Dismiss">✕</button>
        `;

        container.appendChild(toast);

        function dismiss() {
            toast.classList.add('toast-out');
            toast.addEventListener('animationend', () => toast.remove(), { once: true });
        }

        toast.querySelector('.toast-close').addEventListener('click', dismiss);

        if (duration > 0) {
            setTimeout(dismiss, duration);
        }
    };
}());
