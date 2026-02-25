import React, { useEffect, useMemo, useState } from "react";
import axios from "axios";

const HOME_LAYOUT_KEY = "fintech_home_layout_v2";
const AUTH_TOKEN_KEY = "fintech_access_token";
const INITIAL_CASH = 50000000;
const DEFAULT_WIDGET_ORDER = ["metrics", "movers", "volume", "watchlist", "ranking"];

function readHomeLayout() {
  try {
    const raw = localStorage.getItem(HOME_LAYOUT_KEY);
    if (!raw) return { order: DEFAULT_WIDGET_ORDER, collapsed: {} };
    const parsed = JSON.parse(raw);
    const order = Array.isArray(parsed?.order)
      ? parsed.order.filter((x) => DEFAULT_WIDGET_ORDER.includes(x))
      : DEFAULT_WIDGET_ORDER;
    return {
      order: [...order, ...DEFAULT_WIDGET_ORDER.filter((x) => !order.includes(x))],
      collapsed: parsed?.collapsed && typeof parsed.collapsed === "object" ? parsed.collapsed : {},
    };
  } catch {
    return { order: DEFAULT_WIDGET_ORDER, collapsed: {} };
  }
}

function saveHomeLayout(state) {
  try {
    localStorage.setItem(HOME_LAYOUT_KEY, JSON.stringify(state));
  } catch {}
}

function calcMovePercent(rows) {
  if (!Array.isArray(rows) || rows.length < 2) return null;
  const prev = Number(rows[rows.length - 2]?.close);
  const curr = Number(rows[rows.length - 1]?.close);
  if (!Number.isFinite(prev) || !Number.isFinite(curr) || prev === 0) return null;
  return ((curr - prev) / prev) * 100;
}

function readAccessToken() {
  try {
    return localStorage.getItem(AUTH_TOKEN_KEY) || "";
  } catch {
    return "";
  }
}

