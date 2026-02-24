import React, { useEffect } from "react";

export default function LeaguePage({
  isLoggedIn,
  leagueState,
  rankings,
  rankingsLoading,
  loadRankings,
  fmt,
}) {
  useEffect(() => {
    if (isLoggedIn) loadRankings?.();
  }, [isLoggedIn, loadRankings]);

  if (!isLoggedIn) {
    return (
      <div className="app-card" style={cardStyle}>
        <h3 style={{ marginTop: 0 }}>리그 운영</h3>
        <p style={{ margin: 0, color: "#64748b" }}>로그인 후 공용 리그 상태와 랭킹을 확인할 수 있습니다.</p>
      </div>
    );
  }

  const top3 = (rankings || []).slice(0, 3);

  return (
    <div className="app-card" style={cardStyle}>
      <h3 style={{ marginTop: 0, marginBottom: 12 }}>리그 운영</h3>

      <div style={gridStyle}>
        <InfoCard label="리그 코드" value={leagueState?.leagueCode || "MAIN"} />
        <InfoCard label="상태" value={leagueState?.running ? "진행 중" : "정지"} />
        <InfoCard label="공용 기준일" value={leagueState?.currentDate || "-"} />
        <InfoCard label="리그 시작일" value={leagueState?.anchorDate || "-"} />
        <InfoCard label="틱" value={`${leagueState?.tickSeconds || 60}초`} />
        <InfoCard label="진행 단위" value={`${leagueState?.stepDays || 1}일`} />
      </div>

      <div className="app-card" style={{ marginTop: 16, padding: 12, border: "1px solid #e5e7eb" }}>
        <div style={{ fontWeight: 800, marginBottom: 8 }}>Top 3 랭킹</div>
        {rankingsLoading && <div style={{ color: "#64748b" }}>불러오는 중...</div>}
        {!rankingsLoading && top3.length === 0 && <div style={{ color: "#64748b" }}>랭킹 데이터가 없습니다.</div>}
        {!rankingsLoading && top3.length > 0 && (
          <div style={{ display: "grid", gap: 8 }}>
            {top3.map((row) => (
              <div key={`league-top-${row.userId}`} style={topRowStyle}>
                <div style={{ minWidth: 30, fontWeight: 800 }}>#{row.rank}</div>
                <div style={{ flex: 1 }}>{row.userName}{row.me ? " (나)" : ""}</div>
                <div style={{ fontWeight: 700, color: Number(row.returnRate) >= 0 ? "#dc2626" : "#0f766e" }}>
                  {Number(row.returnRate) > 0 ? "+" : ""}{Number(row.returnRate).toFixed(2)}%
                </div>
                <div style={{ color: "#475569", fontSize: 12 }}>{fmt(Number(row.totalValue))}원</div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function InfoCard({ label, value }) {
  return (
    <div className="app-card" style={{ padding: 12, border: "1px solid #e5e7eb" }}>
      <div style={{ color: "#64748b", fontSize: 12, marginBottom: 4 }}>{label}</div>
      <div style={{ fontWeight: 800 }}>{value}</div>
    </div>
  );
}

const cardStyle = {
  padding: 16,
  borderRadius: 12,
  border: "1px solid #e5e7eb",
  background: "#fff",
};

const gridStyle = {
  display: "grid",
  gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))",
  gap: 10,
};

const topRowStyle = {
  display: "flex",
  alignItems: "center",
  gap: 10,
  padding: "10px 12px",
  borderRadius: 10,
  border: "1px solid #e5e7eb",
  background: "#f8fafc",
};
