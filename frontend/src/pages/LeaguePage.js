import React, { useEffect, useMemo, useState } from "react";

const RANKINGS_PAGE_SIZE = 20;

export default function LeaguePage({
  isLoggedIn,
  leagueState,
  rankings,
  rankingsLoading,
  loadRankings,
  fmt,
  getStockNameByCode,
  openRankingUserSummary,
  rankingUserSummary,
  setRankingUserSummary,
  rankingUserSummaryLoading,
  navigateTo,
}) {
  const [rankingQuery, setRankingQuery] = useState("");
  const [rankingPage, setRankingPage] = useState(1);

  useEffect(() => {
    if (isLoggedIn) loadRankings?.();
  }, [isLoggedIn, loadRankings]);

  useEffect(() => {
    setRankingPage(1);
  }, [rankingQuery, rankings]);

  const filteredRankings = useMemo(() => {
    const rows = Array.isArray(rankings) ? rankings : [];
    const q = String(rankingQuery || "").trim().toLowerCase();
    if (!q) return rows;
    return rows.filter((row) => {
      const name = String(row?.userName || "").toLowerCase();
      const rank = String(row?.rank ?? "");
      return name.includes(q) || rank.includes(q);
    });
  }, [rankings, rankingQuery]);

  const totalPages = Math.max(1, Math.ceil(filteredRankings.length / RANKINGS_PAGE_SIZE));
  const currentPage = Math.min(rankingPage, totalPages);
  const pageRows = filteredRankings.slice(
    (currentPage - 1) * RANKINGS_PAGE_SIZE,
    currentPage * RANKINGS_PAGE_SIZE
  );

  if (!isLoggedIn) {
    return (
      <div className="app-card league-page-card">
        <h3 className="league-page-title">리그 운영</h3>
        <p className="league-page-empty">로그인 후 리그 상태와 전체 순위를 확인할 수 있습니다.</p>
      </div>
    );
  }

  return (
    <div className="app-card league-page-card">
      <div className="league-page-header">
        <h3 className="league-page-title">리그 운영</h3>
        <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
          <button type="button" className="home-link-btn" onClick={() => navigateTo?.("/challenges")}>챌린지 보기</button>
          <div className="league-page-badge">{leagueState?.running ? "진행 중" : "일시정지"}</div>
        </div>
      </div>

      <div className="league-grid">
        <InfoCard label="리그 코드" value={leagueState?.leagueCode || "MAIN"} />
        <InfoCard label="기준일" value={leagueState?.currentDate || "-"} />
        <InfoCard label="시작일" value={leagueState?.anchorDate || "-"} />
        <InfoCard label="틱 간격" value={`${leagueState?.tickSeconds || 60}초`} />
        <InfoCard label="진행 단위" value={`${leagueState?.stepDays || 1}일`} />
        <InfoCard label="평가 기준" value="공용 리그 날짜" />
      </div>

      <div className="app-card league-top-card">
        <div className="league-top-title">전체 참여자 순위</div>

        <div className="sim-order-toolbar" style={{ marginBottom: 12 }}>
          <input
            type="text"
            value={rankingQuery}
            onChange={(e) => setRankingQuery(e.target.value)}
            className="sim-order-filter"
            placeholder="닉네임 또는 순위 검색"
            style={{ minWidth: 220 }}
          />
          <div className="league-muted">
            총 {filteredRankings.length}명 | {currentPage}/{totalPages}페이지
          </div>
        </div>

        {rankingsLoading && <div className="league-muted">랭킹을 불러오는 중...</div>}
        {!rankingsLoading && filteredRankings.length === 0 && (
          <div className="league-muted">검색 결과가 없습니다.</div>
        )}
        {!rankingsLoading && filteredRankings.length > 0 && (
          <>
            <div className="sim-order-table-wrap">
              <table className="sim-order-table">
                <colgroup>
                  <col style={{ width: "72px" }} />
                  <col style={{ width: "auto" }} />
                  <col style={{ width: "110px" }} />
                  <col style={{ width: "150px" }} />
                  <col style={{ width: "150px" }} />
                  <col style={{ width: "150px" }} />
                  <col style={{ width: "120px" }} />
                </colgroup>
                <thead>
                  <tr>
                    <th className="num">순위</th>
                    <th>사용자</th>
                    <th className="num">수익률</th>
                    <th className="num">총자산</th>
                    <th className="num">실현손익</th>
                    <th className="num">미실현손익</th>
                    <th>기준일</th>
                  </tr>
                </thead>
                <tbody>
                  {pageRows.map((row) => (
                    <tr key={`league-all-${row.userId}`} className={row.me ? "sim-ranking-me-row" : ""}>
                      <td className="num">{Number(row.rank) || 0}</td>
                      <td style={{ whiteSpace: "nowrap" }}>
                        <button
                          type="button"
                          className="sim-ranking-name-btn"
                          onClick={() => openRankingUserSummary?.(row.userId)}
                          disabled={rankingUserSummaryLoading}
                        >
                          {row.userName}
                          {row.me ? " (나)" : ""}
                        </button>
                      </td>
                      <td className={`num ${Number(row.returnRate) >= 0 ? "up" : "down"}`}>
                        {Number(row.returnRate) > 0 ? "+" : ""}
                        {Number(row.returnRate || 0).toFixed(2)}%
                      </td>
                      <td className="num">{fmt(Number(row.totalValue || 0))}원</td>
                      <td className={`num ${Number(row.realizedPnl) >= 0 ? "up" : "down"}`}>
                        {formatSignedAmount(row.realizedPnl, fmt)}원
                      </td>
                      <td className={`num ${Number(row.unrealizedPnl) >= 0 ? "up" : "down"}`}>
                        {formatSignedAmount(row.unrealizedPnl, fmt)}원
                      </td>
                      <td style={{ whiteSpace: "nowrap" }}>{row.valuationDate || "-"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="sim-order-toolbar" style={{ marginTop: 12 }}>
              <button type="button" onClick={() => setRankingPage(1)} disabled={currentPage <= 1}>
                처음
              </button>
              <button
                type="button"
                onClick={() => setRankingPage((p) => Math.max(1, p - 1))}
                disabled={currentPage <= 1}
              >
                이전
              </button>
              <div className="league-muted">
                {currentPage} / {totalPages}
              </div>
              <button
                type="button"
                onClick={() => setRankingPage((p) => Math.min(totalPages, p + 1))}
                disabled={currentPage >= totalPages}
              >
                다음
              </button>
              <button type="button" onClick={() => setRankingPage(totalPages)} disabled={currentPage >= totalPages}>
                마지막
              </button>
            </div>
          </>
        )}
      </div>

      {rankingUserSummary && (
        <div className="sim-modal-backdrop" role="presentation" onClick={() => setRankingUserSummary?.(null)}>
          <div className="sim-confirm-modal" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
            <div className="sim-confirm-title">포트폴리오 요약</div>
            <div className="sim-confirm-body">
              <div><strong>사용자</strong> {rankingUserSummary.userName}</div>
              <div><strong>기준일</strong> {rankingUserSummary.portfolio?.valuationDate || "-"}</div>
              <div><strong>총자산</strong> {fmt(Number(rankingUserSummary.portfolio?.totalValue || 0))}원</div>
              <div><strong>예수금</strong> {fmt(Number(rankingUserSummary.portfolio?.cash || 0))}원</div>
              <div><strong>실현손익</strong> {formatSignedAmount(rankingUserSummary.portfolio?.realizedPnl, fmt)}원</div>
              <div><strong>미실현손익</strong> {formatSignedAmount(rankingUserSummary.portfolio?.unrealizedPnl, fmt)}원</div>
              <div><strong>보유종목</strong> {rankingUserSummary.portfolio?.holdings?.length || 0}개</div>
            </div>
            {(rankingUserSummary.portfolio?.holdings?.length || 0) > 0 && (
              <div className="home-modal-holdings">
                {rankingUserSummary.portfolio.holdings.slice(0, 8).map((h) => (
                  <div key={`league-summary-hold-${h.code}`} className="home-modal-hold-row">
                    <span>{getStockNameByCode?.(h.code) || h.code} ({h.code})</span>
                    <span>{h.quantity}주</span>
                    <span>{fmt(Number(h.currentPrice || 0))}원</span>
                  </div>
                ))}
              </div>
            )}
            <div className="sim-confirm-actions">
              <button type="button" className="sim-order-mini-btn" onClick={() => setRankingUserSummary?.(null)}>
                닫기
              </button>
            </div>
          </div>
        </div>
      )}
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

function formatSignedAmount(value, fmt) {
  const n = Number(value || 0);
  return `${n > 0 ? "+" : ""}${fmt(n)}`;
}
