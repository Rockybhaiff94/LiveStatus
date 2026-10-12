(function () {
    const token = window.PANEL_SESSION_TOKEN || "";

    function apiFetch(endpoint, options = {}) {
        const headers = options.headers || {};
        headers["Authorization"] = "Bearer " + token;
        headers["Content-Type"] = "application/json";
        return fetch(endpoint, { ...options, headers })
            .then(res => {
                if (res.status === 401) {
                    alert("Your session has expired. Please run /ytlive panel in Minecraft to generate a new link.");
                    window.location.reload();
                    throw new Error("Unauthorized");
                }
                return res.json();
            });
    }

    // Tab Navigation
    const navItems = document.querySelectorAll(".nav-item");
    const tabPanes = document.querySelectorAll(".tab-pane");
    const pageTitle = document.getElementById("page-title");

    navItems.forEach(item => {
        item.addEventListener("click", () => {
            const tabName = item.getAttribute("data-tab");
            navItems.forEach(n => n.classList.remove("active"));
            tabPanes.forEach(p => p.classList.remove("active"));
            item.classList.add("active");
            document.getElementById("tab-" + tabName).classList.add("active");
            pageTitle.textContent = item.textContent.trim();
            loadTabData(tabName);
        });
    });

    function loadTabData(tabName) {
        switch (tabName) {
            case "dashboard": loadDashboard(); break;
            case "youtubers": loadYouTubers(); break;
            case "history": loadHistory(); break;
            case "links": loadLinks(); break;
            case "announcements": loadSettings(); break;
            case "premium": loadPremium(); break;
            case "settings": loadSettings(); break;
            case "audit": loadAudit(); break;
        }
    }

    // Refresh button
    document.getElementById("btn-refresh").addEventListener("click", () => {
        const activeItem = document.querySelector(".nav-item.active");
        if (activeItem) {
            loadTabData(activeItem.getAttribute("data-tab"));
        }
    });

    // Logout / Revoke
    document.getElementById("btn-logout").addEventListener("click", () => {
        if (confirm("Revoke this web panel session? You will need to run /ytlive panel in-game again.")) {
            apiFetch("/api/v1/session/revoke", { method: "POST" })
                .then(() => window.location.reload());
        }
    });

    // 1. Dashboard
    function loadDashboard() {
        apiFetch("/api/v1/dashboard").then(data => {
            document.getElementById("stat-total-youtubers").textContent = data.totalYouTubers || 0;
            document.getElementById("stat-currently-live").textContent = data.currentlyLive || 0;
            document.getElementById("stat-total-events").textContent = data.totalEvents || 0;
            document.getElementById("stat-total-joins").textContent = data.totalLinkJoins || 0;
            document.getElementById("stat-premium-users").textContent = data.premiumUsers || 0;
            if (data.serverStatus) {
                document.getElementById("stat-online-players").textContent = (data.serverStatus.onlinePlayerCount || 0) + " players online";
            }

            const tbody = document.getElementById("dashboard-recent-table");
            tbody.innerHTML = "";
            if (!data.recentEvents || data.recentEvents.length === 0) {
                tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted">No live events recorded yet.</td></tr>';
                return;
            }

            data.recentEvents.forEach(e => {
                const tr = document.createElement("tr");
                const isLive = e.status === "LIVE";
                tr.innerHTML = `
                    <td><strong>${escapeHtml(e.playerName)}</strong></td>
                    <td>${escapeHtml(e.channelTitle || "-")}</td>
                    <td>${escapeHtml(e.streamTitle || "-")}</td>
                    <td>${formatDate(e.startedAt)}</td>
                    <td>${e.endedAt ? formatDate(e.endedAt) : '<span class="text-live">Broadcasting</span>'}</td>
                    <td>${formatDuration(e.durationSeconds)}</td>
                    <td><span class="status-badge ${isLive ? 'live' : 'ended'}">${e.status}</span></td>
                `;
                tbody.appendChild(tr);
            });
        });
    }

    // 2. YouTubers
    function loadYouTubers() {
        apiFetch("/api/v1/youtubers").then(data => {
            const tbody = document.getElementById("youtubers-table");
            tbody.innerHTML = "";
            if (!data.youtubers || data.youtubers.length === 0) {
                tbody.innerHTML = '<tr><td colspan="10" class="text-center text-muted">No YouTubers linked yet. Authorize players using the button above.</td></tr>';
                return;
            }

            data.youtubers.forEach(u => {
                const tr = document.createElement("tr");
                const liveBadge = u.currentlyLive
                    ? '<span class="status-badge live">● LIVE</span>'
                    : '<span class="status-badge offline">Offline</span>';
                const premBadge = u.isPremium
                    ? `<span class="status-badge" style="background:#42300b; color:#f1c40f;">${u.premiumTier || "PREMIUM"}</span>`
                    : '<span class="text-muted">Standard</span>';

                tr.innerHTML = `
                    <td><strong>${escapeHtml(u.playerName)}</strong></td>
                    <td>${escapeHtml(u.channelTitle)}</td>
                    <td><code>${escapeHtml(u.channelId)}</code></td>
                    <td>${liveBadge}</td>
                    <td>${u.totalSessions || 0}</td>
                    <td>${formatDuration(u.totalLiveSeconds || 0)}</td>
                    <td>${u.lastLive !== "-" ? formatDate(u.lastLive) : "-"}</td>
                    <td>${premBadge}</td>
                    <td>
                        <label class="switch">
                            <input type="checkbox" class="toggle-yt-chat" data-uuid="${u.uuid}" ${u.chatEnabled ? 'checked' : ''}>
                            <span class="slider round"></span>
                        </label>
                    </td>
                    <td>
                        <button class="btn-sm btn-danger btn-unlink" data-uuid="${u.uuid}" data-name="${escapeHtml(u.playerName)}">Unlink</button>
                    </td>
                `;
                tbody.appendChild(tr);
            });

            // Bind actions
            document.querySelectorAll(".btn-unlink").forEach(b => {
                b.addEventListener("click", () => {
                    const uuid = b.getAttribute("data-uuid");
                    const name = b.getAttribute("data-name");
                    if (confirm(`Unlink YouTube channel for ${name}?`)) {
                        apiFetch("/api/v1/youtubers/unlink", {
                            method: "POST",
                            body: JSON.stringify({ uuid })
                        }).then(() => loadYouTubers());
                    }
                });
            });

            document.querySelectorAll(".toggle-yt-chat").forEach(c => {
                c.addEventListener("change", () => {
                    const uuid = c.getAttribute("data-uuid");
                    const enabled = c.checked;
                    apiFetch("/api/v1/youtubers/toggle", {
                        method: "POST",
                        body: JSON.stringify({ uuid, enabled })
                    });
                });
            });
        });
    }

    // 3. History
    function loadHistory() {
        apiFetch("/api/v1/history").then(data => {
            const tbody = document.getElementById("history-table");
            tbody.innerHTML = "";
            if (!data.history || data.history.length === 0) {
                tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted">No historical events recorded.</td></tr>';
                return;
            }
            data.history.forEach(e => {
                const tr = document.createElement("tr");
                const isLive = e.status === "LIVE";
                tr.innerHTML = `
                    <td><strong>${escapeHtml(e.playerName)}</strong></td>
                    <td>${escapeHtml(e.channelTitle || "-")}</td>
                    <td><code>${escapeHtml(e.videoId || "-")}</code></td>
                    <td>${escapeHtml(e.streamTitle || "-")}</td>
                    <td>${formatDate(e.startedAt)}</td>
                    <td>${e.endedAt ? formatDate(e.endedAt) : '<span class="text-live">In progress</span>'}</td>
                    <td>${formatDuration(e.durationSeconds)}</td>
                    <td><span class="status-badge ${isLive ? 'live' : 'ended'}">${e.status}</span></td>
                `;
                tbody.appendChild(tr);
            });
        });
    }

    // 4. Server Links
    function loadLinks() {
        apiFetch("/api/v1/links").then(data => {
            document.getElementById("stat-joins-today").textContent = data.joinsToday || 0;
            document.getElementById("stat-joins-week").textContent = data.joinsThisWeek || 0;
            document.getElementById("stat-joins-month").textContent = data.joinsThisMonth || 0;

            const linksBody = document.getElementById("links-table");
            linksBody.innerHTML = "";
            if (data.links) {
                data.links.forEach(l => {
                    const tr = document.createElement("tr");
                    const publicUrl = window.location.origin + "/join/" + encodeURIComponent(l.linkId);
                    tr.innerHTML = `
                        <td><code>${escapeHtml(l.linkId)}</code></td>
                        <td>${escapeHtml(l.label)}</td>
                        <td>${escapeHtml(l.targetAddress)}</td>
                        <td>${l.clicksCount || 0}</td>
                        <td>${l.joinsCount || 0}</td>
                        <td><a href="${publicUrl}" target="_blank" style="color:var(--accent);">${publicUrl}</a></td>
                    `;
                    linksBody.appendChild(tr);
                });
            }

            const joinsBody = document.getElementById("joins-table");
            joinsBody.innerHTML = "";
            if (!data.recentJoins || data.recentJoins.length === 0) {
                joinsBody.innerHTML = '<tr><td colspan="5" class="text-center text-muted">No tracked joins recorded yet.</td></tr>';
            } else {
                data.recentJoins.forEach(j => {
                    const tr = document.createElement("tr");
                    tr.innerHTML = `
                        <td><strong>${escapeHtml(j.playerName)}</strong></td>
                        <td><small>${escapeHtml(j.minecraftUuid)}</small></td>
                        <td>${formatDate(j.joinTime)}</td>
                        <td>${escapeHtml(j.referralSource || "direct")}</td>
                        <td><code>${escapeHtml(j.linkId)}</code></td>
                    `;
                    joinsBody.appendChild(tr);
                });
            }
        });
    }

    // 5. Settings
    function loadSettings() {
        apiFetch("/api/v1/settings").then(data => {
            const s = data.settings || {};
            document.getElementById("setting-youtubeClientId").value = s.youtubeClientId || "";
            document.getElementById("setting-youtubeClientSecret").value = s.youtubeClientSecret || "";
            document.getElementById("setting-liveText").value = s.liveText || "LIVE";
            document.getElementById("setting-liveFormat").value = s.liveFormat || "<red><bold>LIVE</bold></red>";
            document.getElementById("setting-pollingIntervalSeconds").value = s.pollingIntervalSeconds || 30;
            document.getElementById("setting-adminChatEnabled").checked = s.adminChatEnabled !== false;
            document.getElementById("setting-globalMinimumGapSeconds").value = s.globalMinimumGapSeconds || 60;
            document.getElementById("setting-defaultIntervalMinutes").value = s.defaultIntervalMinutes || 15;
        });
    }

    document.getElementById("settings-form").addEventListener("submit", (e) => {
        e.preventDefault();
        const payload = {
            liveText: document.getElementById("setting-liveText").value,
            liveFormat: document.getElementById("setting-liveFormat").value,
            pollingIntervalSeconds: parseInt(document.getElementById("setting-pollingIntervalSeconds").value, 10)
        };
        apiFetch("/api/v1/settings", { method: "POST", body: JSON.stringify(payload) })
            .then(res => alert(res.message || "Settings updated!"));
    });

    document.getElementById("announcements-form").addEventListener("submit", (e) => {
        e.preventDefault();
        const payload = {
            adminChatEnabled: document.getElementById("setting-adminChatEnabled").checked,
            globalMinimumGapSeconds: parseInt(document.getElementById("setting-globalMinimumGapSeconds").value, 10),
            defaultIntervalMinutes: parseInt(document.getElementById("setting-defaultIntervalMinutes").value, 10)
        };
        apiFetch("/api/v1/settings", { method: "POST", body: JSON.stringify(payload) })
            .then(res => alert(res.message || "Announcement settings updated!"));
    });

    // 6. Premium
    function loadPremium() {
        apiFetch("/api/v1/premium").then(data => {
            const tbody = document.getElementById("premium-table");
            tbody.innerHTML = "";
            if (!data.activeUsers || data.activeUsers.length === 0) {
                tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted">No active premium licenses.</td></tr>';
                return;
            }

            data.activeUsers.forEach(u => {
                const tr = document.createElement("tr");
                tr.innerHTML = `
                    <td><strong>${escapeHtml(u.playerName)}</strong></td>
                    <td><small>${escapeHtml(u.minecraftUuid)}</small></td>
                    <td><span class="status-badge" style="background:#42300b; color:#f1c40f;">${escapeHtml(u.tier)}</span></td>
                    <td>${formatDate(u.activatedAt)}</td>
                    <td>${u.expiresAt ? formatDate(u.expiresAt) : '<strong>Lifetime</strong>'}</td>
                    <td>${escapeHtml(u.activationMethod)}</td>
                    <td><code>${escapeHtml(u.referenceId || "-")}</code></td>
                    <td>
                        <button class="btn-sm btn-danger btn-revoke-prem" data-uuid="${u.minecraftUuid}">Revoke</button>
                    </td>
                `;
                tbody.appendChild(tr);
            });

            document.querySelectorAll(".btn-revoke-prem").forEach(b => {
                b.addEventListener("click", () => {
                    const uuid = b.getAttribute("data-uuid");
                    if (confirm("Revoke premium license for this player?")) {
                        apiFetch("/api/v1/premium/revoke", {
                            method: "POST",
                            body: JSON.stringify({ uuid })
                        }).then(() => loadPremium());
                    }
                });
            });
        });
    }

    // Code Redemption
    document.getElementById("redeem-code-form").addEventListener("submit", (e) => {
        e.preventDefault();
        const code = document.getElementById("redeem-code").value;
        const playerName = document.getElementById("redeem-player-name").value;
        const box = document.getElementById("redeem-result");

        apiFetch("/api/v1/premium/redeem", {
            method: "POST",
            body: JSON.stringify({ code, playerName })
        }).then(res => {
            box.style.display = "block";
            if (res.success) {
                box.className = "alert-box alert-success";
                box.textContent = res.message;
                loadPremium();
            } else {
                box.className = "alert-box alert-danger";
                box.textContent = res.error || "Failed to redeem code";
            }
        });
    });

    // Buy Premium
    document.getElementById("purchase-form").addEventListener("submit", (e) => {
        e.preventDefault();
        const playerName = document.getElementById("purchase-player-name").value;
        const tier = document.getElementById("purchase-tier").value;
        const box = document.getElementById("purchase-result");

        apiFetch("/api/v1/premium/purchase", {
            method: "POST",
            body: JSON.stringify({ playerName, tier })
        }).then(res => {
            box.style.display = "block";
            box.className = "alert-box alert-success";
            box.innerHTML = `Order Created: <strong>${res.orderId}</strong>.<br><a href="${res.checkoutUrl}" target="_blank" style="color:var(--accent);">Click here to proceed to Gateway Checkout &rarr;</a>`;
        });
    });

    // 7. Audit
    function loadAudit() {
        apiFetch("/api/v1/audit").then(data => {
            const tbody = document.getElementById("audit-table");
            tbody.innerHTML = "";
            if (!data.auditLogs || data.auditLogs.length === 0) {
                tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted">No audit logs recorded yet.</td></tr>';
                return;
            }
            data.auditLogs.forEach(a => {
                const tr = document.createElement("tr");
                tr.innerHTML = `
                    <td>${formatDate(a.timestamp)}</td>
                    <td><strong>${escapeHtml(a.actor)}</strong></td>
                    <td><code>${escapeHtml(a.ipAddress)}</code></td>
                    <td><code>${escapeHtml(a.action)}</code></td>
                    <td>${escapeHtml(a.target)}</td>
                    <td>${escapeHtml(a.details)}</td>
                `;
                tbody.appendChild(tr);
            });
        });
    }

    // Modal: Allow Player
    const modalAllow = document.getElementById("modal-allow-player");
    document.getElementById("btn-allow-player").addEventListener("click", () => modalAllow.classList.add("show"));
    modalAllow.querySelector(".close-modal").addEventListener("click", () => modalAllow.classList.remove("show"));
    document.getElementById("modal-btn-confirm-allow").addEventListener("click", () => {
        const playerName = document.getElementById("modal-player-name").value.trim();
        if (!playerName) return;
        apiFetch("/api/v1/youtubers/allow", {
            method: "POST",
            body: JSON.stringify({ playerName })
        }).then(() => {
            modalAllow.classList.remove("show");
            document.getElementById("modal-player-name").value = "";
            loadYouTubers();
        });
    });

    // Modal: Create Link
    const modalLink = document.getElementById("modal-create-link");
    document.getElementById("btn-create-link").addEventListener("click", () => modalLink.classList.add("show"));
    modalLink.querySelector(".close-modal").addEventListener("click", () => modalLink.classList.remove("show"));
    document.getElementById("modal-btn-confirm-link").addEventListener("click", () => {
        const linkId = document.getElementById("modal-link-id").value.trim();
        const label = document.getElementById("modal-link-label").value.trim();
        const targetAddress = document.getElementById("modal-link-target").value.trim();
        if (!linkId || !label || !targetAddress) return;
        apiFetch("/api/v1/links/create", {
            method: "POST",
            body: JSON.stringify({ linkId, label, targetAddress })
        }).then(() => {
            modalLink.classList.remove("show");
            document.getElementById("modal-link-id").value = "";
            document.getElementById("modal-link-label").value = "";
            document.getElementById("modal-link-target").value = "";
            loadLinks();
        });
    });

    // Helpers
    function formatDate(isoStr) {
        if (!isoStr) return "-";
        try {
            const d = new Date(isoStr);
            return d.toLocaleDateString() + " " + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        } catch (e) {
            return isoStr;
        }
    }

    function formatDuration(sec) {
        if (!sec || sec <= 0) return "-";
        const h = Math.floor(sec / 3600);
        const m = Math.floor((sec % 3600) / 60);
        if (h > 0) return h + "h " + m + "m";
        return m + "m";
    }

    function escapeHtml(str) {
        if (!str) return "";
        return String(str).replace(/[&<>"']/g, function (m) {
            switch (m) {
                case '&': return '&amp;';
                case '<': return '&lt;';
                case '>': return '&gt;';
                case '"': return '&quot;';
                default: return '&#039;';
            }
        });
    }

    // Auto-refresh loop (every 10s)
    loadDashboard();
    setInterval(() => {
        const active = document.querySelector(".nav-item.active");
        if (active) {
            loadTabData(active.getAttribute("data-tab"));
        }
    }, 10000);
})();
