import React, { useMemo, useState } from "react";
import StockChartCard from "../components/StockChartCard";
import { STOCK_OPTIONS } from "../constants/stocks";

export default function MarketPage({
  apiBaseUrl,
  chartEndDate,
  watchlistCodeSet,
  toggleWatchlist,
  watchlistLoading,
  isLoggedIn,
  getStockNameByCode,
  selectedTradeCode,
  openTradeFromMarket,
  fmt,
}) {
  const [code, setCode] = useState(selectedTradeCode || "005930");
  const [latestPrice, setLatestPrice] = useState(null);

  const stock = useMemo(() => STOCK_OPTIONS.find((s) => s.code === code), [code]);
  const isWatch = Boolean(watchlistCodeSet?.has(code));

  const mockInfo = useMemo(() => {
    const seed = Number(code) % 100;
    return {
      sector: ["반도체", "플랫폼", "자동차", "바이오", "에너지", "금융"][seed % 6],
      marketCap: (seed + 40) * 1_000_000_000_000,
      listedAt: `${2010 + (seed % 15)}-${String((seed % 12) + 1).padStart(2, "0")}-${String((seed % 27) + 1).padStart(2, "0")}`,
      note: "과거 기준일과 연동되는 종목정보 예시 페이지입니다.",
    };
  }, [code]);

  return (
    <div className="app-card" style={{ padding: 16 }}>
      <h3 style={{ marginTop: 0, marginBottom: 12 }}>종목정보</h3>

      <div className="app-toolbar-row" style={{ marginBottom: 12 }}>
        <label>종목</label>
        <select value={code} onChange={(e) => setCode(e.target.value)}>
          {STOCK_OPTIONS.map((s) => (
            <option key={s.code} value={s.code}>
              {s.name} ({s.code})
            </option>
          ))}
        </select>
        <button type="button" onClick={() => openTradeFromMarket?.(code)} className="app-nav-btn">
          모의투자 주문으로
        </button>
        <button
          type="button"
          onClick={() => toggleWatchlist?.(code)}
          disabled={!isLoggedIn || watchlistLoading}
          className={isWatch ? "app-nav-btn active" : "app-nav-btn"}
        >
          {isWatch ? "관심 해제" : "관심 추가"}
        </button>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 10, marginBottom: 12 }}>
        <InfoBox label="종목명" value={stock?.name || code} />
        <InfoBox label="종목코드" value={code} />
        <InfoBox label="기준일" value={chartEndDate || "오늘"} />
        <InfoBox label="현재가(기준일 종가)" value={latestPrice?.price ? `${fmt(latestPrice.price)}원` : "-"} />
        <InfoBox label="업종" value={mockInfo.sector} />
        <InfoBox label="시가총액(모의)" value={`${fmt(mockInfo.marketCap)}원`} />
        <InfoBox label="상장일(참고)" value={mockInfo.listedAt} />
        <InfoBox label="메모" value={mockInfo.note} />
      </div>

      <StockChartCard
        apiBaseUrl={apiBaseUrl}
        code={code}
        months={12}
        endDate={chartEndDate}
        height={320}
        title={`${getStockNameByCode(code)} (${code})`}
        subtitle={`기준일 ${chartEndDate || "오늘"} · 1년 차트`}
        onLatestPriceChange={setLatestPrice}
      />
    </div>
  );
}

function InfoBox({ label, value }) {
  return (
    <div className="app-card" style={{ padding: 12, border: "1px solid #e5e7eb" }}>
      <div style={{ color: "#64748b", fontSize: 12, marginBottom: 4 }}>{label}</div>
      <div style={{ fontWeight: 700, wordBreak: "keep-all" }}>{value}</div>
    </div>
  );
}
