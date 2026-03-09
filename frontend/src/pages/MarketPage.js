import React, { useEffect, useMemo, useState } from "react";
import axios from "axios";
import MinuteChartCard from "../components/MinuteChartCard";
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
  const [orderbook, setOrderbook] = useState(null);
  const [orderbookLoading, setOrderbookLoading] = useState(false);
  const [orderbookError, setOrderbookError] = useState("");

  const stock = useMemo(() => STOCK_OPTIONS.find((s) => s.code === code), [code]);
  const isWatch = Boolean(watchlistCodeSet?.has(code));

  const mockInfo = useMemo(() => {
    const seed = Number(code) % 100;
    return {
      sector: ["Semiconductor", "Auto", "Bio", "Display", "Energy", "Finance"][seed % 6],
      marketCap: (seed + 40) * 1_000_000_000_000,
      listedAt: `${2010 + (seed % 15)}-${String((seed % 12) + 1).padStart(2, "0")}-${String((seed % 27) + 1).padStart(2, "0")}`,
      note: "Mock profile info for the selected stock.",
    };
  }, [code]);

  useEffect(() => {
    if (!apiBaseUrl || !code) return undefined;
    let cancelled = false;

    const loadOrderbook = async (first = false) => {
      if (first) setOrderbookLoading(true);
      try {
        const res = await axios.get(`${apiBaseUrl}/api/stock/orderbook/${code}`);
        if (cancelled) return;
        setOrderbook(res?.data || null);
        setOrderbookError("");
      } catch (err) {
        if (cancelled) return;
        setOrderbookError(err?.response?.data?.message || err?.message || "Failed to load orderbook.");
      } finally {
        if (first && !cancelled) setOrderbookLoading(false);
      }
    };

    loadOrderbook(true);
    const timer = window.setInterval(() => {
      if (!document.hidden) loadOrderbook(false);
    }, 2000);

    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [apiBaseUrl, code]);

  return (
    <div className="app-card" style={{ padding: 16 }}>
      <h3 style={{ marginTop: 0, marginBottom: 12 }}>Market</h3>

      <div className="app-toolbar-row" style={{ marginBottom: 12 }}>
        <label>Stock</label>
        <select value={code} onChange={(e) => setCode(e.target.value)}>
          {STOCK_OPTIONS.map((s) => (
            <option key={s.code} value={s.code}>
              {s.name} ({s.code})
            </option>
          ))}
        </select>
        <button type="button" onClick={() => openTradeFromMarket?.(code)} className="app-nav-btn">
          Open Trade
        </button>
        <button
          type="button"
          onClick={() => toggleWatchlist?.(code)}
          disabled={!isLoggedIn || watchlistLoading}
          className={isWatch ? "app-nav-btn active" : "app-nav-btn"}
        >
          {isWatch ? "Watchlisted" : "Add Watchlist"}
        </button>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 10, marginBottom: 12 }}>
        <InfoBox label="Name" value={stock?.name || code} />
        <InfoBox label="Code" value={code} />
        <InfoBox label="Base Date" value={chartEndDate || "Today"} />
        <InfoBox label="Current Price" value={latestPrice?.price ? `${fmt(latestPrice.price)}원` : "-"} />
        <InfoBox label="Sector" value={mockInfo.sector} />
        <InfoBox label="Market Cap" value={`${fmt(mockInfo.marketCap)}원`} />
        <InfoBox label="Listed" value={mockInfo.listedAt} />
        <InfoBox label="Note" value={mockInfo.note} />
      </div>

      <MinuteChartCard
        apiBaseUrl={apiBaseUrl}
        code={code}
        
        
        
        height={320}
        title={`${getStockNameByCode(code)} (${code})`}
        subtitle={`1Y chart · base ${chartEndDate || "today"} · quote refresh 1m`}
        onLatestPriceChange={setLatestPrice}
      />

      <div className="app-card" style={{ marginTop: 12, padding: 12 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
          <div style={{ fontWeight: 700 }}>Orderbook / Execution Strength</div>
          {orderbook?.time ? <div style={{ fontSize: 12, color: "#64748b" }}>Updated: {orderbook.time}</div> : null}
        </div>

        <div style={{ display: "flex", gap: 16, flexWrap: "wrap", marginTop: 8, fontSize: 13, color: "#334155" }}>
          <span>Price: <strong>{orderbook?.currentPrice != null ? `${fmt(orderbook.currentPrice)}원` : "-"}</strong></span>
          <span>Total Ask: <strong>{orderbook?.totalAskQty != null ? fmt(orderbook.totalAskQty) : "-"}</strong></span>
          <span>Total Bid: <strong>{orderbook?.totalBidQty != null ? fmt(orderbook.totalBidQty) : "-"}</strong></span>
          <span>Strength: <strong>{orderbook?.executionStrength != null ? `${Number(orderbook.executionStrength).toFixed(2)}%` : "-"}</strong></span>
        </div>

        {orderbookLoading && <div style={{ marginTop: 8, color: "#64748b" }}>Loading orderbook...</div>}
        {orderbookError && <div style={{ marginTop: 8, color: "#dc2626", fontSize: 12 }}>{orderbookError}</div>}

        <div style={{ overflowX: "auto", marginTop: 10 }}>
          <table className="sim-order-table" style={{ minWidth: 520 }}>
            <thead>
              <tr>
                <th className="num">Ask Qty</th>
                <th className="num">Ask Price</th>
                <th className="num">Bid Price</th>
                <th className="num">Bid Qty</th>
              </tr>
            </thead>
            <tbody>
              {(orderbook?.levels || []).slice(0, 5).map((lv) => (
                <tr key={`market-ob-${lv.level}`}>
                  <td className="num down">{lv.askQty != null ? fmt(lv.askQty) : "-"}</td>
                  <td className="num down">{lv.askPrice != null ? fmt(lv.askPrice) : "-"}</td>
                  <td className="num up">{lv.bidPrice != null ? fmt(lv.bidPrice) : "-"}</td>
                  <td className="num up">{lv.bidQty != null ? fmt(lv.bidQty) : "-"}</td>
                </tr>
              ))}
              {(!orderbook?.levels || orderbook.levels.length === 0) && (
                <tr>
                  <td colSpan={4} style={{ textAlign: "center", color: "#64748b" }}>No orderbook data.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
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
