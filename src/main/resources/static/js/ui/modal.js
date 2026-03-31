/**
 * ui/modal.js — Lightweight modal helpers used across feature pages.
 */

/** Open a modal by id. */
export function openModal(id) {
    const modal = document.getElementById(id);
    if (!modal) return;
    modal.classList.remove('hidden');
    document.body.style.overflow = 'hidden';
}

/** Close a modal by id. */
export function closeModal(id) {
    const modal = document.getElementById(id);
    if (!modal) return;
    modal.classList.add('hidden');
    document.body.style.overflow = '';
}

/** Close modal when clicking the backdrop (outside the inner box). */
export function bindModalBackdrop(modalId, innerSelector = '.modal-box') {
    const modal = document.getElementById(modalId);
    if (!modal) return;
    modal.addEventListener('click', e => {
        if (!e.target.closest(innerSelector)) closeModal(modalId);
    });
}
