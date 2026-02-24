import React, { useEffect, useMemo, useState } from "react";
import StockChartCard from "../components/StockChartCard";
import { PERIOD_OPTIONS, STOCK_OPTIONS } from "../constants/stocks";

const CARD_PERIOD_PRESETS = [
  { label: "6M", value: 6 },
  { label: "1Y", value: 12 },
  { label: "전체", value: 120 },
];

const CARD_PREFS_KEY = "fintech_chart_card_prefs_v3";

function readCardPrefs() {
  try {
    const raw = localStorage.getItem(CARD_PREFS_KEY);
    if (!raw) return { periods: {}, compares: {}, recentCompares: [] };
    const parsed = JSON.parse(raw);
    return {
      periods: parsed?.periods ?? {},
      compares: parsed?.compares ?? {},
      recentCompares: Array.isArray(parsed?.recentCompares) ? parsed.recentCompares : [],
    };
  } catch {
    return { periods: {}, compares: {}, recentCompares: [] };
  }
}

export default function ChartsPage({
  apiBaseUrl,
  filteredStocks,
  viewCodeSet,
  watchlistCodeSet,
  searchQuery,
  setSearchQuery,
  monthsInput,
  setMonthsInput,
  applyFilter,
  chartDisplayMonths,
  addToView,
  removeFromView,
  reorderViewCodes,
  toggleWatchlist,
  watchlistLoading,
  uiMessage,
  isLoggedIn,
  watchlistCodes,
  applied,
  chartCodes,
  chartGridColumns,
  chartEndDate,
  leagueState,
  getStockNameByCode,
}) {
  const [cardPeriods, setCardPeriods] = useState({});
  const [cardCompareCodes, setCardCompareCodes] = useState({});
  const [recentCompareCodes, setRecentCompareCodes] = useState([]);
  const [leagueBadgePulse, setLeagueBadgePulse] = useState(false);
  const [draggingCode, setDraggingCode] = useState("");

  useEffect(() => {
    const prefs = readCardPrefs();
    setCardPeriods(prefs.periods);
    setCardCompareCodes(prefs.compares);
    setRecentCompareCodes(prefs.recentCompares);
  }, []);

  useEffect(() => {
    try {
      localStorage.setItem(
        CARD_PREFS_KEY,
        JSON.stringify({
          periods: cardPeriods,
          compares: cardCompareCodes,
          recentCompares: recentCompareCodes,
        })
      );
    } catch {}
  }, [cardPeriods, cardCompareCodes, recentCompareCodes]);

  useEffect(() => {
    if (!leagueState?.currentDate) return;
    setLeagueBadgePulse(true);
    const id = window.setTimeout(() => setLeagueBadgePulse(false), 900);
    return () => window.clearTimeout(id);
  }, [leagueState?.currentDate]);

  useEffect(() => {
    setCardPeriods((prev) => {
      const next = { ...prev };
      for (const code of chartCodes) {
        if (!next[code]) next[code] = applied.months;
      }
      return next;
    });
  }, [chartCodes, applied.months]);

  const leagueBadgeClassName = useMemo(
    () => `app-league-badge${leagueBadgePulse ? " app-league-badge-pulse" : ""}`,
    [leagueBadgePulse]
  );

  const prioritizedCompareOptions = useMemo(() => {
    const byCode = new Map(STOCK_OPTIONS.map((s) => [s.code, s]));
    const result = [];
    const used = new Set();

    const push = (code) => {
      if (!code || used.has(code)) return;
      const item = byCode.get(code);
      if (!item) return;
      used.add(code);
      result.push(item);
    };

    recentCompareCodes.forEach(push);
    watchlistCodes.forEach(push);
    STOCK_OPTIONS.forEach((s) => push(s.code));
    return result;
  }, [recentCompareCodes, watchlistCodes]);

  const updateCompareCode = (baseCode, compareCode) => {
    setCardCompareCodes((prev) => ({ ...prev, [baseCode]: compareCode }));
    if (!compareCode) return;
    setRecentCompareCodes((prev) => [compareCode, ...prev.filter((c) => c !== compareCode)].slice(0, 8));
  };

  const getCardMonths = (code) => cardPeriods[code] ?? applied.months;
  const getCardCompareCode = (code) => cardCompareCodes[code] || "";

  return (
    <>
      <div className="app-card" style={cardStyle}>
        <div className="app-toolbar-row" style={{ justifyContent: "space-between", marginBottom: 10 }}>
          <div style={{ fontWeight: 800 }}>차트 조회</div>
          <div className={leagueBadgeClassName}>
            공용 기준일: <strong>{leagueState?.currentDate || chartEndDate || "-"}</strong>
          </div>
        </div>

        {watchlistLoading && <div style={{ color: "#2563eb", fontWeight: 600 }}>관심종목 불러오는 중...</div>}
        {uiMessage && <div style={{ color: "#b91c1c", fontWeight: 600 }}>{uiMessage}</div>}

        <div className="app-toolbar-row" style={{ gap: 10, marginBottom: 10 }}>
          <label style={labelStyle}>종목 검색</label>
          <input value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)} placeholder="종목명 또는 코드" style={inputStyle} />
          <label style={labelStyle}>기본 기간</label>
          <select value={monthsInput} onChange={(e) => setMonthsInput(Number(e.target.value))} style={inputStyle}>
            {PERIOD_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
          <button type="button" onClick={applyFilter} style={primaryBtn}>
            적용
          </button>
        </div>

        <div style={{ color: "#64748b", fontSize: 12, marginBottom: 10 }}>
          카드별 기간(6M/1Y/전체)과 비교 설정은 저장됩니다. 비교 드롭다운은 최근 사용/관심종목이 우선 노출됩니다.
          현재 기본 표시 기간: {chartDisplayMonths}개월
        </div>

        <div style={listWrapStyle}>
          {filteredStocks.map((stock) => {
            const inView = viewCodeSet.has(stock.code);
            const inWatchlist = watchlistCodeSet.has(stock.code);
            return (
              <div key={stock.code} style={{ ...listRowStyle, background: inView ? "#e0f2fe" : "#fff" }}>
                <button type="button" onClick={() => (inView ? removeFromView(stock.code) : addToView(stock.code))} style={listNameBtnStyle(inView)}>
                  {inView ? "제거 " : "+ "} {stock.name} ({stock.code})
                </button>
                <button
                  type="button"
                  onClick={() => toggleWatchlist(stock.code)}
                  disabled={watchlistLoading || !isLoggedIn}
                  style={watchBtnStyle(inWatchlist)}
                >
                  {inWatchlist ? "관심 해제" : "관심 추가"}
                </button>
              </div>
            );
          })}
        </div>
      </div>

      <div className="app-card app-toolbar-row" style={{ ...cardStyle, gap: 8 }}>
        <strong>관심종목</strong>
        {!isLoggedIn && <span style={{ color: "#64748b" }}>로그인 후 개인 관심종목을 사용할 수 있습니다.</span>}
        {isLoggedIn && watchlistCodes.length === 0 && <span style={{ color: "#64748b" }}>등록된 종목이 없습니다.</span>}
        {watchlistCodes.map((code) => (
          <span key={code} style={watchChipStyle}>
            {getStockNameByCode(code)} ({code})
          </span>
        ))}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: chartGridColumns, gap: 16 }}>
        {chartCodes.length === 0 && <div style={cardStyle}>종목을 추가하면 차트가 표시됩니다.</div>}
        {chartCodes.map((code) => {
          const months = getCardMonths(code);
          const compareCode = getCardCompareCode(code);
          const compareLabel = compareCode ? getStockNameByCode(compareCode) : "";
          return (
            <div
              key={`card-wrap-${code}`}
              draggable
              onDragStart={() => setDraggingCode(code)}
              onDragOver={(e) => e.preventDefault()}
              onDrop={(e) => {
                e.preventDefault();
                if (!draggingCode || draggingCode === code) return;
                reorderViewCodes?.(draggingCode, code);
                setDraggingCode("");
              }}
              onDragEnd={() => setDraggingCode("")}
              className={draggingCode === code ? "chart-card-dragging" : "chart-card-draggable"}
            >
              <StockChartCard
                key={`${code}-${months}-${chartEndDate || "today"}-${compareCode || "none"}`}
                apiBaseUrl={apiBaseUrl}
                code={code}
                compareCode={compareCode || undefined}
                compareLabel={compareLabel}
                months={months}
                endDate={chartEndDate}
                height={280}
                title={`${getStockNameByCode(code)} (${code})`}
                subtitle={`표시 ${months === 120 ? "전체" : `${months}개월`} · 기준 ${chartEndDate || "오늘"}`}
                headerActions={
                  <div className="chart-card-controls">
                    <div className="chart-card-chip-group">
                      {CARD_PERIOD_PRESETS.map((preset) => (
                        <button
                          key={`${code}-period-${preset.value}`}
                          type="button"
                          className={months === preset.value ? "chart-card-chip active" : "chart-card-chip"}
                          onClick={() => setCardPeriods((prev) => ({ ...prev, [code]: preset.value }))}
                        >
                          {preset.label}
                        </button>
                      ))}
                    </div>
                    <div className="chart-card-compare-row">
                      <span className="chart-card-drag-handle" title="드래그해서 카드 순서 변경">↕</span>
                      <label className="chart-card-compare-label">비교</label>
                      <select
                        className="chart-card-compare-select"
                        value={compareCode}
                        onChange={(e) => updateCompareCode(code, e.target.value)}
                      >
                        <option value="">없음</option>
                        {prioritizedCompareOptions
                          .filter((s) => s.code !== code)
                          .map((s) => (
                            <option key={`${code}-cmp-${s.code}`} value={s.code}>
                              {s.name} ({s.code})
                            </option>
                          ))}
                      </select>
                    </div>
                  </div>
                }
              />
            </div>
          );
        })}
      </div>
    </>
  );
}

