// Theme Management for Smart Finance Dashboard

// Check for saved theme preference or default to light mode
const theme = localStorage.getItem('theme') || 'light';

// Apply theme on page load
if (theme === 'dark') {
    document.documentElement.classList.add('dark');
} else {
    document.documentElement.classList.remove('dark');
}

// Theme toggle functionality
document.addEventListener('DOMContentLoaded', () => {
    const themeToggle = document.getElementById('themeToggle');
    const themeIcon = document.getElementById('themeIcon');
    
    if (themeToggle) {
        // Set initial icon
        updateThemeIcon();
        
        themeToggle.addEventListener('click', () => {
            const isDark = document.documentElement.classList.toggle('dark');
            localStorage.setItem('theme', isDark ? 'dark' : 'light');
            updateThemeIcon();
        });
    }
    
    function updateThemeIcon() {
        if (!themeIcon) return;
        
        const isDark = document.documentElement.classList.contains('dark');
        
        if (isDark) {
            // Sun icon for light mode
            themeIcon.innerHTML = `
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" 
                      d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z">
                </path>
            `;
        } else {
            // Moon icon for dark mode
            themeIcon.innerHTML = `
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" 
                      d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z">
                </path>
            `;
        }
    }
});

// Smooth transitions
document.documentElement.style.transition = 'background-color 0.3s, color 0.3s';
