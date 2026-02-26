import React, { useEffect, useState } from "react";
import axios from "axios";

function headers(authToken) {
  return authToken ? { Authorization: `Bearer ${authToken}` } : {};
}

export default function NotificationsPage({ apiBaseUrl, authToken, isLoggedIn, fmtDateTime, navigateTo }) {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const [readFilter, setReadFilter] = useState("ALL");
  const [typeFilter, setTypeFilter] = useState("ALL");

  const load = async () => {
    if (!isLoggedIn || !authToken) return;
    setLoading(true);
    try {
      const res = await axios.get(`${apiBaseUrl}/api/notifications`, { headers: headers(authToken) });
      setRows(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      setMessage(err?.response?.data?.message || err?.message || "알림 조회 실패");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoggedIn, authToken, apiBaseUrl]);

  if (!isLoggedIn) {
    return <div className="app-card"><h3>알림</h3><p>로그인 후 확인할 수 있습니다.</p></div>;
  }

  const typeOptions = Array.from(new Set((rows || []).map((n) => String(n.type || "")).filter(Boolean)));
  const filteredRows = (rows || []).filter((n) => {
    if (readFilter === "UNREAD" && n.read) return false;
    if (readFilter === "READ" && !n.read) return false;
    if (typeFilter !== "ALL" && n.type !== typeFilter) return false;
    return true;
  });

  const markRead = async (id) => {
    await axios.post(`${apiBaseUrl}/api/notifications/${id}/read`, {}, { headers: headers(authToken) });
    await load();
  };
  const markAllRead = async () => {
    await axios.post(`${apiBaseUrl}/api/notifications/read-all`, {}, { headers: headers(authToken) });
    await load();
  };

  const openNotificationTarget = async (n) => {
    if (!n) return;
    if (!n.read) {
      try {
        await axios.post(`${apiBaseUrl}/api/notifications/${n.id}/read`, {}, { headers: headers(authToken) });
      } catch {}
    }
    if (n.refType === "CHALLENGE" && n.refId) {
      navigateTo?.(`/challenges/${n.refId}`);
      return;
    }
    await load();
  };

  return (
    <div className="app-card">
      <div className="app-toolbar-row" style={{ justifyContent: "space-between", alignItems: "center" }}>
        <h3 style={{ margin: 0 }}>알림 센터</h3>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <select value={readFilter} onChange={(e) => setReadFilter(e.target.value)}>
            <option value="ALL">전체</option>
            <option value="UNREAD">안읽음</option>
            <option value="READ">읽음</option>
          </select>
          <select value={typeFilter} onChange={(e) => setTypeFilter(e.target.value)}>
            <option value="ALL">전체 타입</option>
            {typeOptions.map((t) => <option key={`type-${t}`} value={t}>{t}</option>)}
          </select>
          <button type="button" onClick={markAllRead}>전체 읽음</button>
          <button type="button" onClick={load} disabled={loading}>새로고침</button>
        </div>
      </div>
      {message && <div className="sim-trade-message" style={{ marginTop: 8 }}>{message}</div>}
      <div style={{ display: "grid", gap: 8, marginTop: 12 }}>
        {loading && <div>불러오는 중...</div>}
        {!loading && filteredRows.length === 0 && <div>조건에 맞는 알림이 없습니다.</div>}
        {filteredRows.map((n) => (
          <div
            key={`noti-${n.id}`}
            style={{ border: "1px solid #e2e8f0", borderRadius: 10, padding: 12, background: n.read ? "#fff" : "#f8fafc", cursor: n.refId ? "pointer" : "default" }}
            onClick={() => openNotificationTarget(n)}
            role={n.refId ? "button" : undefined}
            tabIndex={n.refId ? 0 : undefined}
            onKeyDown={(e) => {
              if (!n.refId) return;
              if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                openNotificationTarget(n);
              }
            }}
          >
            <div style={{ display: "flex", justifyContent: "space-between", gap: 8 }}>
              <strong style={{ display: "flex", alignItems: "center", gap: 6 }}>
                <span aria-hidden="true">{notificationIcon(n.type)}</span>
                <span>{n.title}</span>
              </strong>
              <span style={{ fontSize: 12, color: "#64748b" }}>{fmtDateTime?.(n.createdAt) || n.createdAt}</span>
            </div>
            <div style={{ marginTop: 6 }}>{n.body}</div>
            <div style={{ marginTop: 8, display: "flex", gap: 8, alignItems: "center" }}>
              <span style={{ fontSize: 12, color: "#64748b" }}>{n.type}</span>
              {!n.read && <button type="button" onClick={(e) => { e.stopPropagation(); markRead(n.id); }}>읽음 처리</button>}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function notificationIcon(type) {
  switch (String(type || "").toUpperCase()) {
    case "CHALLENGE_CREATED":
      return "🎯";
    case "CHALLENGE_JOINED":
      return "✅";
    case "CHALLENGE_NEW_PARTICIPANT":
      return "👥";
    case "CHALLENGE_FEED_POST":
      return "💬";
    default:
      return "🔔";
  }
}
