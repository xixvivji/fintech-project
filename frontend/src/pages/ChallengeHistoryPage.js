import React, { useEffect, useMemo, useState } from "react";
import axios from "axios";

function authHeaders(authToken) {
  return authToken ? { Authorization: `Bearer ${authToken}` } : {};
}

export default function ChallengeHistoryPage({ apiBaseUrl, authToken, isLoggedIn, leagueState, fmt, navigateTo }) {
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const [rows, setRows] = useState([]);
  const [leaderboardsById, setLeaderboardsById] = useState({});

  const load = async () => {
    if (!isLoggedIn || !authToken) return;
    setLoading(true);
    try {
      const res = await axios.get(`${apiBaseUrl}/api/challenges`, { headers: authHeaders(authToken) });
      const all = Array.isArray(res.data) ? res.data : [];
      const ended = all.filter((c) => String(c.status || "").toUpperCase() === "ENDED");
      setRows(ended);
      const entries = await Promise.all(
        ended.slice(0, 20).map(async (c) => {
          try {
            const lb = await axios.get(`${apiBaseUrl}/api/challenges/${c.id}/leaderboard`, { headers: authHeaders(authToken) });
            return [String(c.id), Array.isArray(lb.data) ? lb.data : []];
          } catch {
            return [String(c.id), []];
          }
        })
      );
      setLeaderboardsById(Object.fromEntries(entries));
    } catch (err) {
      setMessage(err?.response?.data?.message || err?.message || "히스토리 조회 실패");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoggedIn, authToken, apiBaseUrl]);

  const summary = useMemo(() => {
    const total = rows.length;
    const totalParticipants = rows.reduce((acc, r) => acc + Number(r.participantCount || 0), 0);
    return { total, totalParticipants };
  }, [rows]);

  if (!isLoggedIn) {
    return <div className="app-card"><h3>챌린지 히스토리</h3><p>로그인 후 이용할 수 있습니다.</p></div>;
  }

  return (
    <div style={{ display: "grid", gap: 16 }}>
      <div className="app-card">
        <div className="app-toolbar-row" style={{ justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
          <div>
            <button type="button" className="home-link-btn" onClick={() => navigateTo?.("/challenges")}>← 챌린지 목록</button>
            <h3 style={{ margin: "8px 0 0 0" }}>종료 챌린지 히스토리</h3>
            <div style={{ fontSize: 12, color: "#64748b" }}>
              기준일: <strong>{leagueState?.currentDate || "-"}</strong> · 종료된 챌린지 결과 확정 내역
            </div>
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <button type="button" onClick={load} disabled={loading}>새로고침</button>
          </div>
        </div>
        {message && <div className="sim-trade-message" style={{ marginTop: 8 }}>{message}</div>}
      </div>

      <div style={{ display: "grid", gap: 12, gridTemplateColumns: "repeat(auto-fit, minmax(180px,1fr))" }}>
        <StatCard label="종료 챌린지" value={`${summary.total}개`} />
        <StatCard label="누적 참여자(중복포함)" value={`${summary.totalParticipants}명`} />
      </div>

      <div style={{ display: "grid", gap: 12 }}>
        {loading && <div className="app-card">불러오는 중...</div>}
        {!loading && rows.length === 0 && <div className="app-card">종료된 챌린지가 없습니다.</div>}
        {rows.map((c) => {
          const lb = leaderboardsById[String(c.id)] || [];
          const top3 = lb.slice(0, 3);
          const achievedCount = lb.filter((r) => r.achieved).length;
          return (
            <div key={`history-${c.id}`} className="app-card" style={{ display: "grid", gap: 10 }}>
              <div style={{ display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
                <div style={{ display: "grid", gap: 4 }}>
                  <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                    <strong>{c.title}</strong>
                    <span style={{ padding: "2px 8px", borderRadius: 999, background: "#eef2ff", color: "#4338ca", fontSize: 12, fontWeight: 700 }}>결과 확정</span>
                  </div>
                  <div style={{ fontSize: 12, color: "#64748b" }}>
                    기간 {c.startDate} ~ {c.endDate} · 목표 {Number(c.targetValue || 0).toFixed(1)}% · 참여 {c.participantCount}명 · 달성 {achievedCount}명
                  </div>
                  {c.description && <div style={{ fontSize: 13, color: "#475569" }}>{c.description}</div>}
                </div>
                <button type="button" onClick={() => navigateTo?.(`/challenges/${c.id}`)}>상세 보기</button>
              </div>

              <div style={{ display: "grid", gap: 8 }}>
                <div style={{ fontWeight: 700, fontSize: 13 }}>Top 3 결과</div>
                {top3.length === 0 && <div style={{ color: "#64748b" }}>리더보드 데이터가 없습니다.</div>}
                {top3.map((r) => (
                  <div key={`history-top-${c.id}-${r.userId}`} style={{ display: "grid", gridTemplateColumns: "72px minmax(0,1fr) 120px 120px", gap: 8, alignItems: "center", border: "1px solid #e2e8f0", borderRadius: 8, padding: 8 }}>
                    <div style={{ fontWeight: 700 }}>#{r.rank}</div>
                    <div>{r.userName}{r.me ? " (나)" : ""}</div>
                    <div className={Number(r.returnRate) >= 0 ? "up num" : "down num"}>
                      {Number(r.returnRate) > 0 ? "+" : ""}{Number(r.returnRate || 0).toFixed(2)}%
                    </div>
                    <div className={Number(r.achievementRate) >= 100 ? "up num" : "down num"}>
                      {Number(r.achievementRate || 0).toFixed(1)}%
                    </div>
                  </div>
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function StatCard({ label, value }) {
  return (
    <div className="app-card" style={{ padding: 12 }}>
      <div style={{ fontSize: 12, color: "#64748b" }}>{label}</div>
      <div style={{ fontWeight: 800, fontSize: 18 }}>{value}</div>
    </div>
  );
}