const cardStyle = {
  marginBottom: 20,
  padding: 15,
  background: "#fff",
  borderRadius: 10,
  border: "1px solid #e5e7eb",
  boxShadow: "0 2px 4px rgba(0,0,0,0.04)",
};
const labelStyle = { fontWeight: 700 };
const inputStyle = { padding: 10, borderRadius: 6, border: "1px solid #d1d5db", minWidth: 140 };
const primaryBtn = { padding: "10px 14px", border: "none", borderRadius: 8, background: "#2563eb", color: "#fff", fontWeight: 700 };
const listWrapStyle = { width: "100%", maxHeight: 220, overflowY: "auto", border: "1px solid #e5e7eb", borderRadius: 8, background: "#fcfcfd" };
const listRowStyle = { display: "flex", justifyContent: "space-between", gap: 8, padding: "8px 10px", borderBottom: "1px solid #f1f5f9" };
const listNameBtnStyle = (inView) => ({ border: "none", background: "transparent", cursor: "pointer", textAlign: "left", color: inView ? "#0369a1" : "#111827", fontWeight: 600 });
const watchBtnStyle = (inWatchlist) => ({ border: "1px solid #d1d5db", borderRadius: 6, background: inWatchlist ? "#fff7ed" : "#fff", padding: "2px 8px" });
const watchChipStyle = { padding: "6px 10px", border: "1px solid #d1d5db", borderRadius: 999, background: "#fff" };

