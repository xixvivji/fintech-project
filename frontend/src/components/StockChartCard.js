import React, { useEffect, useRef, useState } from "react";
import { createChart } from "lightweight-charts";
import axios from "axios";

function normalizeCandles(rows) {
  if (!Array.isArray(rows)) return [];
  return rows
    .map((r) => ({
      time: r?.time,
      open: Number(r?.open),
      high: Number(r?.high),
      low: Number(r?.low),
      close: Number(r?.close),
    }))
    .filter((r) => r.time && [r.open, r.high, r.low, r.close].every(Number.isFinite));
}

function toRelativeSeries(rows) {
  const candles = normalizeCandles(rows);
  if (!candles.length) return [];
  const base = candles[0].close || 1;
  return candles.map((r) => ({
    time: r.time,
    value: ((r.close / base) - 1) * 100,
  }));
}

export default function StockChartCard({
  apiBaseUrl,
  code,
  months = 6,
  endDate,
  height = 280,
  onLatestPriceChange,
  title,
  subtitle,
  headerActions,
  compareCode,
  compareLabel,
}) {
  const wrapRef = useRef(null);
  const chartRef = useRef(null);
  const mainSeriesRef = useRef(null);
  const compareSeriesRef = useRef(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!wrapRef.current) return;
    const chart = createChart(wrapRef.current, {
      width: wrapRef.current.clientWidth,
      height,
      layout: { background: { color: "#fff" }, textColor: "#475569" },
      grid: { vertLines: { color: "#eef2f7" }, horzLines: { color: "#eef2f7" } },
      rightPriceScale: { borderColor: "#e2e8f0" },
      leftPriceScale: { visible: Boolean(compareCode), borderColor: "#e2e8f0" },
      timeScale: { borderColor: "#e2e8f0", timeVisible: true },
      crosshair: { mode: 1 },
      handleScroll: { mouseWheel: true, pressedMouseMove: true, horzTouchDrag: true, vertTouchDrag: true },
      handleScale: { mouseWheel: true, pinch: true, axisPressedMouseMove: true },
    });

    const mainSeries = compareCode
      ? chart.addLineSeries({ color: "#2563eb", lineWidth: 2, priceScaleId: "right" })
      : chart.addCandlestickSeries({
          upColor: "#ef4444",
          downColor: "#2563eb",
          wickUpColor: "#ef4444",
          wickDownColor: "#2563eb",
          borderVisible: false,
        });

    let compareSeries = null;
    if (compareCode) {
      compareSeries = chart.addLineSeries({ color: "#f59e0b", lineWidth: 2, priceScaleId: "left" });
      chart.priceScale("left").applyOptions({ visible: true, scaleMargins: { top: 0.1, bottom: 0.1 } });
      chart.priceScale("right").applyOptions({ visible: true, scaleMargins: { top: 0.1, bottom: 0.1 } });
    }

    chartRef.current = chart;
    mainSeriesRef.current = mainSeries;
    compareSeriesRef.current = compareSeries;

    const onResize = () => {
      if (!wrapRef.current || !chartRef.current) return;
      chartRef.current.applyOptions({ width: wrapRef.current.clientWidth });
    };
    window.addEventListener("resize", onResize);
    return () => {
      window.removeEventListener("resize", onResize);
      chart.remove();
    };
  }, [height, compareCode]);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      if (!code || !mainSeriesRef.current) return;
      setLoading(true);
      setError("");
      try {
        const mainReq = axios.get(`${apiBaseUrl}/api/stock/chart/${code}`, {
          params: { months, endDate: endDate || undefined },
        });
        const compareReq = compareCode
          ? axios.get(`${apiBaseUrl}/api/stock/chart/${compareCode}`, { params: { months, endDate: endDate || undefined } })
          : Promise.resolve(null);
        const [mainRes, compareRes] = await Promise.all([mainReq, compareReq]);
        if (cancelled) return;

        if (compareCode) {
          const mainRel = toRelativeSeries(mainRes?.data);
          const compareRel = toRelativeSeries(compareRes?.data);
          if (!mainRel.length) {
            setError("비교용 차트 데이터가 없습니다.");
            mainSeriesRef.current.setData([]);
            compareSeriesRef.current?.setData([]);
            onLatestPriceChange?.(null);
            return;
          }
          mainSeriesRef.current.setData(mainRel);
          compareSeriesRef.current?.setData(compareRel);
          const rawMain = normalizeCandles(mainRes?.data);
          const last = rawMain[rawMain.length - 1];
          onLatestPriceChange?.(last ? { price: last.close, time: last.time } : null);
        } else {
          const rows = normalizeCandles(mainRes?.data);
          if (!rows.length) {
            setError("차트 데이터가 없습니다.");
            mainSeriesRef.current.setData([]);
            onLatestPriceChange?.(null);
            return;
          }
          mainSeriesRef.current.setData(rows);
          const last = rows[rows.length - 1];
          onLatestPriceChange?.({ price: last.close, time: last.time });
        }

        chartRef.current?.timeScale().fitContent();
      } catch (e) {
        if (cancelled) return;
        setError(e?.response?.data?.message || "차트 조회에 실패했습니다.");
        onLatestPriceChange?.(null);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [apiBaseUrl, code, months, endDate, onLatestPriceChange, compareCode]);

  return (
    <div style={{ background: "#fff", borderRadius: 12, border: "1px solid #e2e8f0", padding: 12 }}>
      {(title || headerActions) && (
        <div style={{ display: "flex", justifyContent: "space-between", gap: 8, alignItems: "flex-start", marginBottom: 8, flexWrap: "wrap" }}>
          <div>
            {title && <div style={{ fontWeight: 700, color: "#0f172a" }}>{title}</div>}
            {subtitle && <div style={{ color: "#64748b", fontSize: 12, marginTop: 2 }}>{subtitle}</div>}
            {compareCode && (
              <div style={{ color: "#64748b", fontSize: 12, marginTop: 2 }}>
                비교 모드: {code} vs {compareLabel || compareCode} (상대수익률 %)
              </div>
            )}
          </div>
          {headerActions}
        </div>
      )}
      {loading && <div style={{ marginBottom: 8, color: "#64748b", fontSize: 13 }}>불러오는 중...</div>}
      {error && <div style={{ marginBottom: 8, color: "#dc2626", fontSize: 13 }}>{error}</div>}
      <div ref={wrapRef} style={{ minHeight: height, cursor: "crosshair" }} />
    </div>
  );
}

