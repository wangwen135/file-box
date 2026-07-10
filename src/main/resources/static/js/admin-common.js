(function () {
    const MIN_PWD_LEN = 6;

    function injectAdminTopbar() {
        const main = document.querySelector('.admin-main');
        if (!main || document.querySelector('.admin-topbar')) {
            return;
        }

        const topbar = document.createElement('div');
        topbar.className = 'admin-topbar';
        topbar.innerHTML = `
            <div class="admin-topbar-brand">
                <img class="admin-topbar-brand-logo" src="/images/logo.png" alt="File Box" onerror="this.style.display='none'"/>
                <span class="admin-topbar-logo">File Box</span>
                <span class="admin-topbar-subtitle">管理后台</span>
            </div>
            <div class="admin-topbar-actions">
                <a href="/index.html" class="admin-topbar-link">
                    <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><use href="/images/icons.svg#ico-home"/></svg>
                    <span>返回上传页</span>
                </a>
                <div class="header-dropdown admin-user-menu" id="adminUserMenu">
                    <button class="hd-trigger hd-user" id="adminUserTrigger" type="button">
                        <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><use href="/images/icons.svg#ico-user"/></svg>
                        <span id="currentUser">admin</span>
                        <svg class="ico caret" viewBox="0 0 24 24" aria-hidden="true"><use href="/images/icons.svg#ico-chevron"/></svg>
                    </button>
                    <div class="hd-menu" id="adminUserMenuList">
                        <a class="hd-menu-item" id="adminChangePwdEntry" href="javascript:void(0);">
                            <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><use href="/images/icons.svg#ico-key"/></svg>
                            <span>修改密码</span>
                        </a>
                        <a class="hd-menu-item hd-danger" href="/logout">
                            <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><use href="/images/icons.svg#ico-logout"/></svg>
                            <span>退出登录</span>
                        </a>
                    </div>
                </div>
                <span id="theme-toggle-slot"></span>
            </div>
        `;
        document.body.insertBefore(topbar, document.body.firstChild);
    }

    function setupDropdowns() {
        document.querySelectorAll('.admin-topbar .hd-trigger').forEach(trigger => {
            trigger.addEventListener('click', event => {
                event.stopPropagation();
                const wrapper = trigger.closest('.header-dropdown');
                const wasOpen = wrapper.classList.contains('open');
                document.querySelectorAll('.header-dropdown.open').forEach(el => el.classList.remove('open'));
                if (!wasOpen) {
                    wrapper.classList.add('open');
                }
            });
        });

        document.addEventListener('click', () => {
            document.querySelectorAll('.header-dropdown.open').forEach(el => el.classList.remove('open'));
        });
        document.addEventListener('keydown', event => {
            if (event.key === 'Escape') {
                document.querySelectorAll('.header-dropdown.open').forEach(el => el.classList.remove('open'));
            }
        });
    }

    function setupPasswordEntry() {
        const entry = document.getElementById('adminChangePwdEntry');
        if (!entry) {
            return;
        }
        entry.addEventListener('click', event => {
            event.preventDefault();
            event.stopPropagation();
            document.querySelectorAll('.header-dropdown.open').forEach(el => el.classList.remove('open'));
            openChangePasswordModal();
        });
    }

    function openChangePasswordModal() {
        const overlay = document.createElement('div');
        overlay.className = 'modal-overlay';
        overlay.style.zIndex = '10001';
        overlay.innerHTML = `
            <div class="notify-modal" style="min-width:380px;">
                <div class="modal-header"><h3 class="modal-title">修改密码</h3></div>
                <div class="modal-body">
                    <div style="display:flex;flex-direction:column;gap:14px;">
                        <div>
                            <label style="display:block;margin-bottom:6px;color:#262626;font-size:13px;">当前密码</label>
                            <input type="password" id="pwdCurrent" class="input-field" style="background:#fff;color:#262626;border-color:#d9d9d9;" autocomplete="current-password" />
                        </div>
                        <div>
                            <label style="display:block;margin-bottom:6px;color:#262626;font-size:13px;">新密码（至少 ${MIN_PWD_LEN} 位）</label>
                            <input type="password" id="pwdNew" class="input-field" style="background:#fff;color:#262626;border-color:#d9d9d9;" autocomplete="new-password" />
                        </div>
                        <div>
                            <label style="display:block;margin-bottom:6px;color:#262626;font-size:13px;">确认新密码</label>
                            <input type="password" id="pwdConfirm" class="input-field" style="background:#fff;color:#262626;border-color:#d9d9d9;" autocomplete="new-password" />
                        </div>
                    </div>
                </div>
                <div class="modal-footer">
                    <button type="button" class="modal-btn modal-btn-default" id="pwdCancel">取消</button>
                    <button type="button" class="modal-btn modal-btn-primary" id="pwdSubmit">确认修改</button>
                </div>
            </div>
        `;
        document.body.appendChild(overlay);

        const close = () => overlay.remove();
        const onEsc = event => {
            if (event.key === 'Escape') {
                close();
                document.removeEventListener('keydown', onEsc);
            }
        };
        overlay.addEventListener('click', event => {
            if (event.target === overlay) {
                close();
            }
        });
        document.addEventListener('keydown', onEsc);
        document.getElementById('pwdCancel').addEventListener('click', close);

        const submitBtn = document.getElementById('pwdSubmit');
        const currentEl = document.getElementById('pwdCurrent');
        const newEl = document.getElementById('pwdNew');
        const confirmEl = document.getElementById('pwdConfirm');

        [currentEl, newEl, confirmEl].forEach(el => el.addEventListener('keydown', event => {
            if (event.key === 'Enter') {
                event.preventDefault();
                submitBtn.click();
            }
        }));

        currentEl.focus();
        submitBtn.addEventListener('click', () => {
            const current = currentEl.value;
            const next = newEl.value;
            const confirm = confirmEl.value;

            if (!current || !next || !confirm) return Notify.warning('请填写所有字段', { position: 'top-center' });
            if (next.length < MIN_PWD_LEN) return Notify.warning(`新密码至少 ${MIN_PWD_LEN} 位`, { position: 'top-center' });
            if (next !== confirm) return Notify.warning('两次输入的新密码不一致', { position: 'top-center' });
            if (next === current) return Notify.warning('新密码不能与当前密码相同', { position: 'top-center' });

            submitBtn.disabled = true;
            submitBtn.textContent = '提交中...';
            fetch('/api/auth/change-password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ currentPassword: current, newPassword: next })
            })
                .then(response => response.json().then(data => ({ ok: response.ok, data })))
                .then(({ ok, data }) => {
                    if (ok && data.success) {
                        close();
                        Notify.success('密码修改成功');
                    } else {
                        submitBtn.disabled = false;
                        submitBtn.textContent = '确认修改';
                        Notify.error(data.error || '修改失败', { position: 'top-center' });
                    }
                })
                .catch(() => {
                    submitBtn.disabled = false;
                    submitBtn.textContent = '确认修改';
                    Notify.error('网络错误，请重试', { position: 'top-center' });
                });
        });
    }

    // 统一鉴权守卫:未登录跳登录页,非 ADMIN 跳首页 / Admin session guard: redirect to login if unauthenticated, to home if not admin
    async function guardAdminAccess() {
        try {
            const response = await fetch('/api/auth/user');
            if (!response.ok) {
                window.location.href = '/login.html';
                return;
            }
            const data = await response.json();
            const userEl = document.getElementById('currentUser');
            if (userEl) {
                userEl.textContent = data.username;
            }
            if (data.role !== 'ADMIN') {
                window.location.href = '/index.html';
            }
        } catch (error) {
            console.error('Failed to verify admin session:', error);
        }
    }

    injectAdminTopbar();
    setupDropdowns();
    setupPasswordEntry();
    guardAdminAccess();

    // 版本号单一来源：pom.xml → application.yml → /api/system/info
    // Single source: pom.xml → application.yml → /api/system/info
    function fillAppVersion() {
        fetch('/api/system/info')
            .then(r => r.ok ? r.json() : null)
            .then(data => {
                if (!data || !data.version) return;
                document.querySelectorAll('[data-app-version]').forEach(el => {
                    el.textContent = 'v' + data.version;
                });
            })
            .catch(() => {});
    }
    fillAppVersion();
})();
