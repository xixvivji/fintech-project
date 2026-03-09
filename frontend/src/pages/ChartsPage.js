import React, { useEffect, useMemo, useState } from "react";
import StockChartCard from "../components/StockChartCard";
import MinuteChartCard from "../components/MinuteChartCard";
import { PERIOD_OPTIONS, STOCK_OPTIONS } from "../constants/stocks";

const CARD_PERIOD_PRESETS = [
  { label: "6M", value: 6 },
  { label: "1Y", value: 12 },
  { label: "ALL", value: 120 },
];

const CARD_PREFS_KEY = "fintech_chart_card_prefs_v3";
const CHART_MODE_KEY = "fintech_chart_mode_v1";

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

function readChartMode() {
  try {
    const mode = localStorage.getItem(CHART_MODE_KEY);
    return mode === "MINUTE" ? "MINUTE" : "DAILY";
  } catch {
    return "DAILY";
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
  const [chartMode, setChartMode] = useState(() => readChartMode());

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
    try {
      localStorage.setItem(CHART_MODE_KEY, chartMode);
    } catch {}
  }, [chartMode]);

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
          <div style={{ fontWeight: 800 }}>Chart View</div>
          <div className={leagueBadgeClassName}>
            Trading Date <strong>{leagueState?.currentDate || chartEndDate || "-"}</strong>
          </div>
        </div>

        {watchlistLoading && <div style={{ color: "#2563eb", fontWeight: 600 }}>Loading watchlist...</div>}
        {uiMessage && <div style={{ color: "#b91c1c", fontWeight: 600 }}>{uiMessage}</div>}

        <div className="app-toolbar-row" style={{ gap: 10, marginBottom: 10, flexWrap: "wrap" }}>
          <label style={labelStyle}>Stock Search</label>
          <input
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Name or code"
            style={inputStyle}
          />
          <label style={labelStyle}>Default Period</label>
          <select value={monthsInput} onChange={(e) => setMonthsInput(Number(e.target.value))} style={inputStyle}>
            {PERIOD_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
          <button type="button" onClick={applyFilter} style={primaryBtn}>
            Apply
          </button>

          <div style={{ marginLeft: "auto", display: "inline-flex", border: "1px solid #d1d5db", borderRadius: 8, overflow: "hidden" }}>
            <button
              type="button"
              onClick={() => setChartMode("DAILY")}
              style={modeBtnStyle(chartMode === "DAILY")}
            >
              Daily
            </button>
            <button
              type="button"
              onClick={() => setChartMode("MINUTE")}
              style={modeBtnStyle(chartMode === "MINUTE")}
            >
              1m
            </button>
          </div>
        </div>

        <div style={{ color: "#64748b", fontSize: 12, marginBottom: 10 }}>
          Card period and compare settings are kept per code. Compare is available in Daily mode only.
          Current default period: {chartDisplayMonths} months.
        </div>

        <div style={listWrapStyle}>
          {filteredStocks.map((stock) => {
            const inView = viewCodeSet.has(stock.code);
            const inWatchlist = watchlistCodeSet.has(stock.code);
            return (
              <div key={stock.code} style={{ ...listRowStyle, background: inView ? "#e0f2fe" : "#fff" }}>
                <button type="button" onClick={() => (inView ? removeFromView(stock.code) : addToView(stock.code))} style={listNameBtnStyle(inView)}>
                  {inView ? "Remove" : "Add"} {stock.name} ({stock.code})
                </button>
                <button
                  type="button"
                  onClick={() => toggleWatchlist(stock.code)}
                  disabled={watchlistLoading || !isLoggedIn}
                  style={watchBtnStyle(inWatchlist)}
                >
                  {inWatchlist ? "Unwatch" : "Watch"}
                </button>
              </div>
            );
          })}
        </div>
      </div>

      <div className="app-card app-toolbar-row" style={{ ...cardStyle, gap: 8 }}>
        <strong>Watchlist</strong>
        {!isLoggedIn && <span style={{ color: "#64748b" }}>Log in to use personal watchlist.</span>}
        {isLoggedIn && watchlistCodes.length === 0 && <span style={{ color: "#64748b" }}>No watchlist items.</span>}
        {watchlistCodes.map((code) => (
          <span key={code} style={watchChipStyle}>
            {getStockNameByCode(code)} ({code})
          </span>
        ))}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: chartGridColumns, gap: 16 }}>
        {chartCodes.length === 0 && <div style={cardStyle}>Add stocks to view chart cards.</div>}
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
              {chartMode === "MINUTE" ? (
                <MinuteChartCard
                  key={`${code}-minute`}
                  apiBaseUrl={apiBaseUrl}
                  code={code}
                  height={280}
                  title={`${getStockNameByCode(code)} (${code})`}
                  subtitle="Realtime 1-minute candles"
                />
              ) : (
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
                  subtitle={`Range ${months === 120 ? "ALL" : `${months}M`} / base date ${chartEndDate || "today"}`}
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
                        <span className="chart-card-drag-handle" title="Drag to reorder cards">
                          ::
                        </span>
                        <label className="chart-card-compare-label">Compare</label>
                        <select
                          className="chart-card-compare-select"
                          value={compareCode}
                          onChange={(e) => updateCompareCode(code, e.target.value)}
                        >
                          <option value="">None</option>
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
              )}
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
const modeBtnStyle = (active) => ({
  border: "none",
  padding: "8px 12px",
  background: active ? "#0f172a" : "#fff",
  color: active ? "#fff" : "#334155",
  fontWeight: 700,
  cursor: "pointer",
});
const listWrapStyle = { width: "100%", maxHeight: 220, overflowY: "auto", border: "1px solid #e5e7eb", borderRadius: 8, background: "#fcfcfd" };
const listRowStyle = { display: "flex", justifyContent: "space-between", gap: 8, padding: "8px 10px", borderBottom: "1px solid #f1f5f9" };
const listNameBtnStyle = (inView) => ({ border: "none", background: "transparent", cursor: "pointer", textAlign: "left", color: inView ? "#0369a1" : "#111827", fontWeight: 600 });
const watchBtnStyle = (inWatchlist) => ({ border: "1px solid #d1d5db", borderRadius: 6, background: inWatchlist ? "#fff7ed" : "#fff", padding: "2px 8px" });
const watchChipStyle = { padding: "6px 10px", border: "1px solid #d1d5db", borderRadius: 999, background: "#fff" };
