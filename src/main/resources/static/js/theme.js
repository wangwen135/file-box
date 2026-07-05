/**
 * File Box - 主题切换脚本
 */

(function() {
    // 从 localStorage 获取保存的主题
    const savedTheme = localStorage.getItem('filebox-theme') || 'light';

    // 主题图标 (Lucide 简洁线性图标, ISC 许可: https://lucide.dev )
    // Theme icons — clean line icons (Lucide, ISC license)
    const MOON_SVG = '<svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401"/></svg>';
    const SUN_SVG = '<svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="4"/><path d="M12 2v2"/><path d="M12 20v2"/><path d="m4.93 4.93 1.41 1.41"/><path d="m17.66 17.66 1.41 1.41"/><path d="M2 12h2"/><path d="M20 12h2"/><path d="m6.34 17.66-1.41 1.41"/><path d="m19.07 4.93-1.41 1.41"/></svg>';

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
            toggleBtn.innerHTML = theme === 'dark' ? SUN_SVG : MOON_SVG;
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
