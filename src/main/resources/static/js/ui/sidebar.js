/**
 * ui/sidebar.js — Injects the fixed left sidebar and top header on every
 * authenticated page. Called once from main.js on DOMContentLoaded.
 */
import { SUPPORTED_CURRENCIES, getCurrentCurrency, setCurrentCurrency } from '../core/state.js';

const NAV_ITEMS = [
    { href: '/dashboard',    label: 'Dashboard',    icon: 'M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6' },
    { href: '/transactions', label: 'Transactions', icon: 'M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4' },
    { href: '/investments',  label: 'Assets',       icon: 'M13 7h8m0 0v8m0-8l-8 8-4-4-6 6' },
    { href: '/analytics',   label: 'Analytics',    icon: 'M16 8v8m-4-5v5m-4-2v2m-2 4h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z' },
    { href: '/settings',    label: 'Settings',     icon: 'M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065zM15 12a3 3 0 11-6 0 3 3 0 016 0z' },
];

const PAGE_TITLES = {
    '/dashboard': 'Dashboard', '/transactions': 'Transactions',
    '/investments': 'Assets',  '/analytics': 'Analytics',
    '/settings': 'Settings',   '/profile': 'My Profile',
    '/admin': 'Admin Panel',
};

function svgIcon(d) {
    return `<svg fill="none" stroke="currentColor" viewBox="0 0 24 24" class="icon-sm">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="${d}"/>
    </svg>`;
}

export function injectSidebar() {
    if (document.getElementById('app-sidebar')) return; // guard: already injected
    const existingNav = document.querySelector('body > nav');
    if (!existingNav) return; // public page (login, register, etc.)
    existingNav.style.display = 'none';

    const path   = window.location.pathname;
    const isDark = document.documentElement.classList.contains('dark');

    const MOON = 'M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z';
    const SUN  = 'M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z';

    // ── Build sidebar ──────────────────────────────────────────────────────────
    const sidebar = document.createElement('aside');
    sidebar.id = 'app-sidebar';
    sidebar.innerHTML = `
        <div class="sidebar-logo">
            <span class="sidebar-logo-icon">💰</span>
            <span class="sidebar-logo-text">Smart Finance</span>
        </div>
        <nav class="sidebar-nav" id="sidebar-main-nav">
            ${NAV_ITEMS.map(item => {
                const active = path === item.href || (item.href !== '/' && path.startsWith(item.href));
                return `<a href="${item.href}" class="sidebar-nav-item${active ? ' active' : ''}">
                    <span class="sidebar-nav-icon">${svgIcon(item.icon)}</span>
                    <span class="sidebar-nav-label">${item.label}</span>
                </a>`;
            }).join('')}
        </nav>
        <div class="sidebar-footer">
            <select id="globalCurrencySelect" title="Display currency" class="sidebar-currency">
                ${SUPPORTED_CURRENCIES.map(c =>
                    `<option value="${c}"${c === getCurrentCurrency() ? ' selected' : ''}>${c}</option>`
                ).join('')}
            </select>
            <button id="sidebarThemeBtn" class="sidebar-icon-btn" title="Toggle theme">
                ${svgIcon(isDark ? SUN : MOON).replace('class="icon-sm"', 'id="sidebarThemePathSvg" class="icon-sm"')}
            </button>
            <a href="/profile" class="sidebar-icon-btn${path === '/profile' ? ' active' : ''}" title="My Profile">
                ${svgIcon('M5.121 17.804A13.937 13.937 0 0112 16c2.5 0 4.847.655 6.879 1.804M15 10a3 3 0 11-6 0 3 3 0 016 0zm6 2a9 9 0 11-18 0 9 9 0 0118 0z')}
            </a>
            <a href="/logout" class="sidebar-icon-btn sidebar-logout" title="Logout">
                ${svgIcon('M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1')}
            </a>
        </div>`;

    // ── Build header ───────────────────────────────────────────────────────────
    const header = document.createElement('header');
    header.id = 'app-header';
    header.innerHTML = `
        <div class="header-left">
            <button id="sidebarToggle" class="sidebar-toggle" title="Toggle sidebar">
                ${svgIcon('M4 6h16M4 12h16M4 18h7').replace('class="icon-sm"', 'class="icon"')}
            </button>
            <span class="header-title">${PAGE_TITLES[path] || 'Smart Finance'}</span>
        </div>
        <div class="header-right">
            <div class="relative">
                <button id="notificationBtn" class="header-icon-btn" title="Notifications">
                    ${svgIcon('M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9').replace('class="icon-sm"', 'class="icon"')}
                    <span id="notificationBadge" class="hidden notification-badge">0</span>
                </button>
            </div>
        </div>`;

    // ── Mobile backdrop ────────────────────────────────────────────────────────
    const backdrop = document.createElement('div');
    backdrop.id = 'sidebar-backdrop';
    backdrop.addEventListener('click', () => {
        sidebar.classList.remove('mobile-open');
        backdrop.style.display = 'none';
    });

    document.body.insertBefore(sidebar, document.body.firstChild);
    document.body.insertBefore(header, sidebar.nextSibling);
    document.body.appendChild(backdrop);
    document.body.classList.add('has-sidebar');

    const mainEl = document.querySelector('main');
    if (mainEl) mainEl.style.maxWidth = 'none';

    // ── Wire: currency selector ────────────────────────────────────────────────
    document.getElementById('globalCurrencySelect')?.addEventListener('change', e => {
        setCurrentCurrency(e.target.value);
        location.reload();
    });

    // ── Wire: theme toggle ─────────────────────────────────────────────────────
    document.getElementById('sidebarThemeBtn')?.addEventListener('click', () => {
        const nowDark = document.documentElement.classList.toggle('dark');
        localStorage.setItem('theme', nowDark ? 'dark' : 'light');
        // Swap icon path
        const pathEl = document.querySelector('#sidebarThemePathSvg path');
        if (pathEl) pathEl.setAttribute('d', nowDark ? SUN : MOON);
    });

    // ── Wire: sidebar collapse toggle ──────────────────────────────────────────
    document.getElementById('sidebarToggle')?.addEventListener('click', () => {
        if (window.innerWidth <= 768) {
            const opening = !sidebar.classList.contains('mobile-open');
            sidebar.classList.toggle('mobile-open');
            backdrop.style.display = opening ? 'block' : 'none';
        } else {
            sidebar.classList.toggle('collapsed');
            document.body.classList.toggle('sidebar-collapsed');
        }
    });
}

/**
 * Appends an Admin nav item to the sidebar.
 * Called after `GET /api/user/me` confirms role === 'ADMIN'.
 */
export function injectAdminNavItem() {
    const nav = document.getElementById('sidebar-main-nav');
    if (!nav || nav.querySelector('a[href="/admin"]')) return;
    const a = document.createElement('a');
    a.href = '/admin';
    a.className = 'sidebar-nav-item' + (window.location.pathname === '/admin' ? ' active' : '');
    a.innerHTML = `
        <span class="sidebar-nav-icon">
            ${svgIcon('M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z')}
        </span>
        <span class="sidebar-nav-label">Admin</span>`;
    nav.appendChild(a);
}
