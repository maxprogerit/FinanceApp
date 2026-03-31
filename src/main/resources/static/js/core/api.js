/**
 * core/api.js — Centralised HTTP helpers for Smart Finance Dashboard.
 * All API calls go through apiFetch so error handling is consistent.
 */

export const API = '/api';

/**
 * Core fetch wrapper. Throws on non-2xx responses with a user-friendly message.
 * Handles empty 204 responses (returns null).
 */
export async function apiFetch(url, options = {}) {
    const defaults = { headers: { 'Content-Type': 'application/json' } };
    let response;
    try {
        response = await fetch(url, { ...defaults, ...options });
    } catch (networkErr) {
        throw new Error('Network error — check your connection and try again.');
    }

    if (!response.ok) {
        let message = `Server error ${response.status}`;
        try {
            const body = await response.json();
            message = body.message || body.error || message;
        } catch (_) { /* plain-text or empty body */ }
        const err = new Error(message);
        err.status = response.status;
        throw err;
    }

    if (response.status === 204 || response.headers.get('content-length') === '0') return null;
    return response.json();
}

export async function apiGet(path)              { return apiFetch(`${API}${path}`); }
export async function apiPost(path, body)       { return apiFetch(`${API}${path}`, { method: 'POST',   body: JSON.stringify(body) }); }
export async function apiPut(path, body)        { return apiFetch(`${API}${path}`, { method: 'PUT',    body: JSON.stringify(body) }); }
export async function apiPatch(path, body = {}) { return apiFetch(`${API}${path}`, { method: 'PATCH',  body: JSON.stringify(body) }); }
export async function apiDelete(path)           { return apiFetch(`${API}${path}`, { method: 'DELETE' }); }

/** Multipart form-data POST (for CSV upload, etc.). No Content-Type header — browser sets it. */
export async function apiUpload(path, formData) {
    return apiFetch(`${API}${path}`, { method: 'POST', headers: {}, body: formData });
}