export default function HomePage({
  apiBaseUrl,
  isLoggedIn,
  currentUser,
  leagueState,
  portfolio,
  rankings,
  rankingsLoading,
  loadRankings,
  watchlistCodes,
  getStockNameByCode,
  navigateTo,
  openTradeFromMarket,
  chartEndDate,
  fmt,
  openRankingUserSummary,
  rankingUserSummary,
  setRankingUserSummary,
  rankingUserSummaryLoading,
}) {
  const [layoutOrder, setLayoutOrder] = useState(DEFAULT_WIDGET_ORDER);
  const [collapsedMap, setCollapsedMap] = useState({});
  const [draggingWidget, setDraggingWidget] = useState("");

  const [moversLoading, setMoversLoading] = useState(false);
  const [moversError, setMoversError] = useState("");
  const [moversPayload, setMoversPayload] = useState({ gainers: [], losers: [], tradeDate: "" });

  const [popularLoading, setPopularLoading] = useState(false);
  const [popularError, setPopularError] = useState("");
  const [popularRows, setPopularRows] = useState([]);

  useEffect(() => {
    const saved = readHomeLayout();
    setLayoutOrder(saved.order);
    setCollapsedMap(saved.collapsed);
  }, []);

  useEffect(() => {
    saveHomeLayout({ order: layoutOrder, collapsed: collapsedMap });
  }, [layoutOrder, collapsedMap]);

  useEffect(() => {
    if (isLoggedIn) {
      loadRankings?.();
    }
  }, [isLoggedIn, loadRankings]);

  useEffect(() => {
    let cancelled = false;
    async function loadMovers() {
      if (!apiBaseUrl) return;
      setMoversLoading(true);
      setMoversError("");
      try {
        const res = await axios.get(`${apiBaseUrl}/api/stock/top-movers`, {
          params: { date: chartEndDate || undefined, limit: 5 },
        });
        if (!cancelled) {
          const gainers = Array.isArray(res.data?.gainers) ? res.data.gainers : [];
          const losers = Array.isArray(res.data?.losers) ? res.data.losers : [];
          setMoversPayload({
            tradeDate: res.data?.tradeDate || "",
            gainers: gainers.map((row) => ({
              code: row.code,
              name: getStockNameByCode(row.code),
              changeRate: Number(row.changeRate || 0),
              close: Number(row.closePrice || 0),
            })),
            losers: losers.map((row) => ({
              code: row.code,
              name: getStockNameByCode(row.code),
              changeRate: Number(row.changeRate || 0),
              close: Number(row.closePrice || 0),
            })),
          });
        }
      } catch (err) {
        if (!cancelled) {
          setMoversError(err?.response?.data?.message || "상승/하락 데이터를 불러오지 못했습니다.");
        }
      } finally {
        if (!cancelled) setMoversLoading(false);
      }
    }
    loadMovers();
    return () => {
      cancelled = true;
    };
  }, [apiBaseUrl, chartEndDate, getStockNameByCode]);

  useEffect(() => {
    let cancelled = false;
    async function loadPopularStocks() {
      if (!apiBaseUrl || !isLoggedIn) return;
      setPopularLoading(true);
      setPopularError("");
      try {
        const token = readAccessToken();
        const res = await axios.get(`${apiBaseUrl}/api/sim/popular-stocks`, {
          params: { limit: 10, days: 7 },
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        });
        if (!cancelled) setPopularRows(Array.isArray(res.data) ? res.data : []);
      } catch (err) {
        if (!cancelled) {
          setPopularError(err?.response?.data?.message || "리그 인기 종목을 불러오지 못했습니다.");
        }
      } finally {
        if (!cancelled) setPopularLoading(false);
      }
    }
    loadPopularStocks();
    return () => {
      cancelled = true;
    };
  }, [apiBaseUrl, isLoggedIn]);

  const top3 = useMemo(() => (rankings || []).slice(0, 3), [rankings]);
  const upMovers = useMemo(() => moversPayload.gainers || [], [moversPayload]);
  const downMovers = useMemo(() => moversPayload.losers || [], [moversPayload]);

  const hasPortfolio = Boolean(portfolio);
  const totalValue = Number(portfolio?.totalValue || 0);
  const cash = Number(portfolio?.cash || 0);
  const unrealized = Number(portfolio?.unrealizedPnl || 0);
  const realized = Number(portfolio?.realizedPnl || 0);
  const totalPnl = realized + unrealized;
  const returnRate = isLoggedIn && hasPortfolio && Number.isFinite(totalValue)
    ? (totalPnl / INITIAL_CASH) * 100
    : 0;

  const reorderWidgets = (fromId, toId) => {
    if (!fromId || !toId || fromId === toId) return;
    setLayoutOrder((prev) => {
      const fromIdx = prev.indexOf(fromId);
      const toIdx = prev.indexOf(toId);
      if (fromIdx < 0 || toIdx < 0) return prev;
      const next = [...prev];
      const [moved] = next.splice(fromIdx, 1);
      next.splice(toIdx, 0, moved);
      return next;
    });
  };

  const widgetDefs = {
    metrics: {
      icon: "📊",
      title: "계좌 요약",
      span: "full",
      sparkline: [cash, totalValue * 0.6, totalValue * 0.75, totalValue * 0.9, totalValue],
      body: (
        <div className="home-grid home-grid-metrics">
          <MetricCard label="공용 기준일" value={leagueState?.currentDate || chartEndDate || "-"} tone="neutral" />
          <MetricCard label="리그 상태" value={leagueState?.running ? "진행 중" : "정지"} tone={leagueState?.running ? "up" : "neutral"} />
          <MetricCard label="총자산" value={isLoggedIn ? (hasPortfolio ? `${fmt(totalValue)}원` : "-") : "로그인 필요"} tone="neutral" />
          <MetricCard label="예수금" value={isLoggedIn ? (hasPortfolio ? `${fmt(cash)}원` : "-") : "-"} tone="neutral" />
          <MetricCard label="미실현손익" value={isLoggedIn ? (hasPortfolio ? `${fmtSigned(unrealized, fmt)}원` : "-") : "-"} tone={unrealized >= 0 ? "up" : "down"} />
          <MetricCard label="수익률" value={isLoggedIn ? (hasPortfolio ? `${returnRate > 0 ? "+" : ""}${returnRate.toFixed(2)}%` : "-") : "-"} tone={returnRate >= 0 ? "up" : "down"} />
        </div>
      ),
    },
    movers: {
      icon: "📈",
      title: "상승/하락 TOP",
      span: "full",
      sparkline: [...(moversPayload.gainers || []), ...(moversPayload.losers || [])]
        .slice(0, 6)
        .map((r) => Number(r.changeRate || 0)),
      body: (
        <div className="home-movers-grid">
          <MoverPanel title="상승 TOP" tone="up" rows={upMovers} loading={moversLoading} error={moversError} fmt={fmt} onSelectCode={openTradeFromMarket} />
          <MoverPanel title="하락 TOP" tone="down" rows={downMovers} loading={moversLoading} error={moversError} fmt={fmt} onSelectCode={openTradeFromMarket} />
        </div>
      ),
    },
    volume: {
      icon: "🔥",
      title: "리그 인기 종목 TOP 10",
      span: "full",
      sparkline: popularRows.slice(0, 8).map((x) => Number(x.executionCount || 0)),
      body: (
        <PopularStocksPanel
          rows={popularRows}
          loading={popularLoading}
          error={popularError}
          fmt={fmt}
          getStockNameByCode={getStockNameByCode}
          onOpenTrade={openTradeFromMarket}
          onOpenMarket={(code) => {
            openTradeFromMarket?.(code);
            navigateTo("/market");
          }}
        />
      ),
    },
    watchlist: {
      icon: "⭐",
      title: "관심종목 / 보유 요약",
      span: "wide",
      sparkline: (portfolio?.holdings || []).slice(0, 6).map((h) => Number(h.unrealizedPnl || 0)),
      body: (
        <>
          {!isLoggedIn && <div className="home-empty">로그인하면 관심종목과 개인 계좌 요약을 볼 수 있습니다.</div>}
          {isLoggedIn && (!watchlistCodes || watchlistCodes.length === 0) && (
            <div className="home-empty">관심종목이 없습니다. 차트 페이지에서 추가해보세요.</div>
          )}
          {isLoggedIn && (watchlistCodes || []).length > 0 && (
            <div className="home-watchlist">
              {watchlistCodes.slice(0, 8).map((code) => (
                <button key={`home-watch-${code}`} type="button" className="home-watch-item" onClick={() => openTradeFromMarket?.(code)}>
                  <span className="home-watch-name">{getStockNameByCode(code)}</span>
                  <span className="home-watch-code">{code}</span>
                </button>
              ))}
            </div>
          )}
          {isLoggedIn && portfolio?.holdings?.length > 0 && (
            <div className="home-watchlist-holdings">
              <div className="home-panel-header">
                <strong>보유 종목</strong>
                <button type="button" className="home-link-btn" onClick={() => navigateTo("/sim")}>모의투자에서 보기</button>
              </div>
              <div className="home-mini-table">
                {portfolio.holdings.slice(0, 5).map((h) => (
                  <div key={`hold-${h.code}`} className="home-mini-row">
                    <div>{getStockNameByCode(h.code)} ({h.code})</div>
                    <div>{h.quantity}주</div>
                    <div>{fmt(Number(h.currentPrice || 0))}원</div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </>
      ),
    },
    ranking: {
      icon: "🏆",
      title: "수익률 랭킹",
      span: "narrow",
      sparkline: top3.map((r) => Number(r.returnRate || 0)),
      body: (
        <>
          {rankingsLoading && <div className="home-empty">랭킹을 불러오는 중...</div>}
          {!rankingsLoading && top3.length === 0 && <div className="home-empty">랭킹 데이터가 없습니다.</div>}
          {!rankingsLoading && top3.length > 0 && (
            <div className="home-ranking">
              {top3.map((row) => (
                <button
                  key={`home-rank-${row.userId}`}
                  type="button"
                  className={`home-ranking-row${row.me ? " is-me" : ""}`}
                  onClick={() => openRankingUserSummary?.(row.userId)}
                  disabled={rankingUserSummaryLoading}
                >
                  <div className="home-rank-no">#{row.rank}</div>
                  <div className="home-rank-name">{row.userName}{row.me ? " (나)" : ""}</div>
                  <div className={`home-rank-rate ${Number(row.returnRate) >= 0 ? "up" : "down"}`}>
                    {Number(row.returnRate) > 0 ? "+" : ""}{Number(row.returnRate).toFixed(2)}%
                  </div>
                </button>
              ))}
            </div>
          )}
          <div className="home-note">실현손익 {isLoggedIn && hasPortfolio ? `${fmtSigned(realized, fmt)}원` : "-"} · 보유종목 {portfolio?.holdings?.length || 0}개</div>
        </>
      ),
    },
  };

  return (
    <div style={{ display: "grid", gap: 16 }}>
      <section className="home-hero">
        <div>
          <div className="home-hero-kicker">Mock Investing League</div>
          <h2 className="home-hero-title">{isLoggedIn ? `${currentUser?.name || "사용자"}의 투자 대시보드` : "공용 리그 대시보드"}</h2>
          <div className="home-hero-sub">
            공용 기준일 <strong>{leagueState?.currentDate || chartEndDate || "-"}</strong> · 상태 <strong>{leagueState?.running ? "진행 중" : "정지"}</strong>
          </div>
        </div>
        <div className="home-hero-summary">
          <div className="home-hero-pill">
            <span>리그 시작일</span>
            <strong>{leagueState?.anchorDate || "-"}</strong>
          </div>
          <div className="home-hero-pill">
            <span>틱 설정</span>
            <strong>{leagueState?.tickSeconds || 60}초 / {leagueState?.stepDays || 1}일</strong>
          </div>
          <div className="home-hero-pill">
            <span>내 보유종목</span>
            <strong>{portfolio?.holdings?.length || 0}개</strong>
          </div>
          <div className="home-hero-links">
            <button type="button" className="home-link-btn" onClick={() => navigateTo("/sim")}>모의투자로 이동</button>
            <button type="button" className="home-link-btn" onClick={() => navigateTo("/league")}>리그 상세 보기</button>
          </div>
        </div>
      </section>

      <div className="home-layout-grid">
        {layoutOrder.map((widgetId) => {
          const widget = widgetDefs[widgetId];
          if (!widget) return null;
          const collapsed = Boolean(collapsedMap[widgetId]);
          return (
            <section
              key={widgetId}
              className={`app-card home-widget home-widget-${widget.span || "full"}${draggingWidget === widgetId ? " home-widget-dragging" : ""}`}
              draggable
              onDragStart={() => setDraggingWidget(widgetId)}
              onDragOver={(e) => e.preventDefault()}
              onDrop={(e) => {
                e.preventDefault();
                reorderWidgets(draggingWidget, widgetId);
                setDraggingWidget("");
              }}
              onDragEnd={() => setDraggingWidget("")}
            >
              <div className="home-widget-header">
                <div className="home-widget-title-wrap">
                  <span className="home-widget-drag-handle" title="드래그해서 순서 변경">↕</span>
                  <span className="home-widget-icon" aria-hidden="true">{widget.icon}</span>
                  <strong>{widget.title}</strong>
                </div>
                <div className="home-widget-actions">
                  <Sparkline values={widget.sparkline || []} className="home-widget-sparkline" />
                  {widgetId === "watchlist" && <button type="button" className="home-link-btn" onClick={() => navigateTo("/charts")}>차트 관리</button>}
                  {widgetId === "ranking" && <button type="button" className="home-link-btn" onClick={() => navigateTo("/league")}>전체 랭킹</button>}
                  {widgetId === "volume" && <button type="button" className="home-link-btn" onClick={() => navigateTo("/sim")}>모의투자</button>}
                  <button type="button" className="home-collapse-btn" onClick={() => setCollapsedMap((prev) => ({ ...prev, [widgetId]: !prev[widgetId] }))}>
                    {collapsed ? "열기" : "접기"}
                  </button>
                </div>
              </div>
              {!collapsed && <div className="home-widget-body">{widget.body}</div>}
            </section>
          );
        })}
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
              <div><strong>보유종목</strong> {rankingUserSummary.portfolio?.holdings?.length || 0}개</div>
            </div>
            {(rankingUserSummary.portfolio?.holdings?.length || 0) > 0 && (
              <div className="home-modal-holdings">
                {rankingUserSummary.portfolio.holdings.slice(0, 8).map((h) => (
                  <div key={`home-summary-hold-${h.code}`} className="home-modal-hold-row">
                    <span>{getStockNameByCode(h.code)} ({h.code})</span>
                    <span>{h.quantity}주</span>
                    <span>{fmt(Number(h.currentPrice || 0))}원</span>
                  </div>
                ))}
              </div>
            )}
            <div className="sim-confirm-actions">
              <button type="button" className="sim-order-mini-btn" onClick={() => setRankingUserSummary?.(null)}>닫기</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function MetricCard({ label, value, tone }) {
  return (
    <div className={`home-metric home-metric-${tone || "neutral"}`}>
      <div className="home-metric-label">{label}</div>
      <div className="home-metric-value">{value}</div>
    </div>
  );
}

function Sparkline({ values, className }) {
  const nums = (values || []).map((v) => Number(v)).filter(Number.isFinite);
  if (nums.length < 2) return null;
  const min = Math.min(...nums);
  const max = Math.max(...nums);
  const width = 74;
  const height = 20;
  const range = max - min || 1;
  const points = nums
    .map((v, i) => `${(i / (nums.length - 1)) * width},${height - ((v - min) / range) * height}`)
    .join(" ");
  return (
    <svg className={className} viewBox={`0 0 ${width} ${height}`} role="img" aria-label="trend">
      <polyline fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" points={points} />
    </svg>
  );
}

function MoverPanel({ title, tone, rows, loading, error, fmt, onSelectCode }) {
  return (
    <div className="home-movers-panel">
      <div className={`home-movers-title ${tone}`}>{title}</div>
      {loading && <div className="home-empty">불러오는 중...</div>}
      {!loading && error && <div className="home-empty" style={{ color: "#b91c1c" }}>{error}</div>}
      {!loading && !error && rows.length === 0 && <div className="home-empty">표시할 데이터가 없습니다.</div>}
      {!loading && !error && rows.length > 0 && (
        <div className="home-movers-list">
          {rows.map((row) => (
            <button key={`mover-${title}-${row.code}`} type="button" className="home-mover-row" onClick={() => onSelectCode?.(row.code)}>
              <div className="home-mover-name">{row.name}</div>
              <div className="home-mover-code">{row.code}</div>
              <div className={`home-mover-rate ${Number(row.changeRate) >= 0 ? "up" : "down"}`}>
                {Number(row.changeRate) > 0 ? "+" : ""}{Number(row.changeRate).toFixed(2)}%
              </div>
              <div className="home-mover-price">{fmt(Number(row.close || 0))}원</div>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function PopularStocksPanel({ rows, loading, error, fmt, getStockNameByCode, onOpenTrade, onOpenMarket }) {
  return (
    <div className="home-volume-panel">
      {loading && <div className="home-empty">불러오는 중...</div>}
      {!loading && error && <div className="home-empty" style={{ color: "#b91c1c" }}>{error}</div>}
      {!loading && !error && rows.length === 0 && <div className="home-empty">리그 체결 데이터가 없습니다.</div>}
      {!loading && !error && rows.length > 0 && (
        <div className="home-volume-table-wrap">
          <table className="home-volume-table">
            <thead>
              <tr>
                <th>순위</th>
                <th>종목</th>
                <th>현재가</th>
                <th>체결건수</th>
                <th>체결수량</th>
                <th>기준일</th>
                <th>액션</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row, idx) => (
                <tr key={`popular-${row.code}-${idx}`}>
                  <td>{idx + 1}</td>
                  <td>
                    <button type="button" className="home-link-btn" onClick={() => onOpenMarket?.(row.code)}>
                      {getStockNameByCode(row.code)} ({row.code})
                    </button>
                  </td>
                  <td className="num">{fmt(Number(row.currentPrice || 0))}원</td>
                  <td className="num">{fmt(Number(row.executionCount || 0))}</td>
                  <td className="num">{fmt(Number(row.totalQuantity || 0))}</td>
                  <td>{row.valuationDate || "-"}</td>
                  <td>
                    <button type="button" className="sim-order-mini-btn" onClick={() => onOpenTrade?.(row.code)}>
                      주문
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function fmtSigned(value, fmt) {
  const n = Number(value || 0);
  return `${n > 0 ? "+" : ""}${fmt(n)}`;
}
