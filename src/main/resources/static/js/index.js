    const shareNoticeUrl = window.location.origin;
    const shareNotice = document.getElementById('shareNotice');
    document.getElementById('shareNoticeUrl').textContent = shareNoticeUrl;
    document.getElementById('shareCopyBtn').addEventListener('click', async () => {
        const copied = await copyTextToClipboard(shareNoticeUrl);
        if (copied) {
            Notify.success('访问地址已复制');
        } else {
            Notify.error('复制失败，请手动复制地址');
        }
    });

    fetch('/api/auth/config')
        .then(response => response.ok ? response.json() : null)
        .then(config => {
            if (config && config.shareNoticeEnabled === false) {
                shareNotice.classList.add('hidden');
            }
        })
        .catch(() => {});

    async function copyTextToClipboard(text) {
        if (navigator.clipboard && window.isSecureContext) {
            try {
                await navigator.clipboard.writeText(text);
                return true;
            } catch (error) {
                // Fall through to legacy copy.
            }
        }
        const textarea = document.createElement('textarea');
        textarea.value = text;
        textarea.setAttribute('readonly', '');
        textarea.style.position = 'fixed';
        textarea.style.left = '-9999px';
        document.body.appendChild(textarea);
        textarea.select();
        let copied = false;
        try {
            copied = document.execCommand('copy');
        } catch (error) {
            copied = false;
        }
        document.body.removeChild(textarea);
        return copied;
    }

    // 会话级浏览状态 / session-scoped browsing state
    const BROWSER_STATE_KEY = 'filebox.browserState';
    const BROWSER_STATE_VERSION = 1;
    let currentUsername = '';

    function readBrowserState() {
        try {
            const parsed = JSON.parse(sessionStorage.getItem(BROWSER_STATE_KEY));
            if (!parsed || parsed.version !== BROWSER_STATE_VERSION || !parsed.users || typeof parsed.users !== 'object') {
                return {version: BROWSER_STATE_VERSION, users: {}};
            }
            return parsed;
        } catch (e) {
            return {version: BROWSER_STATE_VERSION, users: {}};
        }
    }

    function writeBrowserState(state) {
        try {
            sessionStorage.setItem(BROWSER_STATE_KEY, JSON.stringify(state));
        } catch (e) {
            // sessionStorage may be unavailable (privacy mode/quota); browsing must still work.
        }
    }

    function getUserBrowserState(createIfMissing = false) {
        if (!currentUsername) return null;
        const state = readBrowserState();
        const userKey = encodeURIComponent(currentUsername);
        let userState = state.users[userKey];
        if ((!userState || typeof userState !== 'object') && createIfMissing) {
            userState = {lastStorageSpace: '', spaces: {}};
            state.users[userKey] = userState;
            writeBrowserState(state);
        }
        return userState && typeof userState === 'object' ? userState : null;
    }

    function updateUserBrowserState(updater) {
        if (!currentUsername) return;
        const state = readBrowserState();
        const userKey = encodeURIComponent(currentUsername);
        let userState = state.users[userKey];
        if (!userState || typeof userState !== 'object') userState = {lastStorageSpace: '', spaces: {}};
        if (!userState.spaces || typeof userState.spaces !== 'object') userState.spaces = {};
        updater(userState);
        state.users[userKey] = userState;
        writeBrowserState(state);
    }

    function saveLastStorageSpace(name) {
        if (!name) return;
        updateUserBrowserState(userState => { userState.lastStorageSpace = name; });
    }

    function saveCurrentBrowsingState() {
        const space = window.currentStorageSpace;
        if (!space) return;
        updateUserBrowserState(userState => {
            userState.lastStorageSpace = space;
            userState.spaces[encodeURIComponent(space)] = {
                view: currentView === 'directory' ? 'directory' : 'recent',
                year: currentYear == null ? null : String(currentYear),
                month: currentMonth == null ? null : String(currentMonth),
                dirPath: Array.isArray(dirPath) ? dirPath.slice() : []
            };
        });
    }

    function restoreBrowsingState(space) {
        const userState = getUserBrowserState();
        const saved = userState && userState.spaces && userState.spaces[encodeURIComponent(space)];
        if (!saved || typeof saved !== 'object') return;
        currentView = saved.view === 'directory' ? 'directory' : 'recent';
        const savedYear = saved.year == null ? null : String(saved.year);
        const savedMonth = saved.month == null ? null : String(saved.month);
        currentYear = savedYear && /^\d{4}$/.test(savedYear) ? savedYear : null;
        currentMonth = currentYear && savedMonth && /^(0[1-9]|1[0-2])$/.test(savedMonth) ? savedMonth : null;
        dirPath = Array.isArray(saved.dirPath)
            ? saved.dirPath.filter(part => typeof part === 'string'
                && part.length > 0 && part !== '.' && part !== '..' && !/[\\/]/.test(part))
            : [];
    }

    // 获取并显示用户组信息
    fetch('/api/user')
        .then(response => {
            if (!response.ok) {
                window.location.href = '/login.html';
                return;
            }
            return response.json();
        })
        .then(async data => {
            if (!data) return;
            window.currentRole = data.role;
            window.isAnonymous = data.isAnonymous || false;
            window.currentStorageSpace = data.currentStorageSpace || data.groupName || '';
            currentUsername = data.username || '';

            const spaceList = Array.isArray(data.storageSpaces) ? data.storageSpaces : [];
            const userState = getUserBrowserState();
            const savedSpace = userState && userState.lastStorageSpace;
            if (savedSpace && spaceList.includes(savedSpace) && savedSpace !== window.currentStorageSpace) {
                if (await switchStorage(savedSpace, false)) return;
            }
            if (!spaceList.includes(savedSpace)) saveLastStorageSpace(window.currentStorageSpace);
            restoreBrowsingState(window.currentStorageSpace);

            // 用户名(管理员加 ★ 样式) / username (admin gets star styling)
            const userInfoElement = document.getElementById('user-info');
            userInfoElement.textContent = data.username;
            const roleNames = { admin: '超级管理员', manager: '管理员', user: '普通用户' };
            document.getElementById('currentRole').textContent = data.isAnonymous
                ? '匿名用户'
                : (roleNames[data.role] || data.role || '—');
            if (data.isAnonymous) {
                document.getElementById('changePwdEntry').style.display = 'none';
                document.getElementById('logoutEntry').style.display = 'none';
                document.getElementById('loginEntry').style.display = '';
            }
            if (data.role === 'admin') {
                userInfoElement.classList.add('admin');
                userInfoElement.title = '管理员用户';
                // 管理员菜单显示“后台管理” / show admin-panel item for ADMIN only
                document.getElementById('adminEntry').style.display = '';
                document.getElementById('adminSep').style.display = '';
            }
            // ADMIN 与 MANAGER 均可在目录浏览里新建/重命名/删除文件夹 / folder ops for ADMIN & MANAGER
            if (data.role === 'admin' || data.role === 'manager') {
                const nfb = document.getElementById('newFolderBtn');
                if (nfb) nfb.style.display = '';
            }

            // 存储空间切换器 / storage space switcher
            renderStorageSwitcher(spaceList, window.currentStorageSpace);
            switchView(currentView);
        })
        .catch(() => {
            window.location.href = '/login.html';
        });

    // ===== 顶栏下拉菜单:存储切换 + 用户菜单 / header dropdowns =====
    // 渲染存储空间切换器 / render the storage-space switcher
    function renderStorageSwitcher(spaceList, current) {
        const trigger = document.getElementById('storageTrigger');
        const currentEl = document.getElementById('storageCurrent');
        const menu = document.getElementById('storageMenu');
        const caret = trigger.querySelector('.caret');

        currentEl.textContent = current || (spaceList[0] || '');

        // 只有一个(或没有)空间:作为静态标签,不展开下拉 / single space: static label, no dropdown
        if (!spaceList.length || spaceList.length <= 1) {
            if (caret) caret.style.display = 'none';
            trigger.style.cursor = 'default';
            return;
        }

        menu.innerHTML = spaceList.map(name => `
            <div class="hd-menu-item${name === current ? ' active' : ''}" data-space="${name}">
                ${name === current ? '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><use href="/images/icons.svg#ico-check"/></svg>' : ''}${name}
            </div>
        `).join('');
        menu.querySelectorAll('.hd-menu-item').forEach(el => {
            el.addEventListener('click', () => switchStorage(el.dataset.space));
        });
    }

    // 切换存储空间并刷新 / switch storage space then reload
    async function switchStorage(name, preserveCurrentState = true) {
        if (preserveCurrentState) saveCurrentBrowsingState();
        saveLastStorageSpace(name);
        try {
            const resp = await fetch('/api/auth/switch-storage', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ storageSpace: name })
            });
            if (resp.ok) {
                window.location.reload(); // 切换后刷新以重载文件列表 / reload to refresh file list
                return true;
            } else {
                saveLastStorageSpace(window.currentStorageSpace);
                Notify.error('切换存储空间失败', { position: 'top-center' });
                return false;
            }
        } catch (e) {
            saveLastStorageSpace(window.currentStorageSpace);
            Notify.error('切换存储空间失败：' + e.message, { position: 'top-center' });
            return false;
        }
    }

    // 下拉菜单:点击触发器展开/收起,点击外部或 Esc 关闭 / toggle on click, close on outside click / Esc
    function setupHeaderDropdowns() {
        document.querySelectorAll('.header-dropdown .hd-trigger').forEach(trigger => {
            trigger.addEventListener('click', (e) => {
                e.stopPropagation();
                const wrapper = trigger.closest('.header-dropdown');
                const menu = wrapper.querySelector('.hd-menu');
                if (!menu || !menu.children.length) return; // 空菜单不展开 / no items → skip
                const wasOpen = wrapper.classList.contains('open');
                document.querySelectorAll('.header-dropdown.open').forEach(w => w.classList.remove('open'));
                if (!wasOpen) wrapper.classList.add('open');
            });
        });
        document.addEventListener('click', () => {
            document.querySelectorAll('.header-dropdown.open').forEach(w => w.classList.remove('open'));
        });
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                document.querySelectorAll('.header-dropdown.open').forEach(w => w.classList.remove('open'));
            }
        });
    }
    setupHeaderDropdowns();

    // ===== 修改密码 / change password =====
    const MIN_PWD_LEN = 6;
    const changePwdEntry = document.getElementById('changePwdEntry');
    if (changePwdEntry) {
        changePwdEntry.addEventListener('click', (event) => {
            event.preventDefault();
            event.stopPropagation();
            document.querySelectorAll('.header-dropdown.open').forEach(w => w.classList.remove('open'));
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
                            <label style="display:block;margin-bottom:6px;color:var(--text-primary);font-size:13px;">当前密码</label>
                            <input type="password" id="pwdCurrent" class="input-field" autocomplete="current-password" />
                        </div>
                        <div>
                            <label style="display:block;margin-bottom:6px;color:var(--text-primary);font-size:13px;">新密码（至少 ${MIN_PWD_LEN} 位）</label>
                            <input type="password" id="pwdNew" class="input-field" autocomplete="new-password" />
                        </div>
                        <div>
                            <label style="display:block;margin-bottom:6px;color:var(--text-primary);font-size:13px;">确认新密码</label>
                            <input type="password" id="pwdConfirm" class="input-field" autocomplete="new-password" />
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

        let closed = false;
        let passwordToast = null;
        const showPasswordToast = (type, message) => {
            if (passwordToast) Notify.removeToast(passwordToast);
            passwordToast = Notify[type](message, { position: 'top-center' });
        };
        const close = () => {
            if (closed) return;
            closed = true;
            document.removeEventListener('keydown', onEsc);
            if (passwordToast) {
                Notify.removeToast(passwordToast);
                passwordToast = null;
            }
            overlay.remove();
        };
        const onEsc = (event) => {
            if (event.key === 'Escape') {
                close();
            }
        };
        overlay.addEventListener('click', (event) => { if (event.target === overlay) close(); });
        document.addEventListener('keydown', onEsc);
        document.getElementById('pwdCancel').addEventListener('click', close);

        const submitBtn = document.getElementById('pwdSubmit');
        const currentEl = document.getElementById('pwdCurrent');
        const newEl = document.getElementById('pwdNew');
        const confirmEl = document.getElementById('pwdConfirm');

        [currentEl, newEl, confirmEl].forEach(el => el.addEventListener('keydown', (event) => {
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

            if (!current || !next || !confirm) return showPasswordToast('warning', '请填写所有字段');
            if (next.length < MIN_PWD_LEN) return showPasswordToast('warning', `新密码至少 ${MIN_PWD_LEN} 位`);
            if (next !== confirm) return showPasswordToast('warning', '两次输入的新密码不一致');
            if (next === current) return showPasswordToast('warning', '新密码不能与当前密码相同');

            submitBtn.disabled = true;
            submitBtn.textContent = '提交中...';
            fetch('/api/auth/change-password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ currentPassword: current, newPassword: next })
                })
                .then(r => r.json().then(data => ({ ok: r.ok, data })))
                .then(({ ok, data }) => {
                    if (closed) return;
                    if (ok && data.success) {
                        close();
                        Notify.success('密码修改成功');
                    } else {
                        submitBtn.disabled = false;
                        submitBtn.textContent = '确认修改';
                        showPasswordToast('error', data.error || '修改失败');
                    }
                })
                .catch(() => {
                    if (closed) return;
                    submitBtn.disabled = false;
                    submitBtn.textContent = '确认修改';
                    showPasswordToast('error', '网络错误，请重试');
                });
        });
    }

    const dropzone = document.getElementById("dropzone");
    const textpaste = document.getElementById("textpaste");
    const sendTextBtn = document.getElementById("sendText");
    const recentDiv = document.getElementById("recent");
    const fileInput = document.getElementById("fileInput");
    const selectFileBtn = document.getElementById("selectFileBtn");

    // 添加按钮点击事件触发文件选择
    selectFileBtn.addEventListener("click", (e) => {
        e.stopPropagation(); // 阻止事件冒泡，避免触发dropzone的点击事件
        fileInput.click();
    });

    let currentYear = null;
    let currentMonth = null;
    let fileRequestSequence = 0;
    let currentXhr = null; // 用于保存当前的XMLHttpRequest对象，以便取消上传

    // ============== 无限滚动分页状态 / infinite-scroll pagination state ==============
    const PAGE_SIZE = 50;          // 与后端默认分页大小对齐 / align with the backend default page size
    let currentOffset = 0;         // 当前分页偏移(两视图共用) / current page offset (shared)
    let currentHasMore = false;    // 是否还有更多 / more pages available
    let isLoadingMore = false;     // 防止并发滚动加载 / guard against concurrent scroll loads
    let recentObserver = null;     // #recent 的 IntersectionObserver
    let dirObserver = null;        // #dirContent 的 IntersectionObserver
    let dirFileGridEl = null;      // renderDir 缓存的 .dir-files 网格引用，供追加用 / cached grid for append

    // 统一处理 fetch 请求：若被重定向到 /login(会话过期)则跳转登录页 / Unified fetch wrapper
    function handleFetch(url, options = {}) {
        return fetch(url, options).then(resp => {
            if (resp.redirected && resp.url.includes('/login')) {
                window.location.href = resp.url;
                return Promise.reject('已跳转到登录页面');
            }
            return resp;
        }).catch(error => {
            if (error !== '已跳转到登录页面') {
                console.error('请求错误:', error);
            }
            throw error;
        });
    }

    // 格式化文件大小 / Format byte count
    function formatFileSize(bytes) {
        if (!bytes || bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + sizes[i];
    }

    // HTML 转义，防止文件名造成注入 / Escape HTML
    function escapeHtml(text) {
        const div = document.createElement("div");
        div.textContent = text;
        return div.innerHTML;
    }

    // Show hover style
    dropzone.addEventListener("dragover", e => {
        e.preventDefault();
        dropzone.classList.add("dragover");
    });
    dropzone.addEventListener("dragleave", e => {
        dropzone.classList.remove("dragover");
    });
    dropzone.addEventListener("drop", e => {
        e.preventDefault();
        dropzone.classList.remove("dragover");
        handleFiles(e.dataTransfer.files);
    });
    dropzone.addEventListener("click", () => fileInput.click());

    // 粘贴处理
    document.addEventListener("paste", e => {
        const items = (e.clipboardData || e.originalEvent?.clipboardData || window.clipboardData)?.items || [];
        let hasFiles = false;

        // 检查是否有文件粘贴
        for (let item of items) {
            if (item.kind === "file") {
                const file = item.getAsFile();
                if (file) {
                    hasFiles = true;
                    handleFiles([file]);
                    break; // 只处理第一个文件
                }
            }
        }

        // 如果有文件粘贴，阻止默认行为以避免冲突
        if (hasFiles) {
            e.preventDefault();
        } else {
            // 没有文件粘贴的情况
            if (e.target !== textpaste) {
                // 如果不是在文本输入框中粘贴，阻止默认行为并将文本放入输入框
                e.preventDefault();
                const text = (e.clipboardData || window.clipboardData).getData("text");
                if (text) {
                    // 检测base64图像数据URI (移动设备粘贴常见)
                    if (text.startsWith("data:image")) {
                        fetch(text)
                            .then(res => res.blob())
                            .then(blob => {
                                const filename = "pasted_" + new Date().toISOString().replace(/[:.]/g, "") + ".png";
                                const file = new File([blob], filename, {type: blob.type});
                                uploadFiles([file]);
                            })
                            .catch(() => {
                                // 回退到文本输入框
                                textpaste.value = text;
                            });
                    } else {
                        textpaste.value = text;
                    }
                }
            }
            // 如果是在文本输入框中粘贴文本，不阻止默认行为，让浏览器正常处理
        }
    });

    fileInput.addEventListener("change", e => {
        const files = e.target.files;
        if (files.length > 0) {
            handleFiles(files);
            fileInput.value = ""; // 清除输入以允许重新选择相同文件
        }
    });

    sendTextBtn.addEventListener("click", () => {
        const txt = textpaste.value.trim();
        if (!txt) return Notify.warning("请输入一些文本");
        uploadText(txt);
    });

    // 取消上传按钮点击事件
    document.getElementById('upload-cancel-btn').addEventListener('click', () => {
        if (currentXhr) {
            currentXhr.abort(); // 取消上传
            currentXhr = null;
        }
        // 隐藏模态框
        document.getElementById('upload-modal-overlay').style.display = 'none';

        document.getElementById('countdown-bar').style.display = 'none';;
    });

    /**
     * 处理文件输入（只取第一个文件）
     * @param {FileList} files - 文件列表
     */
    function handleFiles(files) {
        // 只处理第一个文件
        if (files.length > 0) {
            uploadFiles([files[0]]);
        }
    }


    function uploadFiles(files) {
        const formData = new FormData();
        const timestamp = new Date().toISOString().replace(/[:.]/g, "");
        const file = files[0]; // 只处理第一个文件
        const filename = file.name || `pasted_${timestamp}.bin`;
        formData.append("file", file, filename);
        // 目录视图上传到当前文件夹；其他情况上传到存储空间根目录。
        if (typeof currentView !== 'undefined' && currentView === 'directory' && dirPath && dirPath.length > 0) {
            formData.append("targetFolder", dirPath.join('/'));
        }

        // 显示进度模态框
        const modalOverlay = document.getElementById('upload-modal-overlay');
        const progressContainer = document.getElementById('upload-progress-container');
        const progressBar = document.getElementById('upload-progress-bar');
        const statusText = document.getElementById('upload-status');
        const progressPercentage = document.getElementById('upload-progress-percentage');
        const sizeInfo = document.getElementById('upload-size-info');
        const speedInfo = document.getElementById('upload-speed');
        modalOverlay.style.display = 'flex';
        progressBar.style.width = '0%';
        progressPercentage.textContent = '0%';
        statusText.textContent = `正在上传文件【${filename}】...`;
        sizeInfo.textContent = '0 B / 0 B';
        speedInfo.textContent = '0 B/s';
        // 恢复按钮文本为"取消上传"
        document.getElementById('upload-cancel-btn').textContent = '取消上传';

        // 上传速度计算变量
        let startTime = Date.now();
        let lastLoaded = 0;
        let lastTime = startTime;

        // 使用 XMLHttpRequest 实现带进度监听的文件上传
        currentXhr = new XMLHttpRequest();

        // 监听上传进度
        currentXhr.upload.addEventListener('progress', (event) => {
            if (event.lengthComputable) {
                const percentComplete = Math.round((event.loaded / event.total) * 100);
                progressBar.style.width = percentComplete + '%';
                progressPercentage.textContent = percentComplete + '%';
                statusText.textContent = `正在上传文件【${filename}】...`;

                // 更新已上传大小和总大小
                sizeInfo.textContent = `${formatFileSize(event.loaded)} / ${formatFileSize(event.total)}`;

                // 计算上传速度
                const currentTime = Date.now();
                const timeDiff = currentTime - lastTime;
                const bytesDiff = event.loaded - lastLoaded;

                if (timeDiff > 0) {
                    const speed = (bytesDiff * 1000) / timeDiff;
                    speedInfo.textContent = `${formatFileSize(speed)}/s`;
                }

                lastLoaded = event.loaded;
                lastTime = currentTime;
            }
        });

        // 监听上传完成
        currentXhr.addEventListener('load', () => {
            if (currentXhr.status >= 200 && currentXhr.status < 300) {
                progressBar.style.width = '100%';
                progressPercentage.textContent = '100%';
                statusText.innerHTML = '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><use href="/images/icons.svg#ico-check"/></svg>上传成功！';
                statusText.style.color = '#52c41a';

                // 触发倒计时动画
                const countdownBar = document.getElementById('countdown-bar');
                countdownBar.style.display = 'block';

                // 延迟刷新:按当前视图刷新对应列表 / Refresh whichever view is active
                setTimeout(() => {
                    if (typeof currentView !== 'undefined' && currentView === 'directory') {
                        fetchDir();
                    } else {
                        fetchFiles();
                    }
                }, 500);

                // 3秒后隐藏模态框
                setTimeout(() => {
                    modalOverlay.style.display = 'none';
                    countdownBar.style.display = 'none';
                    // 重置状态
                    statusText.style.color = '#333';
                }, 3000);

                // 上传成功后，修改按钮文本为"关闭"
                const cancelBtn = document.getElementById('upload-cancel-btn');
                cancelBtn.textContent = '关闭';
            } else {
                // 根据HTTP状态码显示不同的错误信息
                let errorMsg = '上传失败';
                if (currentXhr.status === 413) {
                    errorMsg = '文件太大，超过服务器限制';
                } else if (currentXhr.status === 507) {
                    errorMsg = '存储空间不足';
                } else if (currentXhr.status >= 500) {
                    errorMsg = '服务器错误，请稍后重试';
                } else if (currentXhr.status >= 400) {
                    errorMsg = '请求错误，请重新登录';
                }

                statusText.innerHTML = '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><use href="/images/icons.svg#ico-x"/></svg>' + errorMsg;
                statusText.style.color = '#ff4d4f';
                progressPercentage.textContent = 'Error';
                progressBar.style.backgroundColor = '#ff4d4f';

                // 上传失败时，修改按钮文本为"关闭"
                const cancelBtn = document.getElementById('upload-cancel-btn');
                cancelBtn.textContent = '关闭';
            }
            currentXhr = null;
        });


        // 监听上传错误
        currentXhr.addEventListener('error', () => {
            statusText.innerHTML = '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><use href="/images/icons.svg#ico-x"/></svg>网络错误，请检查连接';
            statusText.style.color = '#ff4d4f';
            progressPercentage.textContent = 'Error';
            progressBar.style.backgroundColor = '#ff4d4f';

            // 网络错误时，修改按钮文本为"关闭"
            const cancelBtn = document.getElementById('upload-cancel-btn');
            cancelBtn.textContent = '关闭';
            currentXhr = null;
        });

        // 监听上传被取消
        currentXhr.addEventListener('abort', () => {
            statusText.innerHTML = '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><use href="/images/icons.svg#ico-x"/></svg>上传已取消';
            statusText.style.color = '#999';
            progressPercentage.textContent = 'Cancelled';
            progressBar.style.backgroundColor = '#999';

            setTimeout(() => {
                modalOverlay.style.display = 'none';
                document.getElementById('countdown-bar').classList.remove('active');
                // 重置状态
                statusText.style.color = '#333';
                progressBar.style.backgroundColor = '#4a90e2';
            }, 2000);
            currentXhr = null;
        });

        // 发送请求
        currentXhr.open('POST', '/upload_file', true);
        currentXhr.send(formData);
    }

    /**
     * 上传文本内容
     * @param {string} text - 文本内容
     */
    function uploadText(text) {
        const payload = {text: text};
        // 目录视图:文本也进入当前文件夹(与文件上传一致) / Directory view: text also goes into current folder
        if (typeof currentView !== 'undefined' && currentView === 'directory' && dirPath && dirPath.length > 0) {
            payload.targetFolder = dirPath.join('/');
        }
        handleFetch("/upload_text", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(payload),
        }).then(resp => {
            if (!resp.ok) Notify.error("上传失败");
            else {
                Notify.success("文本上传成功");
                textpaste.value = "";
                // 按当前视图刷新:目录模式刷目录列表,否则刷时间线 / refresh the active view (dir or timeline)
                if (typeof currentView !== 'undefined' && currentView === 'directory') fetchDir();
                else fetchFiles();
            }
        });
    }

    document.querySelectorAll('.timeline-scroll').forEach(button => {
        button.addEventListener('click', () => {
            const viewport = document.getElementById(button.dataset.target);
            viewport.scrollBy({left: Number(button.dataset.direction) * Math.max(240, viewport.clientWidth * 0.7), behavior: 'smooth'});
        });
    });

    document.querySelectorAll('.timeline-viewport').forEach(viewport => {
        viewport.addEventListener('scroll', updateTimelineScrollControls, {passive: true});
    });
    window.addEventListener('resize', updateTimelineScrollControls);

    function updateTimelineScrollControls() {
        document.querySelectorAll('.timeline-row').forEach(row => {
            const viewport = row.querySelector('.timeline-viewport');
            const buttons = row.querySelectorAll('.timeline-scroll');
            if (!viewport || buttons.length !== 2) return;
            const hasOverflow = viewport.scrollWidth > viewport.clientWidth + 1;
            buttons.forEach(button => button.classList.toggle('is-hidden', !hasOverflow));
            buttons[0].disabled = hasOverflow && viewport.scrollLeft <= 1;
            buttons[1].disabled = hasOverflow && viewport.scrollLeft + viewport.clientWidth >= viewport.scrollWidth - 1;
        });
    }

    function centerActiveTimelineNodes() {
        document.querySelectorAll('.timeline-node.active').forEach(node => {
            const viewport = node.closest('.timeline-viewport');
            if (!viewport) return;
            const targetLeft = node.offsetLeft - (viewport.clientWidth - node.offsetWidth) / 2;
            viewport.scrollTo({left: Math.max(0, targetLeft), behavior: 'smooth'});
        });
        updateTimelineScrollControls();
    }

    function createTimelineNode(label, active, onClick) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'timeline-node' + (active ? ' active' : '');
        button.textContent = label;
        button.setAttribute('role', 'tab');
        button.setAttribute('aria-selected', String(active));
        button.addEventListener('click', onClick);
        return button;
    }

    function selectTimeline(year, month) {
        if (currentYear === year && currentMonth === month) return;
        currentYear = year;
        currentMonth = month;
        saveCurrentBrowsingState();
        fetchFiles();
    }

    function timelineSelectionExists(periods) {
        if (currentYear === null) return true;
        const period = periods.find(item => String(item.year) === String(currentYear));
        if (!period) return false;
        if (currentMonth === null) return true;
        return Array.isArray(period.months)
            && period.months.some(month => String(month) === String(currentMonth));
    }

    function renderTimeline(periods) {
        const safePeriods = Array.isArray(periods) ? periods : [];
        const yearTrack = document.getElementById('yearTimeline');
        yearTrack.innerHTML = '';

        yearTrack.appendChild(createTimelineNode('最近', currentYear === null,
            () => selectTimeline(null, null)));
        safePeriods.forEach(period => {
            const group = document.createElement('div');
            const expanded = currentYear === period.year;
            group.className = 'timeline-period-group' + (expanded ? ' expanded' : '');
            group.appendChild(createTimelineNode(period.year, expanded,
                () => selectTimeline(period.year, null)));

            if (expanded) {
                const leftCurve = document.createElement('span');
                leftCurve.className = 'timeline-curve timeline-curve-left';
                leftCurve.setAttribute('aria-hidden', 'true');
                leftCurve.innerHTML = '<svg viewBox="0 0 24 20" preserveAspectRatio="none"><path d="M0 1 C12 1 12 19 24 19"/></svg>';
                group.appendChild(leftCurve);

                const rightCurve = document.createElement('span');
                rightCurve.className = 'timeline-curve timeline-curve-right';
                rightCurve.setAttribute('aria-hidden', 'true');
                rightCurve.innerHTML = '<svg viewBox="0 0 24 20" preserveAspectRatio="none"><path d="M0 19 C12 19 12 1 24 1"/></svg>';
                group.appendChild(rightCurve);

                const branch = document.createElement('div');
                branch.className = 'timeline-month-branch';
                branch.setAttribute('role', 'tablist');
                branch.setAttribute('aria-label', period.year + '年月份');
                const months = period.months || [];
                group.style.width = (124 + months.length * 58) + 'px';
                months.forEach(month => {
                    const monthLabel = parseInt(month, 10) + '月';
                    branch.appendChild(createTimelineNode(monthLabel, currentMonth === month,
                        () => selectTimeline(period.year, month)));
                });
                group.appendChild(branch);
            }
            yearTrack.appendChild(group);
        });

        requestAnimationFrame(centerActiveTimelineNodes);
    }

    // ============== 无限滚动分页 / infinite-scroll pagination ==============

    /** 重置分页状态(切视图/年月/目录/存储后由各 fetch 统一调用) / reset pagination state */
    function resetPagination() {
        currentOffset = 0;
        currentHasMore = false;
        isLoadingMore = false;
        hideSentinel('recent');
        hideSentinel('dir');
    }

    function showSentinel(which) {
        const el = document.getElementById(which === 'dir' ? 'dir-sentinel' : 'recent-sentinel');
        if (el) el.hidden = false;
    }

    function hideSentinel(which) {
        const el = document.getElementById(which === 'dir' ? 'dir-sentinel' : 'recent-sentinel');
        if (el) el.hidden = true;
    }

    /** 追加文件到 #recent(不清空) / append files to #recent without clearing */
    function appendFileList(files) {
        if (!files || files.length === 0) return;
        const empty = recentDiv.querySelector('.empty-state');
        if (empty) empty.remove();
        for (const file of files) {
            const el = createFileElement(file);
            if (el) recentDiv.appendChild(el);
        }
    }

    /** 追加文件到目录视图网格(缺失则新建) / append files to the dir grid, creating it if missing */
    function appendDirFiles(files) {
        if (!files || files.length === 0) return;
        if (!dirFileGridEl || !document.body.contains(dirFileGridEl)) {
            dirFileGridEl = document.createElement('div');
            dirFileGridEl.className = 'dir-files';
            document.getElementById('dirContent').appendChild(dirFileGridEl);
        }
        for (const file of files) {
            const el = createFileElement(file);
            if (el) dirFileGridEl.appendChild(el);
        }
    }

    /** 时间线视图：加载下一页 / timeline view: load next page */
    function loadMoreFiles() {
        if (isLoadingMore || !currentHasMore || currentView !== 'recent') return;
        isLoadingMore = true;
        showSentinel('recent');
        let loaded = 0;
        const requestId = fileRequestSequence; // 翻页不自增序号，便于被新的 page-1 请求作废
        const snapYear = currentYear, snapMonth = currentMonth;
        const params = new URLSearchParams({limit: String(PAGE_SIZE), offset: String(currentOffset)});
        if (snapYear) params.set('year', snapYear);
        if (snapMonth) params.set('month', snapMonth);
        handleFetch('/list_files?' + params.toString()).then(r => {
            if (!r.ok) throw new Error('加载更多失败');
            return r.json();
        }).then(data => {
            // 过期则丢弃：被新 page-1 取代，或视图/年月已变 / drop if superseded or view/year/month changed
            if (requestId !== fileRequestSequence || currentView !== 'recent'
                    || snapYear !== currentYear || snapMonth !== currentMonth) return;
            loaded = data.files ? data.files.length : 0;
            appendFileList(data.files);
            currentHasMore = !!data.hasMore;
            currentOffset += loaded;
        }).catch(err => {
            if (requestId !== fileRequestSequence) return;
            console.error('加载更多失败:', err);
        }).finally(() => {
            isLoadingMore = false;
            if (!currentHasMore) {
                hideSentinel('recent');
            } else if (loaded > 0) {
                // 哨兵仍在视口内(大屏/小卡)则继续加载，避免一屏装不满就停下
                // keep loading if the sentinel is still on-screen (large viewport / small cards)
                const s = document.getElementById('recent-sentinel');
                if (s && s.getBoundingClientRect().top <= window.innerHeight + 300) loadMoreFiles();
            }
        });
    }

    /** 目录视图：加载下一页 / directory view: load next page */
    function loadMoreDir() {
        if (isLoadingMore || !currentHasMore || currentView !== 'directory') return;
        const snapDir = dirPath.join('/');
        isLoadingMore = true;
        showSentinel('dir');
        let loaded = 0;
        const params = new URLSearchParams({path: snapDir, limit: String(PAGE_SIZE), offset: String(currentOffset)});
        handleFetch('/list_dir?' + params.toString()).then(r => {
            if (!r.ok) throw new Error('加载更多失败');
            return r.json();
        }).then(data => {
            if (currentView !== 'directory' || snapDir !== dirPath.join('/')) return; // 已切目录则丢弃
            loaded = data.files ? data.files.length : 0;
            appendDirFiles(data.files);
            currentHasMore = !!data.hasMore;
            currentOffset += loaded;
        }).catch(err => {
            console.error('list_dir 加载更多失败:', err);
        }).finally(() => {
            isLoadingMore = false;
            if (!currentHasMore) {
                hideSentinel('dir');
            } else if (loaded > 0) {
                const s = document.getElementById('dir-sentinel');
                if (s && s.getBoundingClientRect().top <= window.innerHeight + 300) loadMoreDir();
            }
        });
    }

    /** 初始化两个哨兵的 IntersectionObserver(启动时调一次) / wire up sentinels once at startup */
    function initInfiniteScroll() {
        const opts = {rootMargin: '300px'}; // 提前 300px 预取 / pre-fetch 300px before bottom
        const rs = document.getElementById('recent-sentinel');
        if (rs) {
            recentObserver = new IntersectionObserver(entries => {
                if (entries[0] && entries[0].isIntersecting && currentView === 'recent') loadMoreFiles();
            }, opts);
            recentObserver.observe(rs);
        }
        const ds = document.getElementById('dir-sentinel');
        if (ds) {
            dirObserver = new IntersectionObserver(entries => {
                if (entries[0] && entries[0].isIntersecting && currentView === 'directory') loadMoreDir();
            }, opts);
            dirObserver.observe(ds);
        }
    }

    /**
     * 获取文件列表并显示
     */
    function fetchFiles() {
        resetPagination();
        const requestId = ++fileRequestSequence;
        const params = new URLSearchParams({limit: String(PAGE_SIZE), offset: '0'});
        if (currentYear) {
            params.set('year', currentYear);
        }
        if (currentMonth) {
            params.set('month', currentMonth);
        }

        // 显示加载状态
        showLoadingState();

        handleFetch('/list_files?' + params.toString()).then(async r => {
            if (!r.ok) throw new Error(await r.text() || '文件列表请求失败');
            return r.json();
        }).then(data => {
            if (requestId !== fileRequestSequence) return;
            const periods = Array.isArray(data.periods) ? data.periods : [];
            if (!timelineSelectionExists(periods)) {
                currentYear = null;
                currentMonth = null;
                saveCurrentBrowsingState();
                fetchFiles();
                return;
            }
            renderTimeline(periods);
            renderFileList(data.files);
            currentOffset = (data.files ? data.files.length : 0);
            currentHasMore = !!data.hasMore;
            if (currentHasMore) showSentinel('recent');
        }).catch(err => {
            if (requestId !== fileRequestSequence) return;
            console.error('获取文件列表失败:', err);
            Notify.error('获取文件列表失败，请重试');
            // 显示错误状态
            recentDiv.innerHTML = `
                <div style="text-align:center;color:#ff4444;padding:60px 40px;">
                    <div style="margin-bottom:16px;"><svg style="width:48px;height:48px;" viewBox="0 0 24 24" aria-hidden="true"><use href="/images/icons.svg#ico-alert"/></svg></div>
                    <div style="font-size:16px;margin-bottom:8px;">加载失败</div>
                    <div style="font-size:13px;color:#999;">请检查网络连接后重试</div>
                </div>
            `;
        });
    }

    /**
     * 渲染文件列表
     * @param {Array} files - 文件数组
     */
    function renderFileList(files) {
        recentDiv.innerHTML = "";
        if (!files || files.length === 0) {
            renderEmptyState();
            return;
        }

        for (let file of files) {
            const fileElement = createFileElement(file);
            if (fileElement) {
                recentDiv.appendChild(fileElement);
            }
        }
    }

    /**
     * 渲染空状态
     */
    function renderEmptyState() {
        const emptyState = document.createElement('div');
        emptyState.className = 'empty-state';
        emptyState.innerHTML = `
            <div style="text-align:center;color:#999;padding:60px 40px;">
                <div style="margin-bottom:16px;opacity:0.3;"><svg style="width:48px;height:48px;" viewBox="0 0 24 24" aria-hidden="true"><use href="/images/icons.svg#ico-folder"/></svg></div>
                <div style="font-size:16px;margin-bottom:8px;">暂无上传文件</div>
                <div style="font-size:13px;color:#bbb;">拖放文件到上方区域，或点击选择文件开始上传</div>
            </div>
        `;
        recentDiv.appendChild(emptyState);
    }

    /**
     * 显示加载状态
     */
    function showLoadingState() {
        recentDiv.innerHTML = `
            <div style="text-align:center;color:#999;padding:60px 40px;">
                <div class="loading-spinner"></div>
                <div style="margin-top:16px;font-size:14px;">正在加载文件列表...</div>
            </div>
        `;
    }

    /**
     * 创建单个文件元素
     * @param {Object} file - 文件对象
     * @returns {HTMLElement} 文件元素
     */
    function createFileElement(file) {
        const a = document.createElement("a");
        a.href = file.url;
        a.target = "_blank";

        const previewDiv = document.createElement("div");
        previewDiv.className = "preview";

        // 添加预览内容
        addPreviewContent(previewDiv, file);

        // 添加文件信息
        addFileInfo(previewDiv, file);

        // 添加删除按钮（ADMIN 与 MANAGER）/ add delete button (ADMIN & MANAGER)
        if (window.currentRole === 'admin' || window.currentRole === 'manager') {
            addDeleteButton(previewDiv, file, a);
        }

        a.appendChild(previewDiv);
        return a;
    }


    /**
     * 添加预览内容
     * @param {HTMLElement} container - 容器元素
     * @param {Object} file - 文件对象
     */
    function addPreviewContent(container, file) {
        const isPreviewable = file.type === "text" || file.type === "image" || file.type === "video";

        if (isPreviewable) {
            if (file.type === "text") {
                const pre = document.createElement("pre");
                pre.textContent = file.content;
                container.appendChild(pre);
            } else if (file.type === "image") {
                const img = document.createElement("img");
                img.src = file.url;
                img.alt = escapeHtml(file.filename);
                img.loading = "lazy"; // 添加懒加载
                container.appendChild(img);
            } else if (file.type === "video") {
                const vid = document.createElement("video");
                vid.src = file.url;
                vid.controls = true;
                vid.preload = "metadata"; // 优化加载
                container.appendChild(vid);
            }
        } else {
            // 不可预览文件：显示大字体文件名
            const fileNameDiv = document.createElement("div");
            fileNameDiv.textContent = file.filename;
            fileNameDiv.title = file.filename;
            fileNameDiv.className = "full-filename";
            container.appendChild(fileNameDiv);
        }
    }

    /**
     * 添加文件信息
     * @param {HTMLElement} container - 容器元素
     * @param {Object} file - 文件对象
     */
    function addFileInfo(container, file) {
        const fileInfoDiv = document.createElement("div");
        fileInfoDiv.className = "file-info";
        container.appendChild(fileInfoDiv);

        const isPreviewable = file.type === "text" || file.type === "image" || file.type === "video";

        if (isPreviewable) {
            // 可预览文件：显示小字体文件名
            const filenameSpan = document.createElement("span");
            filenameSpan.textContent = file.filename;
            filenameSpan.title = file.filename;
            filenameSpan.className = "filename";
            fileInfoDiv.appendChild(filenameSpan);
        } else {
            // 不可预览文件：显示文件扩展名
            const fileExt = extractFileExtension(file.filename);
            const fileExtDiv = document.createElement("div");
            fileExtDiv.textContent = fileExt;
            fileExtDiv.className = "file-ext";
            fileInfoDiv.appendChild(fileExtDiv);
        }

        // 文件大小和时间
        const sizeTimeDiv = document.createElement("small");
        sizeTimeDiv.textContent = formatFileSize(file.size) + " · " + file.time;
        fileInfoDiv.appendChild(sizeTimeDiv);
        sizeTimeDiv.className = "file-meta";
        sizeTimeDiv.textContent = "";

        const fileSizeSpan = document.createElement("span");
        fileSizeSpan.className = "file-meta-size";
        fileSizeSpan.textContent = formatFileSize(file.size);

        const fileTimeSpan = document.createElement("span");
        fileTimeSpan.className = "file-meta-time";
        fileTimeSpan.textContent = file.time;

        sizeTimeDiv.appendChild(fileSizeSpan);
        sizeTimeDiv.appendChild(fileTimeSpan);
    }

    /**
     * 提取文件扩展名
     * @param {string} filename - 文件名
     * @returns {string} 扩展名
     */
    function extractFileExtension(filename) {
        const hasExtension = filename.includes('.');
        if (hasExtension) {
            let ext = filename.split('.').pop().toUpperCase();
            return ext.length <= 12 ? ext : ext.substring(0, 12);
        }
        return 'UNKNOWN';
    }

    /**
     * 添加删除按钮（仅管理员）
     * @param {HTMLElement} container - 容器元素
     * @param {Object} file - 文件对象
     * @param {HTMLElement} linkElement - 链接元素（用于删除后移除）
     */
    function addDeleteButton(container, file, linkElement) {
        const deleteBtn = document.createElement('button');
        deleteBtn.className = 'delete-btn';
        deleteBtn.innerHTML = '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><use href="/images/icons.svg#ico-trash"/></svg>';
        deleteBtn.title = '删除文件';
        deleteBtn.setAttribute('aria-label', '删除文件: ' + escapeHtml(file.filename));

        deleteBtn.onclick = (e) => {
            e.stopPropagation();
            e.preventDefault();
            // 存储空间根的相对路径(两个列表视图均返回此字段),唯一标识文件,可区分不同目录下的同名文件
            // storage-root-relative path (returned by both list views); uniquely identifies the file, disambiguating same-name files in different dirs
            const filePath = file.path;
            Notify.confirm({
                title: '确认删除',
                content: `确定要删除文件：` +
                    `<div style="margin-top:8px;display:flex;align-items:center;flex-wrap:wrap;">` +
                    `<span class="storage-chip"><svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><use href="/images/icons.svg#ico-database"/></svg>${escapeHtml(window.currentStorageSpace || '存储空间')}</span>` +
                    `<span style="word-break:break-all;min-width:0;">/${escapeHtml(filePath)}</span>` +
                    `</div>`,
                okType: 'danger'
            }).then(result => {
                if (result) {
                    deleteFile(filePath, linkElement);
                }
            });
        };

        container.appendChild(deleteBtn);
    }

    function deleteFile(filePath, element) {
        fetch(`/delete_file?path=${encodeURIComponent(filePath)}`, {
            method: 'DELETE',
            headers: {
                'Content-Type': 'application/json'
            }
        })
            .then(response => {
                if (response.ok) {
                    Notify.success('删除成功！');
                    if (typeof currentView !== 'undefined' && currentView === 'directory') fetchDir();
                    else fetchFiles();
                } else {
                    response.text().then(text => {
                        if (text) {
                            Notify.error('删除失败!\n' + text);
                        } else {
                            Notify.error('删除失败!');
                        }
                    }).catch(() => Notify.error('删除失败!'));
                }
            })
            .catch(err => console.error('删除错误:', err));
    }

    // ============== 目录浏览视图 / Directory browsing view ==============
    let currentView = 'recent';     // 'recent' | 'directory'
    let dirPath = [];                // 当前目录层级(文件夹名数组,相对存储根)

    document.getElementById('tab-recent').addEventListener('click', () => switchView('recent'));
    document.getElementById('tab-dir').addEventListener('click', () => switchView('directory'));

    function switchView(v) {
        resetPagination();
        currentView = v;
        saveCurrentBrowsingState();
        document.getElementById('tab-recent').classList.toggle('active', v === 'recent');
        document.getElementById('tab-dir').classList.toggle('active', v === 'directory');
        document.getElementById('recent-view').style.display = v === 'recent' ? '' : 'none';
        document.getElementById('dir-view').style.display = v === 'directory' ? '' : 'none';
        if (v === 'directory') fetchDir();
        else fetchFiles();
    }

    function fetchDir() {
        resetPagination();
        const p = dirPath.join('/');
        renderBreadcrumb();
        const params = new URLSearchParams({path: p, limit: String(PAGE_SIZE), offset: '0'});
        handleFetch('/list_dir?' + params.toString())
            .then(async r => {
                if (!r.ok) {
                    const error = new Error(await r.text() || '目录列表请求失败');
                    error.status = r.status;
                    throw error;
                }
                return r.json();
            })
            .then(data => {
                if (p !== dirPath.join('/')) return; // 已导航到其他目录则丢弃 / stale response
                currentOffset = (data.files ? data.files.length : 0);
                currentHasMore = !!data.hasMore;
                renderDir(data);
                if (currentHasMore) showSentinel('dir');
            })
            .catch(err => {
                if (p && (err.status === 400 || err.status === 404) && p === dirPath.join('/')) {
                    dirPath = [];
                    saveCurrentBrowsingState();
                    fetchDir();
                    return;
                }
                console.error('list_dir failed:', err);
            });
    }

    function renderBreadcrumb() {
        const bc = document.getElementById('dir-breadcrumb');
        bc.innerHTML = '';
        const root = document.createElement('span');
        root.className = 'crumb storage-root-crumb';
        root.innerHTML = '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><use href="/images/icons.svg#ico-database"/></svg>';
        const rootName = document.createElement('span');
        rootName.textContent = window.currentStorageSpace || '存储空间';
        root.appendChild(rootName);
        root.onclick = () => { dirPath = []; saveCurrentBrowsingState(); fetchDir(); };
        bc.appendChild(root);
        dirPath.forEach((part, i) => {
            const sep = document.createElement('span'); sep.className = 'crumb-sep'; sep.textContent = '/';
            bc.appendChild(sep);
            const c = document.createElement('span'); c.className = 'crumb'; c.textContent = part;
            c.onclick = () => { dirPath = dirPath.slice(0, i + 1); saveCurrentBrowsingState(); fetchDir(); };
            bc.appendChild(c);
        });
    }

    function renderDir(data) {
        const c = document.getElementById('dirContent');
        c.innerHTML = '';
        dirFileGridEl = null; // innerHTML 已销毁旧网格，清空缓存 / clear cached grid ref
        const folders = data.folders || [];
        const files = data.files || [];
        if (folders.length === 0 && files.length === 0 && !currentHasMore) {
            c.innerHTML = '<div class="dir-empty"><svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><use href="/images/icons.svg#ico-folder"/></svg> 空文件夹</div>';
            return;
        }
        folders.forEach(f => {
            const card = document.createElement('div');
            card.className = 'folder-card';
            const ic = document.createElement('span'); ic.className = 'folder-icon'; ic.innerHTML = '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><use href="/images/icons.svg#ico-folder"/></svg>';
            const nm = document.createElement('span'); nm.className = 'folder-name'; nm.textContent = f.name; nm.title = f.name;
            card.appendChild(ic); card.appendChild(nm);
            card.onclick = () => { dirPath.push(f.name); saveCurrentBrowsingState(); fetchDir(); };
            if (window.currentRole === 'admin' || window.currentRole === 'manager') appendFolderOps(card, f.name);
            c.appendChild(card);
        });
        // 文件:网格排列,与"最近上传"一致 / files in a grid matching the recent view
        if (files.length > 0) {
            const fileGrid = document.createElement('div');
            fileGrid.className = 'dir-files';
            files.forEach(f => {
                const el = createFileElement(f);
                if (el) fileGrid.appendChild(el);
            });
            c.appendChild(fileGrid);
            dirFileGridEl = fileGrid; // 缓存网格引用，供 loadMoreDir 追加 / cache for append
        }
    }

    function appendFolderOps(card, name) {
        const ops = document.createElement('span');
        ops.className = 'folder-ops';
        const rename = document.createElement('button');
        rename.className = 'mini-btn';
        rename.innerHTML = '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><use href="/images/icons.svg#ico-pencil"/></svg>';
        rename.title = '重命名文件夹';
        rename.setAttribute('aria-label', '重命名文件夹 ' + name);
        rename.onclick = (e) => {
            e.stopPropagation();
            openRenameFolderModal(name);
        };
        const del = document.createElement('button');
        del.className = 'mini-btn'; del.innerHTML = '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><use href="/images/icons.svg#ico-trash"/></svg>'; del.title = '删除文件夹';
        del.onclick = (e) => {
            e.stopPropagation();
            const rel = [...dirPath, name].join('/');
            Notify.confirm({
                title: '确认删除文件夹', content: '将递归删除 "' + rel + '" 及其所有内容,不可恢复。', okType: 'danger'
            }).then(ok => {
                if (!ok) return;
                handleFetch('/delete_folder?path=' + encodeURIComponent(rel), {method: 'DELETE'})
                    .then(r => r.ok ? fetchDir() : r.text().then(t => Notify.error(t || '删除失败')));
            });
        };
        ops.appendChild(rename);
        ops.appendChild(del);
        card.appendChild(ops);
    }

    function openRenameFolderModal(oldName) {
        if (document.getElementById('renameFolderModal')) return;
        const overlay = document.createElement('div');
        overlay.id = 'renameFolderModal';
        overlay.className = 'modal-overlay';
        overlay.innerHTML = `
            <div class="notify-modal" role="dialog" aria-modal="true" aria-labelledby="renameFolderTitle">
                <div class="modal-header"><h3 id="renameFolderTitle" class="modal-title">重命名文件夹</h3></div>
                <div class="modal-body">
                    <label for="renameFolderName" style="display:block;margin-bottom:8px;color:var(--text-primary);font-size:14px;">新名称</label>
                    <input id="renameFolderName" class="input-field" type="text" maxlength="255"
                           autocomplete="off" />
                    <div id="renameFolderError" style="display:none;margin-top:8px;color:var(--danger);font-size:12px;"></div>
                </div>
                <div class="modal-footer">
                    <button type="button" class="modal-btn modal-btn-default" id="renameFolderCancel">取消</button>
                    <button type="button" class="modal-btn modal-btn-primary" id="renameFolderSubmit">保存</button>
                </div>
            </div>`;
        document.body.appendChild(overlay);

        const input = overlay.querySelector('#renameFolderName');
        const errorEl = overlay.querySelector('#renameFolderError');
        const submitBtn = overlay.querySelector('#renameFolderSubmit');
        input.value = oldName;
        const close = () => {
            document.removeEventListener('keydown', onKeydown);
            overlay.remove();
        };
        const onKeydown = event => { if (event.key === 'Escape') close(); };
        const submit = async () => {
            const newName = input.value.trim();
            if (!newName) {
                errorEl.textContent = '请输入文件夹名称';
                errorEl.style.display = 'block';
                input.focus();
                return;
            }
            if (newName === oldName) {
                close();
                return;
            }
            if (newName.includes('/') || newName.includes('\\')) {
                errorEl.textContent = '文件夹名称不能包含 / 或 \\';
                errorEl.style.display = 'block';
                return;
            }

            submitBtn.disabled = true;
            submitBtn.textContent = '保存中…';
            const path = [...dirPath, oldName].join('/');
            try {
                const response = await handleFetch('/rename_folder', {
                    method: 'POST', headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({path, newName})
                });
                if (!response.ok) throw new Error(await response.text() || '重命名失败');
                close();
                fetchDir();
                Notify.success('文件夹已重命名');
            } catch (error) {
                errorEl.textContent = error.message || '重命名失败';
                errorEl.style.display = 'block';
                submitBtn.disabled = false;
                submitBtn.textContent = '保存';
            }
        };
        overlay.addEventListener('click', event => { if (event.target === overlay) close(); });
        overlay.querySelector('#renameFolderCancel').addEventListener('click', close);
        submitBtn.addEventListener('click', submit);
        input.addEventListener('keydown', event => {
            if (event.key === 'Enter') { event.preventDefault(); submit(); }
        });
        document.addEventListener('keydown', onKeydown);
        requestAnimationFrame(() => { input.focus(); input.select(); });
    }

    document.getElementById('newFolderBtn').addEventListener('click', openCreateFolderModal);

    function openCreateFolderModal() {
        if (document.getElementById('createFolderModal')) return;

        const overlay = document.createElement('div');
        overlay.id = 'createFolderModal';
        overlay.className = 'modal-overlay';
        overlay.innerHTML = `
            <div class="notify-modal" role="dialog" aria-modal="true" aria-labelledby="createFolderTitle">
                <div class="modal-header">
                    <h3 id="createFolderTitle" class="modal-title">新建文件夹</h3>
                </div>
                <div class="modal-body">
                    <div style="margin-bottom:16px;padding:10px 12px;border-radius:7px;background:var(--primary-light);color:var(--text-secondary);font-size:13px;">
                        将在 <strong id="newFolderParentPath" style="color:var(--primary);word-break:break-all;"></strong> 目录下新建文件夹
                    </div>
                    <label for="newFolderName" style="display:block;margin-bottom:8px;color:var(--text-primary);font-size:14px;">文件夹名称</label>
                    <input id="newFolderName" class="input-field" type="text" maxlength="255"
                           placeholder="例如：项目 A 或 项目 A/资料"
                           autocomplete="off" />
                    <div style="margin-top:8px;color:var(--text-muted);font-size:12px;">可以使用“/”一次创建多级文件夹</div>
                    <div id="newFolderError" style="display:none;margin-top:8px;color:var(--danger);font-size:12px;"></div>
                </div>
                <div class="modal-footer">
                    <button type="button" class="modal-btn modal-btn-default" id="newFolderCancel">取消</button>
                    <button type="button" class="modal-btn modal-btn-primary" id="newFolderSubmit">创建</button>
                </div>
            </div>`;
        document.body.appendChild(overlay);

        const input = overlay.querySelector('#newFolderName');
        const parentPathEl = overlay.querySelector('#newFolderParentPath');
        const errorEl = overlay.querySelector('#newFolderError');
        const submitBtn = overlay.querySelector('#newFolderSubmit');
        parentPathEl.textContent = dirPath.length ? '/' + dirPath.join('/') + '/' : '/';
        const close = () => {
            document.removeEventListener('keydown', onKeydown);
            overlay.remove();
        };
        const onKeydown = (event) => {
            if (event.key === 'Escape') close();
        };
        const submit = () => {
            const name = input.value.trim();
            if (!name) {
                errorEl.textContent = '请输入文件夹名称';
                errorEl.style.display = 'block';
                input.focus();
                return;
            }

            errorEl.style.display = 'none';
            submitBtn.disabled = true;
            submitBtn.textContent = '创建中…';
            const full = dirPath.length ? dirPath.join('/') + '/' + name : name;
            handleFetch('/create_folder', {
                method: 'POST', headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({path: full})
            }).then(async r => {
                if (!r.ok) throw new Error(await r.text() || '创建失败');
                close();
                fetchDir();
                Notify.success('文件夹创建成功');
            }).catch(error => {
                errorEl.textContent = error.message || '创建失败';
                errorEl.style.display = 'block';
                submitBtn.disabled = false;
                submitBtn.textContent = '创建';
            });
        };

        overlay.addEventListener('click', event => { if (event.target === overlay) close(); });
        overlay.querySelector('#newFolderCancel').addEventListener('click', close);
        submitBtn.addEventListener('click', submit);
        input.addEventListener('keydown', event => {
            if (event.key === 'Enter') {
                event.preventDefault();
                submit();
            }
        });
        document.addEventListener('keydown', onKeydown);
        requestAnimationFrame(() => input.focus());
    }

    initInfiniteScroll();
