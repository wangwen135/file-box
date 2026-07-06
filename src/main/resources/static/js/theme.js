/**
 * File Box - 主题切换脚本
 */

(function() {
    // 从 localStorage 获取保存的主题
    const savedTheme = localStorage.getItem('filebox-theme') || 'light';

    // 主题图标走本地精灵 /images/icons.svg (Lucide 线性图标, ISC 许可: https://lucide.dev)
    // Theme icons live in the local sprite /images/icons.svg (Lucide, ISC).
    // 自带 width/height,确保未定义 .theme-toggle svg 尺寸的页面(登录/后台)也能正常显示。
    const THEME_ICON = name => `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><use href="/images/icons.svg#ico-${name}"/></svg>`;

    // 应用主题
    function applyTheme(theme) {
        if (theme === 'dark') {
            document.documentElement.setAttribute('data-theme', 'dark');
        } else {
            document.documentElement.removeAttribute('data-theme');
        }
        localStorage.setItem('filebox-theme', theme);
        updateThemeIcon(theme);
    }

    // 更新主题图标 (浅色模式显示月亮 → 点击切到深色; 深色模式显示太阳 → 点击切到浅色)
    function updateThemeIcon(theme) {
        const toggleBtn = document.querySelector('.theme-toggle');
        if (toggleBtn) {
            toggleBtn.innerHTML = theme === 'dark' ? THEME_ICON('sun') : THEME_ICON('moon');
            const label = theme === 'dark' ? '切换到浅色模式' : '切换到深色模式';
            toggleBtn.setAttribute('title', label);
            toggleBtn.setAttribute('aria-label', label);
        }
    }

    // 切换主题
    function toggleTheme() {
        const currentTheme = document.documentElement.getAttribute('data-theme') || 'light';
        const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
        applyTheme(newTheme);
    }

    // 创建主题切换按钮 (若页面已硬编码按钮则复用之,并始终绑定监听器,避免硬编码按钮无响应)
    function createThemeToggle() {
        let toggle = document.querySelector('.theme-toggle');
        const slot = document.getElementById('theme-toggle-slot');
        if (!toggle) {
            toggle = document.createElement('button');
            toggle.className = 'theme-toggle';
            toggle.setAttribute('aria-label', '切换主题');
            // 优先放进页头槽位(与存储/用户/退出同排);无槽位则回退到右上角悬浮(如登录页)
            // Prefer an in-header slot; fall back to a floating button (e.g. login page).
            if (slot) {
                slot.appendChild(toggle);
                toggle.classList.add('in-header');
            } else {
                document.body.appendChild(toggle);
            }
        }
        toggle.setAttribute('title', '切换主题');
        toggle.addEventListener('click', toggleTheme);
        updateThemeIcon(localStorage.getItem('filebox-theme') || 'light');
    }

    // 立即应用主题（在 DOM 加载前）
    applyTheme(savedTheme);

    // 初始化
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function() {
            createThemeToggle();
        });
    } else {
        createThemeToggle();
    }
})();
