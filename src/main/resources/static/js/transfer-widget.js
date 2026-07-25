// 实时传输浮窗:仅管理员可见。右下角悬浮,可拖动,点开浮层列出当前上传/下载。
// WebSocket 推送活跃快照(/ws/transfers),初始加载走 /api/admin/active-transfers 兜底。
// Real-time transfer widget (admin only). Bottom-right, draggable, popup lists active transfers.
// Live updates via WebSocket (/ws/transfers); initial load falls back to /api/admin/active-transfers.
(function () {
    'use strict';

    var POSITION_KEY = 'filebox-tx-widget-pos';
    var ARROW_UP = '<svg class="tx-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 19V5"/><path d="M5 12l7-7 7 7"/></svg>';
    var ARROW_DOWN = '<svg class="tx-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 5v14"/><path d="M19 12l-7 7-7-7"/></svg>';

    function init() {
        fetch('/api/user', { credentials: 'same-origin' })
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function (data) {
                if (!data) return;
                if ((data.role || '').toUpperCase() !== 'ADMIN') return; // 仅管理员 / admin only
                mount();
            })
            .catch(function () {});
    }

    function mount() {
        injectStyle();
        var root = document.createElement('div');
        root.className = 'tx-widget';
        root.innerHTML =
            '<button class="tx-fab" type="button" title="传输状态(可拖动)">' +
                '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><use href="/images/icons.svg#ico-history"/></svg>' +
                '<span class="tx-count"></span>' +
            '</button>' +
            '<div class="tx-panel" hidden>' +
                '<div class="tx-panel-head"><span>当前传输</span></div>' +
                '<div class="tx-list"></div>' +
            '</div>';
        document.body.appendChild(root);

        var fab = root.querySelector('.tx-fab');
        var panel = root.querySelector('.tx-panel');
        var list = root.querySelector('.tx-list');
        var countEl = root.querySelector('.tx-count');

        restorePosition(root);

        fab.addEventListener('click', function () {
            if (fab.getAttribute('data-dragged') === '1') { fab.removeAttribute('data-dragged'); return; }
            panel.hidden = !panel.hidden;
        });

        makeDraggable(root, fab);

        // 点浮窗以外的地方自动收起面板(点图标本身由上面的 fab click 处理,不会冲突)
        // click outside the widget closes the panel (the fab click above handles the icon itself)
        document.addEventListener('click', function (e) {
            if (!panel.hidden && !root.contains(e.target)) {
                panel.hidden = true;
            }
        });

        function render(transfers) {
            var items = transfers || [];
            countEl.textContent = items.length > 0 ? String(items.length) : '';
            countEl.style.display = items.length > 0 ? '' : 'none';
            fab.classList.toggle('busy', items.length > 0);
            list.innerHTML = '';
            if (items.length === 0) {
                list.innerHTML = '<div class="tx-empty">当前没有正在进行的传输</div>';
                return;
            }
            items.forEach(function (t) {
                var row = document.createElement('div');
                row.className = 'tx-row';
                row.setAttribute('data-start', t.startedAtMillis);
                var dir = document.createElement('span');
                dir.className = 'tx-dir ' + (t.direction === 'UPLOAD' ? 'up' : 'down');
                dir.innerHTML = t.direction === 'UPLOAD' ? ARROW_UP : ARROW_DOWN;
                var file = document.createElement('span');
                file.className = 'tx-file';
                file.textContent = t.file || '';
                file.title = t.file || '';
                var meta = document.createElement('span');
                meta.className = 'tx-meta';
                meta.setAttribute('data-user', t.user || '');
                meta.setAttribute('data-space', t.space || '');
                row.appendChild(dir);
                row.appendChild(file);
                row.appendChild(meta);
                list.appendChild(row);
            });
            tickElapsed();
        }

        function tickElapsed() {
            var now = Date.now();
            var rows = list.querySelectorAll('.tx-row');
            for (var i = 0; i < rows.length; i++) {
                var start = parseInt(rows[i].getAttribute('data-start'), 10);
                var sec = Math.max(0, Math.floor((now - start) / 1000));
                var meta = rows[i].querySelector('.tx-meta');
                meta.textContent = meta.getAttribute('data-user') + ' · ' + meta.getAttribute('data-space') + ' · ' + sec + 's';
            }
        }
        setInterval(tickElapsed, 1000);

        // 初始加载兜底,然后 WebSocket 实时推送 / initial load, then live WS pushes
        fetch('/api/admin/active-transfers', { credentials: 'same-origin' })
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function (d) { if (d) render(d.transfers); })
            .catch(function () {});
        connectWs(render);
    }

    function connectWs(render) {
        var url = (location.protocol === 'https:' ? 'wss:' : 'ws:') + '//' + location.host + '/ws/transfers';
        var ws;
        function open() {
            try {
                ws = new WebSocket(url);
            } catch (e) {
                setTimeout(open, 3000);
                return;
            }
            ws.onmessage = function (ev) {
                try { render(JSON.parse(ev.data).transfers); } catch (e) {}
            };
            ws.onclose = function () { setTimeout(open, 3000); }; // 断线重连 / auto-reconnect
            ws.onerror = function () { try { ws.close(); } catch (e) {} };
        }
        open();
    }

    function makeDraggable(root, handle) {
        var dragging = false, sx = 0, sy = 0, ox = 0, oy = 0;
        handle.addEventListener('mousedown', function (e) {
            dragging = true;
            sx = e.clientX; sy = e.clientY;
            var rect = root.getBoundingClientRect();
            ox = rect.left; oy = rect.top;
            e.preventDefault();
        });
        document.addEventListener('mousemove', function (e) {
            if (!dragging) return;
            var nx = ox + (e.clientX - sx);
            var ny = oy + (e.clientY - sy);
            nx = Math.max(8, Math.min(window.innerWidth - root.offsetWidth - 8, nx));
            ny = Math.max(8, Math.min(window.innerHeight - 48, ny));
            root.style.left = nx + 'px';
            root.style.top = ny + 'px';
            root.style.right = 'auto';
            root.style.bottom = 'auto';
            if (Math.abs(e.clientX - sx) > 4 || Math.abs(e.clientY - sy) > 4) {
                handle.setAttribute('data-dragged', '1');
            }
        });
        document.addEventListener('mouseup', function () { dragging = false; });
    }

    function restorePosition(root) {
        try {
            var pos = JSON.parse(localStorage.getItem(POSITION_KEY) || 'null');
            if (pos && typeof pos.x === 'number') {
                root.style.left = pos.x + 'px';
                root.style.top = pos.y + 'px';
                root.style.right = 'auto';
                root.style.bottom = 'auto';
            }
        } catch (e) {}
        var t = null;
        new MutationObserver(function () {
            clearTimeout(t);
            t = setTimeout(function () {
                try {
                    var r = root.getBoundingClientRect();
                    localStorage.setItem(POSITION_KEY, JSON.stringify({ x: r.left, y: r.top }));
                } catch (e) {}
            }, 400);
        }).observe(root, { attributes: true, attributeFilter: ['style'] });
    }

    function injectStyle() {
        if (document.getElementById('tx-widget-style')) return;
        var css = [
            '.tx-widget{position:fixed;right:20px;bottom:20px;z-index:9998;font-family:inherit}',
            '.tx-fab{position:relative;width:48px;height:48px;border-radius:50%;border:none;cursor:pointer;background:var(--primary,#4f7cff);color:#fff;box-shadow:0 6px 18px rgba(0,0,0,.22);display:flex;align-items:center;justify-content:center}',
            '.tx-fab .ico{width:22px;height:22px}',
            '.tx-fab.busy{animation:tx-pulse 1.4s infinite}',
            '@keyframes tx-pulse{0%,100%{box-shadow:0 6px 18px rgba(0,0,0,.22)}50%{box-shadow:0 0 0 10px rgba(79,124,255,.18)}}',
            '.tx-count{position:absolute;top:-6px;right:-6px;min-width:18px;height:18px;padding:0 4px;border-radius:9px;background:#ff4d4f;color:#fff;font-size:11px;line-height:18px;text-align:center}',
            '.tx-panel{position:absolute;right:0;bottom:58px;width:300px;max-height:60vh;overflow:auto;background:var(--bg-elevated,#fff);color:var(--text-primary,#222);border:1px solid var(--border,#e5e7eb);border-radius:12px;box-shadow:0 12px 32px rgba(0,0,0,.25);padding:8px}',
            '.tx-panel[hidden]{display:none}',
            '.tx-panel-head{font-size:13px;font-weight:600;padding:4px 6px 8px;border-bottom:1px solid var(--border,#eee);margin-bottom:6px}',
            '.tx-row{display:flex;align-items:center;gap:8px;padding:6px;font-size:12px;border-radius:8px}',
            '.tx-row:hover{background:var(--bg-hover,#f5f7fb)}',
            '.tx-dir{flex:0 0 auto;width:20px;height:20px;border-radius:6px;display:inline-flex;align-items:center;justify-content:center;color:#fff}',
            '.tx-dir.up{background:#22c55e}.tx-dir.down{background:#3b82f6}',
            '.tx-arrow{width:12px;height:12px}',
            '.tx-file{flex:1 1 auto;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}',
            '.tx-meta{flex:0 0 auto;font-size:11px;color:var(--text-secondary,#999);text-align:right;max-width:130px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}',
            '.tx-empty{padding:14px;text-align:center;color:var(--text-secondary,#999);font-size:12px}'
        ].join('');
        var st = document.createElement('style');
        st.id = 'tx-widget-style';
        st.textContent = css;
        document.head.appendChild(st);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
