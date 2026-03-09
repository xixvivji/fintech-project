import React, { useCallback, useEffect, useRef, useState } from "react";
import { createChart } from "lightweight-charts";
import axios from "axios";

const MIN_HISTORY_DATE = "2015-01-01";
const LEFT_EDGE_LOAD_THRESHOLD = 20;
const REALTIME_POLL_MS = 3000;
const CHART_CACHE_TTL_MS = 30 * 1000;
const chartDataCache = new Map();
const inflightChartRequests = new Map();

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

function mergeCandles(existingRows, incomingRows) {
  const merged = new Map();
  for (const row of normalizeCandles(existingRows)) {
    merged.set(row.time, row);
  }
  for (const row of normalizeCandles(incomingRows)) {
    merged.set(row.time, row);
  }
  return Array.from(merged.values()).sort((a, b) => String(a.time).localeCompare(String(b.time)));
}

function buildChartCacheKey(apiBaseUrl, code, months, endDate) {
  return `${apiBaseUrl}|${code}|${months}|${endDate || "today"}`;
}

async function fetchChartData(apiBaseUrl, code, months, endDate) {
  const key = buildChartCacheKey(apiBaseUrl, code, months, endDate);
  const now = Date.now();
  const cached = chartDataCache.get(key);
  if (cached && now - cached.ts < CHART_CACHE_TTL_MS) {
    return cached.rows;
  }

  const inflight = inflightChartRequests.get(key);
  if (inflight) return inflight;

  const req = axios
    .get(`${apiBaseUrl}/api/stock/chart/${code}`, { params: { months, endDate: endDate || undefined } })
    .then((res) => {
      const rows = normalizeCandles(res?.data);
      chartDataCache.set(key, { ts: Date.now(), rows });
      return rows;
    })
    .finally(() => {
      inflightChartRequests.delete(key);
    });

  inflightChartRequests.set(key, req);
  return req;
}

