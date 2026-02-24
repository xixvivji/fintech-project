import React, { useEffect, useMemo, useState } from "react";
import axios from "axios";
import StockChartCard from "../components/StockChartCard";

const HOME_LAYOUT_KEY = "fintech_home_layout_v1";
const DEFAULT_WIDGET_ORDER = ["metrics", "movers", "watchlist", "ranking", "charts"];
const DEFAULT_COLLAPSED = {};

function readHomeLayout() {
  try {
    const raw = localStorage.getItem(HOME_LAYOUT_KEY);
    if (!raw) return { order: DEFAULT_WIDGET_ORDER, collapsed: DEFAULT_COLLAPSED };
    const parsed = JSON.parse(raw);
    const order = Array.isArray(parsed?.order) ? parsed.order.filter((x) => DEFAULT_WIDGET_ORDER.includes(x)) : DEFAULT_WIDGET_ORDER;
    const mergedOrder = [...order, ...DEFAULT_WIDGET_ORDER.filter((x) => !order.includes(x))];
    return {
      order: mergedOrder,
      collapsed: parsed?.collapsed && typeof parsed.collapsed === "object" ? parsed.collapsed : DEFAULT_COLLAPSED,
    };
  } catch {
    return { order: DEFAULT_WIDGET_ORDER, collapsed: DEFAULT_COLLAPSED };
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
  const [collapsedMap, setCollapsedMap] = useState(DEFAULT_COLLAPSED);
  const [draggingWidget, setDraggingWidget] = useState("");
  const [moversLoading, setMoversLoading] = useState(false);
  const [moversError, setMoversError] = useState("");
  const [moversRows, setMoversRows] = useState([]);

  useEffect(() => {
    const saved = readHomeLayout();
    setLayoutOrder(saved.order);
    setCollapsedMap(saved.collapsed);
  }, []);

  useEffect(() => {
    saveHomeLayout({ order: layoutOrder, collapsed: collapsedMap });
  }, [layoutOrder, collapsedMap]);

  useEffect(() => {
    if (isLoggedIn) loadRankings?.();
  }, [isLoggedIn, loadRankings]);

  const moverUniverse = useMemo(() => {
    const preferred = Array.from(new Set([...(watchlistCodes || []), "005930", "000660", "035420", "035720", "005380", "000270"]));
    return preferred.slice(0, 10);
  }, [watchlistCodes]);

  useEffect(() => {
    let cancelled = false;
    async function loadMovers() {
      if (!apiBaseUrl || !moverUniverse.length) return;
      setMoversLoading(true);
      setMoversError("");
      try {
        const results = await Promise.all(
          moverUniverse.map(async (code) => {
            const res = await axios.get(`${apiBaseUrl}/api/stock/chart/${code}`, {
              params: { months: 1, endDate: chartEndDate || undefined },
            });
            const rows = Array.isArray(res.data) ? res.data : [];
            const changeRate = calcMovePercent(rows);
            const last = rows[rows.length - 1];
            return {
              code,
              name: getStockNameByCode(code),
              changeRate,
              close: Number(last?.close || 0),
            };
          })
        );
        if (cancelled) return;
        setMoversRows(results.filter((x) => Number.isFinite(x.changeRate)));
      } catch (err) {
        if (cancelled) return;
        setMoversError(err?.response?.data?.message || "상승/하락 데이터를 불러오지 못했습니다.");
      } finally {
        if (!cancelled) setMoversLoading(false);
      }
    }
    loadMovers();
    return () => {
      cancelled = true;
    };
  }, [apiBaseUrl, chartEndDate, getStockNameByCode, moverUniverse]);

  const top5 = useMemo(() => (rankings || []).slice(0, 5), [rankings]);
  const upMovers = useMemo(() => [...moversRows].sort((a, b) => (b.changeRate ?? -999) - (a.changeRate ?? -999)).slice(0, 5), [moversRows]);
  const downMovers = useMemo(() => [...moversRows].sort((a, b) => (a.changeRate ?? 999) - (b.changeRate ?? 999)).slice(0, 5), [moversRows]);
  const watchTop = useMemo(() => {
    const base = (watchlistCodes || []).slice(0, 2);
    if (base.length >= 2) return base;
    return Array.from(new Set([...base, "005930", "000660"])).slice(0, 2);
  }, [watchlistCodes]);

  const totalValue = Number(portfolio?.totalValue || 0);
  const cash = Number(portfolio?.cash || 0);
  const unrealized = Number(portfolio?.unrealizedPnl || 0);
  const realized = Number(portfolio?.realizedPnl || 0);
  const returnRate = Number(portfolio?.returnRate || 0);

  const toggleCollapsed = (id) => {
    setCollapsedMap((prev) => ({ ...prev, [id]: !prev[id] }));
  };

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
      icon: "◈",
      title: "계좌 요약",
      span: "full",
      sparkline: [
        cash * 0.9 + totalValue * 0.1,
        cash * 0.7 + totalValue * 0.3,
        cash * 0.5 + totalValue * 0.5,
        totalValue * 0.85,
        totalValue,
      ],
      body: (
        <div className="home-grid home-grid-metrics">
          <MetricCard label="공용 기준일" value={leagueState?.currentDate || "-"} tone="neutral" />
          <MetricCard label="리그 상태" value={leagueState?.running ? "진행 중" : "정지"} tone={leagueState?.running ? "up" : "neutral"} />
          <MetricCard label="총자산" value={isLoggedIn ? `${fmt(totalValue)}원` : "로그인 필요"} tone="neutral" />
          <MetricCard label="예수금" value={isLoggedIn ? `${fmt(cash)}원` : "-"} tone="neutral" />
          <MetricCard label="미실현손익" value={isLoggedIn ? `${unrealized > 0 ? "+" : ""}${fmt(unrealized)}원` : "-"} tone={unrealized >= 0 ? "up" : "down"} />
          <MetricCard label="수익률" value={isLoggedIn ? `${returnRate > 0 ? "+" : ""}${returnRate.toFixed(2)}%` : "-"} tone={returnRate >= 0 ? "up" : "down"} />
        </div>
      ),
    },
    movers: {
      icon: "↕",
      title: "상승/하락 TOP",
      span: "full",
      sparkline: moversRows.slice(0, 6).map((r) => Number(r.changeRate || 0)),
      body: (
        <div className="home-movers-grid">
          <MoverPanel
            title="상승 TOP"
            tone="up"
            rows={upMovers}
            loading={moversLoading}
            error={moversError}
            fmt={fmt}
            onSelectCode={openTradeFromMarket}
          />
          <MoverPanel
            title="하락 TOP"
            tone="down"
            rows={downMovers}
            loading={moversLoading}
            error={moversError}
            fmt={fmt}
            onSelectCode={openTradeFromMarket}
          />
        </div>
      ),
    },
    watchlist: {
      icon: "★",
      title: "관심종목 & 보유 요약",
      span: "wide",
      sparkline: (portfolio?.holdings || []).slice(0, 6).map((h) => Number(h.unrealizedPnl || 0)),
      body: (
        <>
          {!isLoggedIn && <div className="home-empty">로그인하면 관심종목과 개인 계좌 정보를 볼 수 있습니다.</div>}
          {isLoggedIn && (!watchlistCodes || watchlistCodes.length === 0) && (
            <div className="home-empty">아직 관심종목이 없습니다. 차트 페이지에서 추가해보세요.</div>
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
            <div style={{ marginTop: 14 }}>
              <div className="home-panel-header">
                <strong>보유 종목</strong>
                <button type="button" className="home-link-btn" onClick={() => navigateTo("/sim")}>모의투자에서 보기</button>
              </div>
              <div className="home-mini-table">
                {portfolio.holdings.slice(0, 5).map((h) => (
                  <div key={`hold-${h.code}`} className="home-mini-row">
                    <div>{getStockNameByCode(h.code)} ({h.code})</div>
                    <div>{h.quantity}주</div>
                    <div>{fmt(h.currentPrice || 0)}원</div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </>
      ),
    },
    ranking: {
      icon: "♟",
      title: "수익률 랭킹",
      span: "narrow",
      sparkline: top5.map((r) => Number(r.returnRate || 0)),
      body: (
        <>
          {rankingsLoading && <div className="home-empty">랭킹을 불러오는 중...</div>}
          {!rankingsLoading && top5.length === 0 && <div className="home-empty">랭킹 데이터가 없습니다.</div>}
          {!rankingsLoading && top5.length > 0 && (
            <div className="home-ranking">
              {top5.map((row) => (
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
          <div className="home-note">
            실현손익 {isLoggedIn ? `${realized > 0 ? "+" : ""}${fmt(realized)}원` : "-"} · 보유종목 {portfolio?.holdings?.length || 0}개
          </div>
        </>
      ),
    },
    charts: {
      icon: "◷",
      title: "빠른 차트",
      span: "full",
      sparkline: watchTop.map((_, idx) => idx + 1),
      body: (
        <div className="home-grid home-grid-charts">
          {watchTop.map((code) => (
            <StockChartCard
              key={`home-chart-${code}-${chartEndDate || "today"}`}
              apiBaseUrl={apiBaseUrl}
              code={code}
              months={6}
              endDate={chartEndDate}
              height={240}
              title={`${getStockNameByCode(code)} (${code})`}
              subtitle={`홈 미니차트 · 기준일 ${chartEndDate || "오늘"}`}
              headerActions={
                <button type="button" className="app-nav-btn" onClick={() => openTradeFromMarket?.(code)}>
                  주문
                </button>
              }
            />
          ))}
        </div>
      ),
    },
  };

  return (
    <div style={{ display: "grid", gap: 16 }}>
      <section className="home-hero">
        <div>
          <div className="home-hero-kicker">Mock Investing League</div>
          <h2 className="home-hero-title">
            {isLoggedIn ? `${currentUser?.name || "사용자"}님의 투자 대시보드` : "공용 리그 대시보드"}
          </h2>
          <div className="home-hero-sub">
            공용 기준일 <strong>{leagueState?.currentDate || chartEndDate || "-"}</strong> · 상태{" "}
            <strong>{leagueState?.running ? "진행 중" : "정지"}</strong>
          </div>
        </div>
        <div className="home-hero-actions">
          <button type="button" className="app-nav-btn active" onClick={() => navigateTo("/sim")}>모의투자</button>
          <button type="button" className="app-nav-btn" onClick={() => navigateTo("/charts")}>차트</button>
          <button type="button" className="app-nav-btn" onClick={() => navigateTo("/league")}>리그</button>
          <button type="button" className="app-nav-btn" onClick={() => navigateTo("/market")}>종목정보</button>
          <button type="button" className="app-nav-btn" onClick={() => navigateTo("/news")}>뉴스·공시</button>
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
                  <span className="home-widget-icon" aria-hidden="true">{widget.icon || "•"}</span>
                  <strong>{widget.title}</strong>
                </div>
                <div className="home-widget-actions">
                  <Sparkline values={widget.sparkline || []} className="home-widget-sparkline" />
                  {widgetId === "watchlist" && <button type="button" className="home-link-btn" onClick={() => navigateTo("/charts")}>차트 관리</button>}
                  {widgetId === "ranking" && <button type="button" className="home-link-btn" onClick={() => navigateTo("/league")}>전체 랭킹</button>}
                  {widgetId === "movers" && <button type="button" className="home-link-btn" onClick={() => navigateTo("/market")}>종목정보</button>}
                  <button type="button" className="home-collapse-btn" onClick={() => toggleCollapsed(widgetId)}>
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
    .map((v, i) => {
      const x = (i / (nums.length - 1)) * width;
      const y = height - ((v - min) / range) * height;
      return `${x},${y}`;
    })
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
