/**
 * File Box - 主题切换脚本
 */

(function() {
    // 从 localStorage 获取保存的主题
    const savedTheme = localStorage.getItem('filebox-theme') || 'light';
    
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
    
    // 更新主题图标
    function updateThemeIcon(theme) {
        const toggleBtn = document.querySelector('.theme-toggle');
        if (toggleBtn) {
            toggleBtn.textContent = theme === 'dark' ? '☀️' : '🌙';
            toggleBtn.setAttribute('title', theme === 'dark' ? '切换到浅色模式' : '切换到深色模式');
        }
    }
    
    // 切换主题
    function toggleTheme() {
        const currentTheme = document.documentElement.getAttribute('data-theme') || 'light';
        const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
        applyTheme(newTheme);
    }
    
    // 创建主题切换按钮
    function createThemeToggle() {
        // 检查是否已存在
        if (document.querySelector('.theme-toggle')) {
            return;
        }
        
        const toggle = document.createElement('button');
        toggle.className = 'theme-toggle';
        toggle.setAttribute('title', '切换主题');
        toggle.setAttribute('aria-label', '切换主题');
        toggle.addEventListener('click', toggleTheme);
        
        document.body.appendChild(toggle);
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