export default function StockChartCard({
  apiBaseUrl,
  code,
  months = 6,
  endDate,
  realtimePollMs = REALTIME_POLL_MS,
  height = 280,
  onLatestPriceChange,
  title,
  subtitle,
  headerActions,
  compareCode,
  compareLabel,
  forcedPeriod,
  hidePeriodSelector = false,
}) {
  const wrapRef = useRef(null);
  const chartRef = useRef(null);
  const mainSeriesRef = useRef(null);
  const compareSeriesRef = useRef(null);
  const mainRowsRef = useRef([]);
  const loadingMoreRef = useRef(false);
  const reachedMinHistoryRef = useRef(false);
  const chartDisposedRef = useRef(true);
  const periodRef = useRef("DAY");
  const compareModeRef = useRef(Boolean(compareCode));
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [period, setPeriod] = useState(forcedPeriod || "DAY");
  const [hoverInfo, setHoverInfo] = useState(null);

  useEffect(() => {
    periodRef.current = period;
  }, [period]);

  useEffect(() => {
    if (!forcedPeriod) return;
    setPeriod(forcedPeriod);
  }, [forcedPeriod]);

  useEffect(() => {
    compareModeRef.current = Boolean(compareCode);
  }, [compareCode]);

  const applyMainSeriesData = useCallback((rows) => {
    if (!mainSeriesRef.current) return;
    if (compareModeRef.current) {
      mainSeriesRef.current.setData(toRelativeSeries(rows));
      return;
    }
    mainSeriesRef.current.setData(aggregateCandles(rows, periodRef.current));
  }, []);

  useEffect(() => {
    if (!wrapRef.current) return;
    chartDisposedRef.current = false;
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
    loadingMoreRef.current = false;
    reachedMinHistoryRef.current = false;

    const onCrosshairMove = (param) => {
      if (chartDisposedRef.current) return;
      if (!param?.time || !mainSeriesRef.current) {
        setHoverInfo(null);
        return;
      }
      const point = param.seriesData.get(mainSeriesRef.current);
      if (!point || compareModeRef.current) {
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
    };
    chart.subscribeCrosshairMove(onCrosshairMove);

    const onVisibleLogicalRangeChange = async (range) => {
      if (chartDisposedRef.current) return;
      if (compareModeRef.current) return;
      if (!range || loadingMoreRef.current || reachedMinHistoryRef.current) return;
      if (range.from > LEFT_EDGE_LOAD_THRESHOLD) return;

      const rows = Array.isArray(mainRowsRef.current) ? mainRowsRef.current : [];
      if (!rows.length) return;
      const earliest = rows[0]?.time;
      if (!earliest || earliest <= MIN_HISTORY_DATE) {
        reachedMinHistoryRef.current = true;
        return;
      }

      loadingMoreRef.current = true;
      const prevRange = chart.timeScale().getVisibleLogicalRange();
      try {
        const olderRows = await fetchChartData(apiBaseUrl, code, months, earliest);
        if (!olderRows.length) {
          reachedMinHistoryRef.current = true;
          return;
        }
        if (chartDisposedRef.current) return;

        const activePeriod = periodRef.current;
        const prevShownLength = compareModeRef.current
          ? toRelativeSeries(rows).length
          : aggregateCandles(rows, activePeriod).length;
        const nextRows = mergeCandles(rows, olderRows);
        const nextShownLength = compareModeRef.current
          ? toRelativeSeries(nextRows).length
          : aggregateCandles(nextRows, activePeriod).length;
        const addedCount = Math.max(0, nextShownLength - prevShownLength);
        mainRowsRef.current = nextRows;
        applyMainSeriesData(nextRows);

        const newEarliest = nextRows[0]?.time;
        if (!newEarliest || newEarliest <= MIN_HISTORY_DATE || addedCount === 0) {
          reachedMinHistoryRef.current = true;
        }

        if (prevRange && addedCount > 0) {
          chart.timeScale().setVisibleLogicalRange({
            from: prevRange.from + addedCount,
            to: prevRange.to + addedCount,
          });
        }
      } catch (_) {
        // Ignore transient load-more failures.
      } finally {
        if (!chartDisposedRef.current) {
          loadingMoreRef.current = false;
        }
      }
    };
    chart.timeScale().subscribeVisibleLogicalRangeChange(onVisibleLogicalRangeChange);

    const onResize = () => {
      if (chartDisposedRef.current || !wrapRef.current || !chartRef.current) return;
      chartRef.current.applyOptions({ width: wrapRef.current.clientWidth });
    };
    window.addEventListener("resize", onResize);
    return () => {
      chartDisposedRef.current = true;
      chart.timeScale().unsubscribeVisibleLogicalRangeChange(onVisibleLogicalRangeChange);
      chart.unsubscribeCrosshairMove(onCrosshairMove);
      window.removeEventListener("resize", onResize);
      chartRef.current = null;
      mainSeriesRef.current = null;
      compareSeriesRef.current = null;
      chart.remove();
    };
  }, [height, compareCode, apiBaseUrl, code, months, applyMainSeriesData]);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      if (!code || !mainSeriesRef.current) return;
      setLoading(true);
      setError("");
      try {
        const mainReq = fetchChartData(apiBaseUrl, code, months, endDate);
        const compareReq = compareCode
          ? fetchChartData(apiBaseUrl, compareCode, months, endDate)
          : Promise.resolve(null);
        const [mainRes, compareRes] = await Promise.all([mainReq, compareReq]);
        if (cancelled || chartDisposedRef.current) return;

        if (compareCode) {
          const mainRel = toRelativeSeries(mainRes);
          const compareRel = toRelativeSeries(compareRes);
          mainRowsRef.current = normalizeCandles(mainRes);
          if (!mainRel.length) {
            setError("비교용 차트 데이터가 없습니다.");
            mainSeriesRef.current.setData([]);
            compareSeriesRef.current?.setData([]);
            onLatestPriceChange?.(null);
            return;
          }
          mainSeriesRef.current.setData(mainRel);
          compareSeriesRef.current?.setData(compareRel);
          const last = mainRes[mainRes.length - 1];
          onLatestPriceChange?.(last ? { price: last.close, time: last.time } : null);
        } else {
          const rows = normalizeCandles(mainRes);
          mainRowsRef.current = rows;
          reachedMinHistoryRef.current = rows.length > 0 && rows[0].time <= MIN_HISTORY_DATE;
          if (!rows.length) {
            setError("차트 데이터가 없습니다.");
            mainSeriesRef.current.setData([]);
            onLatestPriceChange?.(null);
            return;
          }
          const shown = aggregateCandles(rows, periodRef.current);
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
  }, [apiBaseUrl, code, months, endDate, onLatestPriceChange, compareCode]);

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
      if (document.hidden) return;
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

    const timer = window.setInterval(updateSeriesWithRealtime, Math.max(1000, Number(realtimePollMs) || REALTIME_POLL_MS));
    updateSeriesWithRealtime();
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [apiBaseUrl, code, endDate, compareCode, onLatestPriceChange, applyMainSeriesData, realtimePollMs]);

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
          {!hidePeriodSelector && (
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
          )}
        </div>
      )}
      {loading && <div style={{ marginBottom: 8, color: "#64748b", fontSize: 13 }}>불러오는 중...</div>}
      {error && <div style={{ marginBottom: 8, color: "#dc2626", fontSize: 13 }}>{error}</div>}
      <div ref={wrapRef} style={{ minHeight: height, cursor: "crosshair" }} />
    </div>
  );
}
