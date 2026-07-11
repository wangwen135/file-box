(function() {
    'use strict';

    const Notification = {
        config: {
            position: 'top-right',
            duration: 3000,
            maxCount: 5
        },
        toasts: [],

        initCSS() {
            const style = document.createElement('style');
            style.textContent = `
                .notification-container {
                    position: fixed;
                    /* 高于所有 modal-overlay（默认 10000，修改密码弹窗 10001），保证提示永远在最上层
                       above all modal overlays so toasts always render on top */
                    z-index: 10010;
                    pointer-events: none;
                }
                .notification-container.top-right {
                    top: 20px;
                    right: 20px;
                }
                .notification-container.top-left {
                    top: 20px;
                    left: 20px;
                }
                .notification-container.top-center {
                    top: 20px;
                    left: 50%;
                    transform: translateX(-50%);
                }
                .notification-container.bottom-right {
                    bottom: 20px;
                    right: 20px;
                }
                .notification-container.bottom-left {
                    bottom: 20px;
                    left: 20px;
                }
                .notification-container.bottom-center {
                    bottom: 20px;
                    left: 50%;
                    transform: translateX(-50%);
                }
                
                .toast {
                    pointer-events: auto;
                    min-width: 300px;
                    max-width: 450px;
                    margin-bottom: 10px;
                    padding: 14px 20px;
                    background: #fff;
                    border-radius: 8px;
                    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12), 0 0 1px rgba(0, 0, 0, 0.1);
                    display: flex;
                    align-items: flex-start;
                    gap: 12px;
                    animation: toast-slide-in 0.3s ease-out;
                    transition: all 0.3s ease;
                }
                
                .toast.removing {
                    animation: toast-slide-out 0.3s ease-in forwards;
                }
                
                .toast.success {
                    border-left: 4px solid #52c41a;
                }
                .toast.success .toast-icon {
                    color: #52c41a;
                }
                
                .toast.error {
                    border-left: 4px solid #ff4d4f;
                }
                .toast.error .toast-icon {
                    color: #ff4d4f;
                }
                
                .toast.warning {
                    border-left: 4px solid #faad14;
                }
                .toast.warning .toast-icon {
                    color: #faad14;
                }
                
                .toast.info {
                    border-left: 4px solid #1890ff;
                }
                .toast.info .toast-icon {
                    color: #1890ff;
                }
                
                .toast-icon {
                    flex-shrink: 0;
                    font-size: 20px;
                    line-height: 1;
                    margin-top: 2px;
                }
                
                .toast-content {
                    flex: 1;
                    min-width: 0;
                }
                
                .toast-title {
                    font-size: 14px;
                    font-weight: 500;
                    color: #262626;
                    margin-bottom: 4px;
                    line-height: 1.4;
                }
                
                .toast-message {
                    font-size: 13px;
                    color: #595959;
                    line-height: 1.5;
                    word-break: break-word;
                    white-space: pre-wrap;
                }
                
                .toast-close {
                    flex-shrink: 0;
                    width: 20px;
                    height: 20px;
                    border: none;
                    background: transparent;
                    cursor: pointer;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    color: #8c8c8c;
                    font-size: 16px;
                    line-height: 1;
                    padding: 0;
                    border-radius: 4px;
                    transition: all 0.2s;
                }
                
                .toast-close:hover {
                    background: rgba(0, 0, 0, 0.06);
                    color: #262626;
                }
                
                @keyframes toast-slide-in {
                    from {
                        opacity: 0;
                        transform: translateX(100%);
                    }
                    to {
                        opacity: 1;
                        transform: translateX(0);
                    }
                }
                
                @keyframes toast-slide-out {
                    from {
                        opacity: 1;
                        transform: translateX(0);
                        max-height: 200px;
                        margin-bottom: 10px;
                        padding: 14px 20px;
                    }
                    to {
                        opacity: 0;
                        transform: translateX(100%);
                        max-height: 0;
                        margin-bottom: 0;
                        padding: 0;
                    }
                }
                
                @keyframes modal-fade-in {
                    from {
                        opacity: 0;
                    }
                    to {
                        opacity: 1;
                    }
                }
                
                @keyframes modal-scale-in {
                    from {
                        opacity: 0;
                        transform: scale(0.9);
                    }
                    to {
                        opacity: 1;
                        transform: scale(1);
                    }
                }
                
                @keyframes modal-fade-out {
                    from {
                        opacity: 1;
                    }
                    to {
                        opacity: 0;
                    }
                }
                
                .modal-overlay {
                    position: fixed;
                    top: 0;
                    left: 0;
                    right: 0;
                    bottom: 0;
                    background: rgba(0, 0, 0, 0.45);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    z-index: 10000;
                    animation: modal-fade-in 0.2s ease-out;
                    padding: 20px;
                }
                
                .modal-overlay.closing {
                    animation: modal-fade-out 0.2s ease-in forwards;
                }
                
                .notify-modal {
                    background: #fff;
                    border-radius: 12px;
                    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
                    min-width: 400px;
                    max-width: 520px;
                    animation: modal-scale-in 0.2s ease-out;
                    overflow: hidden;
                }
                
                .modal-header {
                    padding: 20px 24px 16px;
                    border-bottom: 1px solid #f0f0f0;
                }
                
                .modal-title {
                    font-size: 18px;
                    font-weight: 500;
                    color: #262626;
                    margin: 0;
                    line-height: 1.4;
                }
                
                .modal-body {
                    padding: 24px;
                    color: #595959;
                    font-size: 14px;
                    line-height: 1.6;
                }
                
                .modal-footer {
                    padding: 12px 24px 20px;
                    display: flex;
                    justify-content: flex-end;
                    gap: 12px;
                }
                
                .modal-btn {
                    padding: 8px 20px;
                    border-radius: 6px;
                    font-size: 14px;
                    font-weight: 400;
                    cursor: pointer;
                    transition: all 0.2s;
                    border: 1px solid transparent;
                    min-width: 70px;
                }
                
                .modal-btn-default {
                    background: #fff;
                    border-color: #d9d9d9;
                    color: #262626;
                }
                
                .modal-btn-default:hover {
                    color: #1890ff;
                    border-color: #1890ff;
                }
                
                .modal-btn-primary {
                    background: #1890ff;
                    border-color: #1890ff;
                    color: #fff;
                }
                
                .modal-btn-primary:hover {
                    background: #40a9ff;
                    border-color: #40a9ff;
                }
                
                .modal-btn-danger {
                    background: #ff4d4f;
                    border-color: #ff4d4f;
                    color: #fff;
                }
                
                .modal-btn-danger:hover {
                    background: #ff7875;
                    border-color: #ff7875;
                }
                
                .modal-btn-warning {
                    background: #faad14;
                    border-color: #faad14;
                    color: #fff;
                }
                
                .modal-btn-warning:hover {
                    background: #ffc53d;
                    border-color: #ffc53d;
                }

                /* 输入框模态:prompt / form / 输入框 modal: prompt & form */
                .modal-hint {
                    margin-bottom: 12px;
                    color: #595959;
                    font-size: 13px;
                    line-height: 1.6;
                    word-break: break-word;
                }
                .modal-field {
                    margin-bottom: 14px;
                }
                .modal-field:last-of-type {
                    margin-bottom: 0;
                }
                .modal-field-label {
                    display: block;
                    margin-bottom: 6px;
                    color: #262626;
                    font-size: 13px;
                }
                .modal-input {
                    display: block;
                    width: 100%;
                    box-sizing: border-box;
                }
                .modal-error {
                    margin-top: 8px;
                    color: #ff4d4f;
                    font-size: 12px;
                    line-height: 1.4;
                }
            `;
            document.head.appendChild(style);
        },

        getContainer(position) {
            // 支持单条 toast 指定位置；缺省回退到全局 config.position
            // per-toast position override; falls back to global config
            const pos = position || this.config.position;
            let container = document.querySelector('.notification-container.' + pos);
            if (!container) {
                container = document.createElement('div');
                container.className = 'notification-container ' + pos;
                document.body.appendChild(container);
            }
            return container;
        },

        showToast(options) {
            const config = typeof options === 'string' ? { message: options } : options;
            const {
                type = 'info',
                title = '',
                message = '',
                duration = this.config.duration,
                showClose = true,
                onClose,
                position
            } = config;

            if (this.toasts.length >= this.config.maxCount) {
                const oldestToast = this.toasts.shift();
                this.removeToast(oldestToast);
            }

            const container = this.getContainer(position);
            const toast = document.createElement('div');
            toast.className = `toast ${type}`;

            const icons = {
                success: '✓',
                error: '✕',
                warning: '⚠',
                info: 'ℹ'
            };

            const iconHTML = `<span class="toast-icon">${icons[type] || icons.info}</span>`;
            const closeHTML = showClose ? `<button class="toast-close">&times;</button>` : '';
            const titleHTML = title ? `<div class="toast-title">${title}</div>` : '';

            toast.innerHTML = `
                ${iconHTML}
                <div class="toast-content">
                    ${titleHTML}
                    <div class="toast-message">${message}</div>
                </div>
                ${closeHTML}
            `;

            container.appendChild(toast);
            this.toasts.push(toast);

            const closeBtn = toast.querySelector('.toast-close');
            if (closeBtn) {
                closeBtn.addEventListener('click', () => {
                    this.removeToast(toast);
                    if (onClose) onClose();
                });
            }

            if (duration > 0) {
                const timer = setTimeout(() => {
                    this.removeToast(toast);
                    if (onClose) onClose();
                }, duration);

                toast.addEventListener('mouseenter', () => clearTimeout(timer));
                toast.addEventListener('mouseleave', () => {
                    setTimeout(() => {
                        if (toast.parentNode) {
                            this.removeToast(toast);
                            if (onClose) onClose();
                        }
                    }, duration);
                });
            }

            return toast;
        },

        removeToast(toast) {
            if (!toast.parentNode) return;
            
            const index = this.toasts.indexOf(toast);
            if (index > -1) {
                this.toasts.splice(index, 1);
            }
            
            toast.classList.add('removing');
            toast.addEventListener('animationend', () => {
                if (toast.parentNode) {
                    toast.parentNode.removeChild(toast);
                }
            });
        },

        success(message, options = {}) {
            return this.showToast({ ...options, type: 'success', message });
        },

        error(message, options = {}) {
            return this.showToast({ ...options, type: 'error', message, duration: 0 });
        },

        warning(message, options = {}) {
            return this.showToast({ ...options, type: 'warning', message });
        },

        info(message, options = {}) {
            return this.showToast({ ...options, type: 'info', message });
        },

        confirm(options) {
            return new Promise((resolve) => {
                const config = typeof options === 'string' ? { content: options } : options;
                const {
                    title = '确认',
                    content = '',
                    okText = '确定',
                    cancelText = '取消',
                    okType = 'primary',
                    width = 400
                } = config;

                const overlay = document.createElement('div');
                overlay.className = 'modal-overlay';

                const modal = document.createElement('div');
                modal.className = 'notify-modal';
                if (width) modal.style.minWidth = width + 'px';

                modal.innerHTML = `
                    <div class="modal-header">
                        <h3 class="modal-title">${title}</h3>
                    </div>
                    <div class="modal-body">${content}</div>
                    <div class="modal-footer">
                        <button class="modal-btn modal-btn-default">${cancelText}</button>
                        <button class="modal-btn modal-btn-${okType}">${okText}</button>
                    </div>
                `;

                overlay.appendChild(modal);
                document.body.appendChild(overlay);

                const okBtn = modal.querySelector('.modal-btn-' + okType);
                const cancelBtn = modal.querySelector('.modal-btn-default');

                const close = (result) => {
                    document.removeEventListener('keydown', handleKeydown);
                    overlay.classList.add('closing');
                    overlay.addEventListener('animationend', () => {
                        if (overlay.parentNode) {
                            document.body.removeChild(overlay);
                        }
                    });
                    resolve(result);
                };

                const handleKeydown = (e) => {
                    if (e.key === 'Escape' || e.key === 'Esc') {
                        close(false);
                    }
                };

                document.addEventListener('keydown', handleKeydown);
                okBtn.addEventListener('click', () => close(true));
                cancelBtn.addEventListener('click', () => close(false));
                overlay.addEventListener('click', (e) => {
                    if (e.target === overlay) close(false);
                });
            });
        },

        alert(options) {
            return new Promise((resolve) => {
                const config = typeof options === 'string' ? { content: options } : options;
                const {
                    title = '提示',
                    content = '',
                    okText = '确定',
                    type = 'info',
                    width = 400
                } = config;

                const overlay = document.createElement('div');
                overlay.className = 'modal-overlay';

                const modal = document.createElement('div');
                modal.className = 'notify-modal';
                if (width) modal.style.minWidth = width + 'px';

                const iconHTML = type === 'error' ? '<span style="font-size: 48px; display: block; text-align: center; color: #ff4d4f;">✕</span>' : '';
                const titleHTML = iconHTML ? '' : `<h3 class="modal-title">${title}</h3>`;

                modal.innerHTML = `
                    <div class="modal-header">
                        ${titleHTML}
                    </div>
                    <div class="modal-body" style="${iconHTML ? 'text-align: center;' : ''}">
                        ${iconHTML}
                        ${content}
                    </div>
                    <div class="modal-footer" style="justify-content: ${iconHTML ? 'center' : 'flex-end'};">
                        <button class="modal-btn modal-btn-${type === 'error' ? 'danger' : 'primary'}">${okText}</button>
                    </div>
                `;

                overlay.appendChild(modal);
                document.body.appendChild(overlay);

                const okBtn = modal.querySelector('.modal-btn-' + (type === 'error' ? 'danger' : 'primary'));

                const close = () => {
                    overlay.classList.add('closing');
                    overlay.addEventListener('animationend', () => {
                        if (overlay.parentNode) {
                            document.body.removeChild(overlay);
                        }
                    });
                    resolve();
                };

                okBtn.addEventListener('click', close);
                overlay.addEventListener('click', (e) => {
                    if (e.target === overlay) close();
                });
            });
        },

        /**
         * 单字段输入模态(原生 prompt 的替代) / single-line input modal (prompt replacement)
         * resolve(value) 确定;resolve(null) 取消/ESC/点遮罩
         */
        prompt(options) {
            return new Promise((resolve) => {
                const config = typeof options === 'string' ? { content: options } : options;
                const {
                    title = '请输入',
                    content = '',
                    placeholder = '',
                    defaultValue = '',
                    okText = '确定',
                    cancelText = '取消',
                    okType = 'primary',
                    width = 400,
                    maxLength,
                    selectOnFocus = true,
                    validate
                } = config;

                const overlay = document.createElement('div');
                overlay.className = 'modal-overlay';

                const modal = document.createElement('div');
                modal.className = 'notify-modal';
                if (width) modal.style.minWidth = width + 'px';

                const contentHTML = content ? `<div class="modal-hint">${content}</div>` : '';
                const maxLenAttr = maxLength ? `maxlength="${maxLength}"` : '';

                modal.innerHTML = `
                    <div class="modal-header">
                        <h3 class="modal-title">${title}</h3>
                    </div>
                    <div class="modal-body">
                        ${contentHTML}
                        <input class="input-field modal-input" type="text" ${maxLenAttr}
                               placeholder="${placeholder}" autocomplete="off" />
                        <div class="modal-error" style="display:none;"></div>
                    </div>
                    <div class="modal-footer">
                        <button class="modal-btn modal-btn-default">${cancelText}</button>
                        <button class="modal-btn modal-btn-${okType}">${okText}</button>
                    </div>
                `;

                overlay.appendChild(modal);
                document.body.appendChild(overlay);

                const input = modal.querySelector('.modal-input');
                const errorEl = modal.querySelector('.modal-error');
                const okBtn = modal.querySelector('.modal-btn-' + okType);
                const cancelBtn = modal.querySelector('.modal-btn-default');

                if (defaultValue) input.value = defaultValue;

                const close = (result) => {
                    document.removeEventListener('keydown', handleKeydown);
                    overlay.classList.add('closing');
                    overlay.addEventListener('animationend', () => {
                        if (overlay.parentNode) document.body.removeChild(overlay);
                    });
                    resolve(result);
                };

                const showError = (msg) => {
                    errorEl.textContent = msg || '';
                    errorEl.style.display = msg ? 'block' : 'none';
                };

                const submit = () => {
                    const val = input.value;
                    if (typeof validate === 'function') {
                        const err = validate(val);
                        if (err) {
                            showError(err);
                            input.focus();
                            return;
                        }
                    }
                    showError('');
                    close(val);
                };

                const handleKeydown = (e) => {
                    if (e.key === 'Escape' || e.key === 'Esc') close(null);
                };

                document.addEventListener('keydown', handleKeydown);
                okBtn.addEventListener('click', submit);
                cancelBtn.addEventListener('click', () => close(null));
                input.addEventListener('keydown', (e) => {
                    if (e.key === 'Enter') { e.preventDefault(); submit(); }
                });
                overlay.addEventListener('click', (e) => {
                    if (e.target === overlay) close(null);
                });

                requestAnimationFrame(() => {
                    input.focus();
                    if (selectOnFocus && input.value) input.select();
                });
            });
        },

        /**
         * 多字段表单模态 / multi-field form modal
         * onSubmit(values) 返回错误字符串(模态内展示、不关闭)或 null(关闭并 resolve values)
         * resolve(values) 确定;resolve(null) 取消/ESC/点遮罩
         */
        form(options) {
            return new Promise((resolve) => {
                const {
                    title = '请填写',
                    fields = [],
                    okText = '确定',
                    cancelText = '取消',
                    okType = 'primary',
                    width = 400,
                    onSubmit
                } = options || {};

                const overlay = document.createElement('div');
                overlay.className = 'modal-overlay';

                const modal = document.createElement('div');
                modal.className = 'notify-modal';
                if (width) modal.style.minWidth = width + 'px';

                const fieldsHTML = fields.map(f => `
                    <div class="modal-field">
                        <label class="modal-field-label">${f.label || ''}</label>
                        <input class="input-field modal-input" type="${f.type || 'text'}"
                               name="${f.name || ''}"
                               placeholder="${f.placeholder || ''}"
                               value="${f.value || ''}"
                               ${f.autoComplete ? `autocomplete="${f.autoComplete}"` : ''} />
                    </div>
                `).join('');

                modal.innerHTML = `
                    <div class="modal-header">
                        <h3 class="modal-title">${title}</h3>
                    </div>
                    <div class="modal-body">
                        ${fieldsHTML}
                        <div class="modal-error" style="display:none;"></div>
                    </div>
                    <div class="modal-footer">
                        <button class="modal-btn modal-btn-default">${cancelText}</button>
                        <button class="modal-btn modal-btn-${okType}">${okText}</button>
                    </div>
                `;

                overlay.appendChild(modal);
                document.body.appendChild(overlay);

                const inputs = modal.querySelectorAll('.modal-input');
                const errorEl = modal.querySelector('.modal-error');
                const okBtn = modal.querySelector('.modal-btn-' + okType);
                const cancelBtn = modal.querySelector('.modal-btn-default');

                const close = (result) => {
                    document.removeEventListener('keydown', handleKeydown);
                    overlay.classList.add('closing');
                    overlay.addEventListener('animationend', () => {
                        if (overlay.parentNode) document.body.removeChild(overlay);
                    });
                    resolve(result);
                };

                const showError = (msg) => {
                    errorEl.textContent = msg || '';
                    errorEl.style.display = msg ? 'block' : 'none';
                };

                const submit = async () => {
                    const values = {};
                    inputs.forEach(inp => { values[inp.name] = inp.value; });
                    showError('');
                    if (typeof onSubmit === 'function') {
                        okBtn.disabled = true;
                        const origText = okBtn.textContent;
                        okBtn.textContent = '处理中…';
                        try {
                            const err = await onSubmit(values);
                            if (err) {
                                showError(err);
                                okBtn.disabled = false;
                                okBtn.textContent = origText;
                                return;
                            }
                        } catch (e) {
                            showError(e && e.message ? e.message : '操作失败');
                            okBtn.disabled = false;
                            okBtn.textContent = origText;
                            return;
                        }
                        okBtn.disabled = false;
                        okBtn.textContent = origText;
                    }
                    close(values);
                };

                const handleKeydown = (e) => {
                    if (e.key === 'Escape' || e.key === 'Esc') close(null);
                };

                document.addEventListener('keydown', handleKeydown);
                okBtn.addEventListener('click', submit);
                cancelBtn.addEventListener('click', () => close(null));
                inputs.forEach(inp => inp.addEventListener('keydown', (e) => {
                    if (e.key === 'Enter') { e.preventDefault(); submit(); }
                }));
                overlay.addEventListener('click', (e) => {
                    if (e.target === overlay) close(null);
                });

                requestAnimationFrame(() => {
                    if (inputs.length > 0) inputs[0].focus();
                });
            });
        },

        clear() {
            while (this.toasts.length > 0) {
                this.removeToast(this.toasts[0]);
            }
        }
    };

    Notification.initCSS();

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = Notification;
    } else {
        window.Notify = Notification;
    }
})();
