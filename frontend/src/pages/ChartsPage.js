import React from "react";
import StockChartCard from "../components/StockChartCard";
import { PERIOD_OPTIONS } from "../constants/stocks";

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
  addToView,
  removeFromView,
  toggleWatchlist,
  watchlistLoading,
  uiMessage,
  isLoggedIn,
  watchlistCodes,
  applied,
  chartCodes,
  chartGridColumns,
  chartEndDate,
  getStockNameByCode,
}) {
  return (
    <>
      <div className="app-card" style={cardStyle}>
        {watchlistLoading && <div style={{ color: "#2563eb", fontWeight: 600 }}>관심종목 불러오는 중...</div>}
        {uiMessage && <div style={{ color: "#b91c1c", fontWeight: 600 }}>{uiMessage}</div>}
        <div className="app-toolbar-row" style={{ display: "flex", flexWrap: "wrap", gap: 10, alignItems: "center" }}>
          <label style={labelStyle}>종목 검색</label>
          <input value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)} placeholder="종목명 또는 코드" style={inputStyle} />
          <label style={labelStyle}>기간</label>
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

        <div style={{ width: "100%", maxHeight: 220, overflowY: "auto", border: "1px solid #e5e7eb", borderRadius: 8, background: "#fcfcfd" }}>
          {filteredStocks.map((stock) => {
            const inView = viewCodeSet.has(stock.code);
            const inWatchlist = watchlistCodeSet.has(stock.code);
            return (
              <div
                key={stock.code}
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  gap: 8,
                  padding: "8px 10px",
                  borderBottom: "1px solid #f1f5f9",
                  background: inView ? "#e0f2fe" : "#fff",
                }}
              >
                <button
                  type="button"
                  onClick={() => (inView ? removeFromView(stock.code) : addToView(stock.code))}
                  style={listNameBtnStyle(inView)}
                >
                  {inView ? "제거 " : "+ "} {stock.name} ({stock.code})
                </button>
                <button
                  type="button"
                  onClick={() => toggleWatchlist(stock.code)}
                  disabled={watchlistLoading || !isLoggedIn}
                  style={{
                    border: "1px solid #d1d5db",
                    borderRadius: 6,
                    background: inWatchlist ? "#fff7ed" : "#fff",
                    padding: "2px 8px",
                  }}
                >
                  {inWatchlist ? "관심 해제" : "관심 추가"}
                </button>
              </div>
            );
          })}
        </div>
      </div>

      <div className="app-card app-toolbar-row" style={{ ...cardStyle, display: "flex", flexWrap: "wrap", gap: 8, alignItems: "center" }}>
        <strong>관심종목</strong>
        {!isLoggedIn && <span style={{ color: "#64748b" }}>로그인 후 개인 관심종목을 사용할 수 있습니다.</span>}
        {isLoggedIn && watchlistCodes.length === 0 && <span style={{ color: "#64748b" }}>등록된 종목이 없습니다.</span>}
        {watchlistCodes.map((code) => (
          <span key={code} style={{ padding: "6px 10px", border: "1px solid #d1d5db", borderRadius: 999, background: "#fff" }}>
            {getStockNameByCode(code)} ({code})
          </span>
        ))}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: chartGridColumns, gap: 16 }}>
        {chartCodes.length === 0 && <div style={cardStyle}>종목을 추가하면 차트가 표시됩니다.</div>}
        {chartCodes.map((code) => (
          <StockChartCard
            key={`${code}-${applied.months}-${chartEndDate || "today"}`}
            apiBaseUrl={apiBaseUrl}
            code={code}
            months={applied.months}
            endDate={chartEndDate}
            title={`${getStockNameByCode(code)} (${code})`}
          />
        ))}
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
const listNameBtnStyle = (inView) => ({
  border: "none",
  background: "transparent",
  cursor: "pointer",
  textAlign: "left",
  color: inView ? "#0369a1" : "#111827",
  fontWeight: 600,
});
