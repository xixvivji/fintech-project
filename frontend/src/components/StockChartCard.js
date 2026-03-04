import React, { useCallback, useEffect, useRef, useState } from "react";
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

function toPeriodStart(time, period) {
  const d = new Date(`${time}T00:00:00`);
  if (Number.isNaN(d.getTime())) return time;
  if (period === "DAY") return time;
  if (period === "WEEK") {
    const day = d.getDay();
    const diff = day === 0 ? -6 : 1 - day;
    d.setDate(d.getDate() + diff);
    return d.toISOString().slice(0, 10);
  }
  if (period === "MONTH") {
    d.setDate(1);
    return d.toISOString().slice(0, 10);
  }
  if (period === "YEAR") {
    d.setMonth(0, 1);
    return d.toISOString().slice(0, 10);
  }
  return time;
}

function aggregateCandles(rows, period) {
  const normalized = normalizeCandles(rows);
  if (period === "DAY") return normalized;
  const grouped = new Map();
  for (const c of normalized) {
    const key = toPeriodStart(c.time, period);
    const prev = grouped.get(key);
    if (!prev) {
      grouped.set(key, { time: key, open: c.open, high: c.high, low: c.low, close: c.close });
      continue;
    }
    prev.high = Math.max(prev.high, c.high);
    prev.low = Math.min(prev.low, c.low);
    prev.close = c.close;
  }
  return Array.from(grouped.values()).sort((a, b) => String(a.time).localeCompare(String(b.time)));
}

function formatKoreanDate(time) {
  if (!time) return "-";
  const d = new Date(`${time}T00:00:00`);
  if (Number.isNaN(d.getTime())) return String(time);
  return d.toLocaleDateString("ko-KR", { year: "numeric", month: "2-digit", day: "2-digit" });
}

function fmtPrice(v) {
  if (!Number.isFinite(Number(v))) return "-";
  return new Intl.NumberFormat("ko-KR").format(Math.round(Number(v)));
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
  const mainRowsRef = useRef([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [period, setPeriod] = useState("DAY");
  const [hoverInfo, setHoverInfo] = useState(null);

  const applyMainSeriesData = useCallback((rows) => {
    if (!mainSeriesRef.current) return;
    if (compareCode) {
      mainSeriesRef.current.setData(toRelativeSeries(rows));
      return;
    }
    mainSeriesRef.current.setData(aggregateCandles(rows, period));
  }, [compareCode, period]);

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

    chart.subscribeCrosshairMove((param) => {
      if (!param?.time || !mainSeriesRef.current) {
        setHoverInfo(null);
        return;
      }
      const point = param.seriesData.get(mainSeriesRef.current);
      if (!point || compareCode) {
        setHoverInfo(null);
        return;
      }
      const time = typeof param.time === "string"
        ? param.time
        : `${param.time.year}-${String(param.time.month).padStart(2, "0")}-${String(param.time.day).padStart(2, "0")}`;
      if (![point.open, point.high, point.low, point.close].every((x) => Number.isFinite(Number(x)))) {
        setHoverInfo(null);
        return;
      }
      setHoverInfo({
        time,
        open: Number(point.open),
        high: Number(point.high),
        low: Number(point.low),
        close: Number(point.close),
      });
    });

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
          mainRowsRef.current = normalizeCandles(mainRes?.data);
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
          mainRowsRef.current = rows;
          if (!rows.length) {
            setError("차트 데이터가 없습니다.");
            mainSeriesRef.current.setData([]);
            onLatestPriceChange?.(null);
            return;
          }
          const shown = aggregateCandles(rows, period);
          mainSeriesRef.current.setData(shown);
          const last = shown[shown.length - 1];
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
  }, [apiBaseUrl, code, months, endDate, onLatestPriceChange, compareCode, period]);

  useEffect(() => {
    if (compareCode) return;
    const rows = mainRowsRef.current || [];
    if (!rows.length) return;
    applyMainSeriesData(rows);
    chartRef.current?.timeScale().fitContent();
  }, [period, compareCode, applyMainSeriesData]);

  useEffect(() => {
    if (!apiBaseUrl || !code || endDate) return undefined;
    let cancelled = false;

    const updateSeriesWithRealtime = async () => {
      try {
        const res = await axios.get(`${apiBaseUrl}/api/stock/quote/${code}`);
        if (cancelled) return;
        const price = Number(res?.data?.price);
        if (!Number.isFinite(price) || price <= 0) return;

        const rows = Array.isArray(mainRowsRef.current) ? [...mainRowsRef.current] : [];
        if (!rows.length) return;
        const last = rows[rows.length - 1];
        const updated = {
          ...last,
          close: price,
          high: Math.max(Number(last.high || price), price),
          low: Math.min(Number(last.low || price), price),
        };
        rows[rows.length - 1] = updated;
        mainRowsRef.current = rows;

        if (compareCode) {
          const rel = toRelativeSeries(rows);
          mainSeriesRef.current?.setData(rel);
        } else {
          applyMainSeriesData(rows);
        }
        onLatestPriceChange?.({ price, time: updated.time });
      } catch (_) {
        // Ignore intermittent quote failures.
      }
    };

    const timer = window.setInterval(updateSeriesWithRealtime, 2000);
    updateSeriesWithRealtime();
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [apiBaseUrl, code, endDate, compareCode, onLatestPriceChange, applyMainSeriesData]);

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
      {!compareCode && (
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, marginBottom: 8, flexWrap: "wrap" }}>
          <div style={{ fontSize: 12, color: "#334155", display: "flex", gap: 10, flexWrap: "wrap" }}>
            <span>날짜: <strong>{formatKoreanDate(hoverInfo?.time)}</strong></span>
            <span>시가: <strong>{fmtPrice(hoverInfo?.open)}</strong></span>
            <span>고가: <strong>{fmtPrice(hoverInfo?.high)}</strong></span>
            <span>저가: <strong>{fmtPrice(hoverInfo?.low)}</strong></span>
            <span>종가: <strong>{fmtPrice(hoverInfo?.close)}</strong></span>
          </div>
          <div style={{ display: "inline-flex", border: "1px solid #dbe2ea", borderRadius: 999, overflow: "hidden" }}>
            {[
              { key: "DAY", label: "일" },
              { key: "WEEK", label: "주" },
              { key: "MONTH", label: "월" },
              { key: "YEAR", label: "년" },
            ].map((x) => (
              <button
                key={x.key}
                type="button"
                onClick={() => setPeriod(x.key)}
                style={{
                  border: "none",
                  padding: "4px 10px",
                  background: period === x.key ? "#0f172a" : "#fff",
                  color: period === x.key ? "#fff" : "#334155",
                  cursor: "pointer",
                  fontSize: 12,
                  fontWeight: 700,
                }}
              >
                {x.label}
              </button>
            ))}
          </div>
        </div>
      )}
      {loading && <div style={{ marginBottom: 8, color: "#64748b", fontSize: 13 }}>불러오는 중...</div>}
      {error && <div style={{ marginBottom: 8, color: "#dc2626", fontSize: 13 }}>{error}</div>}
      <div ref={wrapRef} style={{ minHeight: height, cursor: "crosshair" }} />
    </div>
  );
}
