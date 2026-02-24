import React, { useEffect, useRef, useState } from "react";
import { createChart } from "lightweight-charts";
import axios from "axios";

function normalize(rows) {
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

export default function StockChartCard({
  apiBaseUrl,
  code,
  months = 6,
  endDate,
  height = 260,
  onLatestPriceChange,
  title,
}) {
  const wrapRef = useRef(null);
  const chartRef = useRef(null);
  const seriesRef = useRef(null);
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
      timeScale: { borderColor: "#e2e8f0" },
    });
    const series = chart.addCandlestickSeries({
      upColor: "#ef4444",
      downColor: "#2563eb",
      wickUpColor: "#ef4444",
      wickDownColor: "#2563eb",
      borderVisible: false,
    });
    chartRef.current = chart;
    seriesRef.current = series;

    const onResize = () => {
      if (!wrapRef.current || !chartRef.current) return;
      chartRef.current.applyOptions({ width: wrapRef.current.clientWidth });
    };
    window.addEventListener("resize", onResize);
    return () => {
      window.removeEventListener("resize", onResize);
      chart.remove();
    };
  }, [height]);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      if (!code || !seriesRef.current) return;
      setLoading(true);
      setError("");
      try {
        const res = await axios.get(`${apiBaseUrl}/api/stock/chart/${code}`, {
          params: { months, endDate: endDate || undefined },
        });
        if (cancelled) return;
        const rows = normalize(res.data);
        if (rows.length === 0) {
          setError("차트 데이터가 없습니다.");
          seriesRef.current.setData([]);
          onLatestPriceChange?.(null);
          return;
        }
        seriesRef.current.setData(rows);
        chartRef.current?.timeScale().fitContent();
        const last = rows[rows.length - 1];
        onLatestPriceChange?.({ price: last.close, time: last.time });
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
  }, [apiBaseUrl, code, months, endDate, onLatestPriceChange]);

  return (
    <div style={{ background: "#fff", borderRadius: 12, border: "1px solid #e2e8f0", padding: 12 }}>
      {title && <div style={{ marginBottom: 8, fontWeight: 700, color: "#0f172a" }}>{title}</div>}
      {loading && <div style={{ marginBottom: 8, color: "#64748b", fontSize: 13 }}>불러오는 중...</div>}
      {error && <div style={{ marginBottom: 8, color: "#dc2626", fontSize: 13 }}>{error}</div>}
      <div ref={wrapRef} style={{ minHeight: height }} />
    </div>
  );
}

