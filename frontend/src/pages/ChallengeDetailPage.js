import React, { useEffect, useMemo, useState } from "react";
import axios from "axios";

function authHeaders(authToken) {
  return authToken ? { Authorization: `Bearer ${authToken}` } : {};
}

export default function ChallengeDetailPage({
  apiBaseUrl,
  authToken,
  isLoggedIn,
  challengeId,
  currentUser,
  leagueState,
  fmt,
  fmtDateTime,
  getStockNameByCode,
  navigateTo,
}) {
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const [detail, setDetail] = useState(null);
  const [progress, setProgress] = useState(null);
  const [leaderboard, setLeaderboard] = useState([]);
  const [participants, setParticipants] = useState([]);
  const [feed, setFeed] = useState([]);
  const [feedCommentsByPost, setFeedCommentsByPost] = useState({});
  const [commentDraftByPost, setCommentDraftByPost] = useState({});
  const [feedText, setFeedText] = useState("");
  const [feedOnlyMine, setFeedOnlyMine] = useState(false);
  const [leaderboardFilter, setLeaderboardFilter] = useState("ALL");
  const [rankingUserSummary, setRankingUserSummary] = useState(null);
  const [rankingUserSummaryLoading, setRankingUserSummaryLoading] = useState(false);
  const [challengeProgressForModal, setChallengeProgressForModal] = useState(null);

  const loadComments = async (postIds) => {
    if (!postIds?.length) {
      setFeedCommentsByPost({});
      return;
    }
    const headers = { headers: authHeaders(authToken) };
    const entries = await Promise.all(postIds.map(async (postId) => {
      try {
        const res = await axios.get(`${apiBaseUrl}/api/feed/${postId}/comments`, headers);
        return [String(postId), Array.isArray(res.data) ? res.data : []];
      } catch {
        return [String(postId), []];
      }
    }));
    setFeedCommentsByPost(Object.fromEntries(entries));
  };

  const loadAll = async () => {
    if (!isLoggedIn || !authToken || !challengeId) return;
    setLoading(true);
    setMessage("");
    try {
      const headers = { headers: authHeaders(authToken) };
      const [detailRes, lbRes, pRes, feedRes] = await Promise.all([
        axios.get(`${apiBaseUrl}/api/challenges/${challengeId}`, headers),
        axios.get(`${apiBaseUrl}/api/challenges/${challengeId}/leaderboard`, headers),
        axios.get(`${apiBaseUrl}/api/challenges/${challengeId}/participants`, headers),
        axios.get(`${apiBaseUrl}/api/challenges/${challengeId}/feed`, headers),
      ]);
      const feedRows = Array.isArray(feedRes.data) ? feedRes.data : [];
      setDetail(detailRes.data || null);
      setLeaderboard(Array.isArray(lbRes.data) ? lbRes.data : []);
      setParticipants(Array.isArray(pRes.data) ? pRes.data : []);
      setFeed(feedRows);
      try {
        const pr = await axios.get(`${apiBaseUrl}/api/challenges/${challengeId}/progress/me`, headers);
        setProgress(pr.data || null);
      } catch {
        setProgress(null);
      }
      await loadComments(feedRows.map((x) => x.id));
    } catch (err) {
      setMessage(err?.response?.data?.message || err?.message || "챌린지 상세 조회 실패");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAll();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [challengeId, isLoggedIn, authToken, apiBaseUrl]);

  const meRank = leaderboard.find((x) => x.me);

  const filteredLeaderboard = useMemo(() => {
    return (leaderboard || []).filter((r) => {
      if (leaderboardFilter === "ACHIEVED" && !r.achieved) return false;
      if (leaderboardFilter === "MY_AROUND" && meRank && !r.me && Math.abs(Number(r.rank || 0) - Number(meRank.rank || 0)) > 3) return false;
      return true;
    });
  }, [leaderboard, leaderboardFilter, meRank]);

  const filteredFeed = useMemo(() => {
    if (!feedOnlyMine) return feed;
    return (feed || []).filter((post) => Number(post.userId) === Number(currentUser?.id));
  }, [feed, feedOnlyMine, currentUser]);

  if (!isLoggedIn) {
    return <div className="app-card"><h3>챌린지 상세</h3><p>로그인 후 이용할 수 있습니다.</p></div>;
  }

  const onJoin = async () => {
    try {
      await axios.post(`${apiBaseUrl}/api/challenges/${challengeId}/join`, {}, { headers: authHeaders(authToken) });
      setMessage("챌린지에 참여했습니다.");
      await loadAll();
    } catch (err) {
      setMessage(err?.response?.data?.message || err?.message || "참여 실패");
    }
  };

  const onLeave = async () => {
    try {
      await axios.post(`${apiBaseUrl}/api/challenges/${challengeId}/leave`, {}, { headers: authHeaders(authToken) });
      setMessage("챌린지 참여를 취소했습니다.");
      await loadAll();
    } catch (err) {
      setMessage(err?.response?.data?.message || err?.message || "참여 취소 실패");
    }
  };

  const onPostFeed = async () => {
    if (!feedText.trim()) return;
    try {
      await axios.post(`${apiBaseUrl}/api/challenges/${challengeId}/feed`, { content: feedText.trim() }, { headers: authHeaders(authToken) });
      setFeedText("");
      await loadAll();
    } catch (err) {
      setMessage(err?.response?.data?.message || err?.message || "피드 작성 실패");
    }
  };

  const onDeleteFeed = async (postId) => {
    try {
      await axios.delete(`${apiBaseUrl}/api/feed/${postId}`, { headers: authHeaders(authToken) });
      await loadAll();
    } catch (err) {
      setMessage(err?.response?.data?.message || err?.message || "피드 삭제 실패");
    }
  };

  const onCreateComment = async (postId) => {
    const text = String(commentDraftByPost[String(postId)] || "").trim();
    if (!text) return;
    try {
      await axios.post(`${apiBaseUrl}/api/feed/${postId}/comments`, { content: text }, { headers: authHeaders(authToken) });
      setCommentDraftByPost((prev) => ({ ...prev, [String(postId)]: "" }));
      await loadComments([postId]);
    } catch (err) {
      setMessage(err?.response?.data?.message || err?.message || "댓글 작성 실패");
    }
  };

  const onDeleteComment = async (commentId, postId) => {
    try {
      await axios.delete(`${apiBaseUrl}/api/feed/comments/${commentId}`, { headers: authHeaders(authToken) });
      await loadComments([postId]);
    } catch (err) {
      setMessage(err?.response?.data?.message || err?.message || "댓글 삭제 실패");
    }
  };

  const openUserPortfolio = async (userId) => {
    setRankingUserSummaryLoading(true);
    try {
      const headers = { headers: authHeaders(authToken) };
      const [portfolioRes, challengeProgressRes] = await Promise.all([
        axios.get(`${apiBaseUrl}/api/sim/rankings/${userId}/portfolio`, headers),
        axios.get(`${apiBaseUrl}/api/challenges/${challengeId}/progress/${userId}`, headers).catch(() => ({ data: null })),
      ]);
      setRankingUserSummary(portfolioRes.data || null);
      setChallengeProgressForModal(challengeProgressRes?.data || null);
    } catch (err) {
      setMessage(err?.response?.data?.message || err?.message || "사용자 상세 조회 실패");
    } finally {
      setRankingUserSummaryLoading(false);
    }
  };

  const achievementPct = Number(progress?.achievementRate || 0);
  const achievedCount = (leaderboard || []).filter((r) => r.achieved).length;
  const participationRate = detail?.maxParticipants ? Math.min(100, ((detail?.participantCount || 0) / detail.maxParticipants) * 100) : 0;
  const isEnded = String(detail?.status || "").toUpperCase() === "ENDED";

  return (
    <div style={{ display: "grid", gap: 16 }}>
      <div className="app-card" style={{ background: "linear-gradient(180deg, #ffffff 0%, #f8fbff 100%)" }}>
        <div className="app-toolbar-row" style={{ justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
          <div style={{ display: "grid", gap: 6 }}>
            <button type="button" className="home-link-btn" onClick={() => navigateTo?.("/challenges")}>← 챌린지 목록</button>
            <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
              <h3 style={{ margin: 0 }}>{detail?.title || `챌린지 #${challengeId}`}</h3>
              {isEnded && <span style={{ padding: "3px 8px", borderRadius: 999, background: "#eef2ff", color: "#4338ca", fontSize: 12, fontWeight: 700 }}>결과 확정</span>}
            </div>
            <div style={{ fontSize: 12, color: "#64748b" }}>
              {detail ? `${detail.startDate} ~ ${detail.endDate} · 목표 수익률 ${Number(detail.targetValue || 0).toFixed(1)}% · ${statusLabel(detail.status)}` : "로딩 중..."}
            </div>
            <div style={{ fontSize: 12, color: "#64748b" }}>
              상태 기준일: <strong>{leagueState?.currentDate || "-"}</strong> (리그 날짜 기준)
            </div>
          </div>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            {detail && !detail.joined && <button type="button" onClick={onJoin}>참여하기</button>}
            {detail && detail.joined && detail.status === "UPCOMING" && <button type="button" onClick={onLeave}>참여취소</button>}
            <button type="button" onClick={() => document.getElementById("challenge-feed")?.scrollIntoView({ behavior: "smooth", block: "start" })}>피드로 이동</button>
            <button type="button" onClick={loadAll} disabled={loading}>새로고침</button>
          </div>
        </div>
        {detail?.description && <div style={{ marginTop: 10, whiteSpace: "pre-wrap" }}>{detail.description}</div>}
        {message && <div className="sim-trade-message" style={{ marginTop: 10 }}>{message}</div>}
      </div>

      <div style={{ display: "grid", gap: 12, gridTemplateColumns: "repeat(auto-fit, minmax(180px,1fr))" }}>
        <StatCard label="챌린지 상태" value={statusLabel(detail?.status)} />
        <StatCard label="참여자" value={`${detail?.participantCount || 0}/${detail?.maxParticipants || 0}명`} />
        <StatCard label="달성자" value={`${achievedCount}명`} />
        <StatCard label="공개 범위" value={detail?.visibility === "PRIVATE" ? "비공개" : "공개"} />
      </div>

      <div className="app-card">
        <div style={{ fontWeight: 700, marginBottom: 8 }}>챌린지 진행 현황</div>
        <div style={{ display: "grid", gap: 10 }}>
          <BarRow label="참여자 모집 현황" pct={participationRate} right={`${participationRate.toFixed(1)}%`} color="#0ea5e9" />
          <BarRow label="내 목표 달성률" pct={Math.max(0, Math.min(achievementPct, 100))} right={progress ? `${achievementPct.toFixed(1)}%` : "미참여"} color={achievementPct >= 100 ? "#16a34a" : "#f59e0b"} />
        </div>
      </div>

      <div className="app-card">
        <div style={{ fontWeight: 700, marginBottom: 8 }}>내 진행률</div>
        {!progress && <div style={{ color: "#64748b" }}>참여 후 내 목표 달성률 카드가 표시됩니다.</div>}
        {progress && (
          <div className="home-grid home-grid-metrics">
            <MetricBox label="기준 자산" value={`${fmt(Number(progress.baselineTotalValue || 0))}원`} />
            <MetricBox label="현재 자산" value={`${fmt(Number(progress.currentTotalValue || 0))}원`} />
            <MetricBox label="총손익" value={`${Number(progress.pnl) > 0 ? "+" : ""}${fmt(Number(progress.pnl || 0))}원`} tone={Number(progress.pnl) >= 0 ? "up" : "down"} />
            <MetricBox label="수익률" value={`${Number(progress.returnRate) > 0 ? "+" : ""}${Number(progress.returnRate || 0).toFixed(2)}%`} tone={Number(progress.returnRate) >= 0 ? "up" : "down"} />
            <MetricBox label="목표 달성률" value={`${achievementPct.toFixed(1)}%`} tone={achievementPct >= 100 ? "up" : "neutral"} />
            <MetricBox label="상태" value={progress.achieved ? "달성" : "진행 중"} tone={progress.achieved ? "up" : "neutral"} />
          </div>
        )}
      </div>

      <div className="app-card">
        <div className="app-toolbar-row" style={{ justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
          <div style={{ fontWeight: 700 }}>리더보드</div>
          <select value={leaderboardFilter} onChange={(e) => setLeaderboardFilter(e.target.value)}>
            <option value="ALL">전체</option>
            <option value="ACHIEVED">달성자만</option>
            <option value="MY_AROUND">내 주변 순위</option>
          </select>
        </div>
        <div className="sim-order-table-wrap">
          <table className="sim-order-table">
            <thead>
              <tr>
                <th className="num">순위</th>
                <th>사용자</th>
                <th className="num">달성률</th>
                <th className="num">수익률</th>
                <th className="num">총손익</th>
                <th className="num">현재자산</th>
                <th>기준일</th>
              </tr>
            </thead>
            <tbody>
              {filteredLeaderboard.length === 0 && <tr><td colSpan={7}>표시할 리더보드 데이터가 없습니다.</td></tr>}
              {filteredLeaderboard.map((r) => (
                <tr key={`challenge-lb-${r.userId}`} className={r.me ? "sim-ranking-me-row" : ""}>
                  <td className="num">{r.rank}</td>
                  <td>
                    <button type="button" className="sim-ranking-name-btn" onClick={() => openUserPortfolio(r.userId)} disabled={rankingUserSummaryLoading}>
                      {r.userName}{r.me ? " (나)" : ""}
                    </button>
                  </td>
                  <td className={`num ${Number(r.achievementRate) >= 100 ? "up" : "down"}`}>{Number(r.achievementRate || 0).toFixed(1)}%</td>
                  <td className={`num ${Number(r.returnRate) >= 0 ? "up" : "down"}`}>{Number(r.returnRate) > 0 ? "+" : ""}{Number(r.returnRate || 0).toFixed(2)}%</td>
                  <td className={`num ${Number(r.pnl) >= 0 ? "up" : "down"}`}>{Number(r.pnl) > 0 ? "+" : ""}{fmt(Number(r.pnl || 0))}원</td>
                  <td className="num">{fmt(Number(r.currentTotalValue || 0))}원</td>
                  <td>{r.valuationDate || "-"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div style={{ display: "grid", gap: 16, gridTemplateColumns: "minmax(0, 360px) minmax(0, 1fr)" }}>
        <div className="app-card">
          <div style={{ fontWeight: 700, marginBottom: 8 }}>참여자 ({participants.length})</div>
          <div style={{ display: "grid", gap: 6, maxHeight: 420, overflow: "auto" }}>
            {participants.length === 0 && <div>참여자가 없습니다.</div>}
            {participants.map((p) => (
              <button key={`cp-${p.userId}`} type="button" onClick={() => openUserPortfolio(p.userId)} className="home-ranking-row" style={{ textAlign: "left" }}>
                <div className="home-rank-name">{p.userName}</div>
                <div style={{ fontSize: 12, color: "#64748b" }}>기준자산 {fmt(Number(p.baselineTotalValue || 0))}원</div>
                <div style={{ fontSize: 12, color: "#64748b" }}>참여일 {fmtDateTime?.(p.joinedAt) || p.joinedAt}</div>
              </button>
            ))}
          </div>
        </div>

        <div className="app-card" id="challenge-feed">
          <div className="app-toolbar-row" style={{ justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
            <div style={{ fontWeight: 700 }}>챌린지 피드 ({feed.length})</div>
            <label style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12, color: "#64748b" }}>
              <input type="checkbox" checked={feedOnlyMine} onChange={(e) => setFeedOnlyMine(e.target.checked)} />
              내 글만 보기
            </label>
          </div>
          <div style={{ display: "grid", gap: 8 }}>
            <textarea rows={3} placeholder="참여 후기, 전략, 회고를 공유해보세요." value={feedText} onChange={(e) => setFeedText(e.target.value)} />
            <button type="button" onClick={onPostFeed} disabled={!feedText.trim()}>피드 작성</button>
          </div>
          <div style={{ display: "grid", gap: 8, marginTop: 12 }}>
            {filteredFeed.length === 0 && <div>표시할 피드가 없습니다.</div>}
            {filteredFeed.map((post) => {
              const comments = feedCommentsByPost[String(post.id)] || [];
              return (
                <div key={`challenge-feed-${post.id}`} style={{ border: "1px solid #e2e8f0", borderRadius: 10, padding: 10 }}>
                  <div style={{ display: "flex", justifyContent: "space-between", gap: 8, fontSize: 12, color: "#64748b", alignItems: "center" }}>
                    <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                      <span>{post.userName}</span>
                      {Number(post.userId) === Number(currentUser?.id) && <span style={{ padding: "2px 6px", borderRadius: 999, background: "#eff6ff", color: "#1d4ed8" }}>내 글</span>}
                    </div>
                    <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                      <span>{fmtDateTime?.(post.createdAt) || post.createdAt}</span>
                      {Number(post.userId) === Number(currentUser?.id) && <button type="button" className="sim-order-mini-btn" onClick={() => onDeleteFeed(post.id)}>삭제</button>}
                    </div>
                  </div>
                  <div style={{ marginTop: 6, whiteSpace: "pre-wrap" }}>{post.content}</div>

                  <div style={{ marginTop: 10, paddingTop: 10, borderTop: "1px solid #f1f5f9", display: "grid", gap: 8 }}>
                    <div style={{ fontSize: 12, color: "#64748b", fontWeight: 600 }}>댓글 {comments.length}개</div>
                    <div style={{ display: "grid", gap: 6 }}>
                      {comments.map((c) => (
                        <div key={`cmt-${c.id}`} style={{ background: "#f8fafc", borderRadius: 8, padding: 8 }}>
                          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, fontSize: 12, color: "#64748b" }}>
                            <span>{c.userName}{Number(c.userId) === Number(currentUser?.id) ? " (나)" : ""}</span>
                            <span>{fmtDateTime?.(c.createdAt) || c.createdAt}</span>
                          </div>
                          <div style={{ marginTop: 4, whiteSpace: "pre-wrap" }}>{c.content}</div>
                          {Number(c.userId) === Number(currentUser?.id) && (
                            <div style={{ marginTop: 6 }}>
                              <button type="button" className="sim-order-mini-btn" onClick={() => onDeleteComment(c.id, post.id)}>댓글 삭제</button>
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                    <div style={{ display: "grid", gap: 6 }}>
                      <textarea
                        rows={2}
                        placeholder="댓글 작성"
                        value={commentDraftByPost[String(post.id)] || ""}
                        onChange={(e) => setCommentDraftByPost((prev) => ({ ...prev, [String(post.id)]: e.target.value }))}
                      />
                      <button type="button" onClick={() => onCreateComment(post.id)} disabled={!String(commentDraftByPost[String(post.id)] || "").trim()}>
                        댓글 등록
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {rankingUserSummary && (
        <div className="sim-modal-backdrop" role="presentation" onClick={() => { setRankingUserSummary(null); setChallengeProgressForModal(null); }}>
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

            {challengeProgressForModal && (
              <div className="sim-confirm-body" style={{ borderTop: "1px solid #e2e8f0", marginTop: 8, paddingTop: 12 }}>
                <div style={{ fontWeight: 700, marginBottom: 6 }}>챌린지 기준 진행률</div>
                <div><strong>기준 자산</strong> {fmt(Number(challengeProgressForModal.baselineTotalValue || 0))}원</div>
                <div><strong>현재 자산</strong> {fmt(Number(challengeProgressForModal.currentTotalValue || 0))}원</div>
                <div><strong>수익률</strong> {Number(challengeProgressForModal.returnRate) > 0 ? "+" : ""}{Number(challengeProgressForModal.returnRate || 0).toFixed(2)}%</div>
                <div><strong>목표 달성률</strong> {Number(challengeProgressForModal.achievementRate || 0).toFixed(1)}%</div>
                <div><strong>상태</strong> {challengeProgressForModal.achieved ? "달성" : "진행 중"}</div>
              </div>
            )}

            {(rankingUserSummary.portfolio?.holdings?.length || 0) > 0 && (
              <div className="home-modal-holdings">
                {rankingUserSummary.portfolio.holdings.slice(0, 10).map((h) => (
                  <div key={`challenge-summary-hold-${h.code}`} className="home-modal-hold-row">
                    <span>{getStockNameByCode?.(h.code) || h.code} ({h.code})</span>
                    <span>{h.quantity}주</span>
                    <span>{fmt(Number(h.currentPrice || 0))}원</span>
                  </div>
                ))}
              </div>
            )}
            <div className="sim-confirm-actions">
              <button type="button" className="sim-order-mini-btn" onClick={() => { setRankingUserSummary(null); setChallengeProgressForModal(null); }}>닫기</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function MetricBox({ label, value, tone = "neutral" }) {
  return (
    <div className={`home-metric home-metric-${tone}`}>
      <div className="home-metric-label">{label}</div>
      <div className="home-metric-value">{value}</div>
    </div>
  );
}

function StatCard({ label, value }) {
  return (
    <div className="app-card" style={{ padding: 12 }}>
      <div style={{ fontSize: 12, color: "#64748b" }}>{label}</div>
      <div style={{ fontWeight: 800, fontSize: 18 }}>{value || "-"}</div>
    </div>
  );
}

function BarRow({ label, pct, right, color }) {
  const width = Math.max(0, Math.min(100, Number(pct || 0)));
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, color: "#64748b" }}>
        <span>{label}</span>
        <span>{right}</span>
      </div>
      <div style={{ height: 8, borderRadius: 999, background: "#e2e8f0", overflow: "hidden", marginTop: 4 }}>
        <div style={{ width: `${width}%`, height: "100%", background: color || "#0ea5e9" }} />
      </div>
    </div>
  );
}

function statusLabel(status) {
  switch (String(status || "").toUpperCase()) {
    case "ONGOING": return "진행중";
    case "UPCOMING": return "예정";
    case "ENDED": return "종료";
    default: return "기타";
  }
}

function formatSignedAmount(value, fmt) {
  const n = Number(value || 0);
  return `${n > 0 ? "+" : ""}${fmt(n)}`;
}
