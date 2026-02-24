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
      <div className="app-card league-page-card">
        <h3 className="league-page-title">리그 운영</h3>
        <p className="league-page-empty">로그인 후 공용 리그 상태와 랭킹을 확인할 수 있습니다.</p>
      </div>
    );
  }

  const top3 = (rankings || []).slice(0, 3);

  return (
    <div className="app-card league-page-card">
      <div className="league-page-header">
        <h3 className="league-page-title">리그 운영</h3>
        <div className="league-page-badge">{leagueState?.running ? "진행 중" : "정지"}</div>
      </div>

      <div className="league-grid">
        <InfoCard label="리그 코드" value={leagueState?.leagueCode || "MAIN"} />
        <InfoCard label="공용 기준일" value={leagueState?.currentDate || "-"} />
        <InfoCard label="리그 시작일" value={leagueState?.anchorDate || "-"} />
        <InfoCard label="틱" value={`${leagueState?.tickSeconds || 60}초`} />
        <InfoCard label="진행 단위" value={`${leagueState?.stepDays || 1}일`} />
        <InfoCard label="표시 기준" value="공용 리그 날짜" />
      </div>

      <div className="app-card league-top-card">
        <div className="league-top-title">Top 3 랭킹</div>
        {rankingsLoading && <div className="league-muted">불러오는 중...</div>}
        {!rankingsLoading && top3.length === 0 && <div className="league-muted">랭킹 데이터가 없습니다.</div>}
        {!rankingsLoading && top3.length > 0 && (
          <div className="league-top-list">
            {top3.map((row) => (
              <div key={`league-top-${row.userId}`} className="league-top-row">
                <div className="league-rank-no">#{row.rank}</div>
                <div className="league-rank-name">{row.userName}{row.me ? " (나)" : ""}</div>
                <div className={`league-rank-rate ${Number(row.returnRate) >= 0 ? "up" : "down"}`}>
                  {Number(row.returnRate) > 0 ? "+" : ""}
                  {Number(row.returnRate).toFixed(2)}%
                </div>
                <div className="league-rank-total">{fmt(Number(row.totalValue))}원</div>
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
    <div className="app-card league-info-card">
      <div className="league-info-label">{label}</div>
      <div className="league-info-value">{value}</div>
    </div>
  );
}
