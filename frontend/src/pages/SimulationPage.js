import React, { useEffect, useMemo, useState } from "react";
import axios from "axios";
import MinuteChartCard from "../components/MinuteChartCard";
import StockChartCard from "../components/StockChartCard";

export default function SimulationPage(props) {
  const {
    apiBaseUrl,
    authToken,
    isLoggedIn,
    simLoading,
    resetSimulation,
    leagueState,
    portfolio,
    tradeCode,
    setTradeCode,
    tradeQty,
    setTradeQty,
    tradeOrderType,
    setTradeOrderType,
    tradeLimitPrice,
    setTradeLimitPrice,
    tradeableCodes,
    getStockNameByCode,
    tradeMessage,
    chartEndDate,
    setSimSelectedPrice,
    simSelectedPrice,
    selectedTradeHolding,
    simOrderTab,
    setSimOrderTab,
    pendingLoading,
    pendingOrders,
    cancelPendingOrder,
    executionsLoading,
    filteredExecutions,
    executionFilterSide,
    setExecutionFilterSide,
    executionFilterCode,
    setExecutionFilterCode,
    rankingsLoading,
    rankings,
    rankingRowsTop10WithMe,
    rankingPeriod,
    setRankingPeriod,
    openRankingUserSummary,
    rankingUserSummary,
    setRankingUserSummary,
    rankingUserSummaryLoading,
    fmt,
    fmtDateTime,
    fmtSigned,
    getHoldingReturnRate,
    holdingOrderQtys,
    setHoldingOrderQtys,
    setHoldingQuickQty,
    getHoldingOrderQty,
    openOrderConfirm,
    orderConfirmDraft,
    setOrderConfirmDraft,
    confirmOrderDraft,
  } = props;

  const [autoBuyLoading, setAutoBuyLoading] = useState(false);
  const [autoBuyMessage, setAutoBuyMessage] = useState("");
  const [autoBuyRules, setAutoBuyRules] = useState([]);
  const [editingAutoBuyRuleId, setEditingAutoBuyRuleId] = useState(null);
  const [orderbook, setOrderbook] = useState(null);
  const [orderbookLoading, setOrderbookLoading] = useState(false);
  const [orderbookError, setOrderbookError] = useState("");
  const [isMarketOpenKst, setIsMarketOpenKst] = useState(true);
  const [chartTimeframe, setChartTimeframe] = useState("1m");
  const [autoBuyForm, setAutoBuyForm] = useState({
    name: "",
    code: tradeCode || "005930",
    quantity: "1",
    frequency: "DAILY",
    enabled: true,
    startDate: "",
    endDate: "",
  });

  const autoBuyPreviewName = useMemo(() => {
    const code = String(autoBuyForm.code || "").trim();
    const qty = Number(autoBuyForm.quantity || 0) || 0;
    return `${getStockNameByCode?.(code) || code || "-"} ${qty}주 자동매수`;
  }, [autoBuyForm.code, autoBuyForm.quantity, getStockNameByCode]);

  const chartTimeframeOptions = [
    { key: "1m", label: "1분", bucketMinutes: 1 },
    { key: "10m", label: "10분", bucketMinutes: 10 },
    { key: "30m", label: "30분", bucketMinutes: 30 },
    { key: "60m", label: "1시간", bucketMinutes: 60 },
    { key: "1d", label: "일", forcedPeriod: "DAY", months: 6 },
    { key: "1w", label: "주", forcedPeriod: "WEEK", months: 24 },
    { key: "1mo", label: "월", forcedPeriod: "MONTH", months: 120 },
    { key: "1y", label: "년", forcedPeriod: "YEAR", months: 120 },
  ];
  const activeTimeframe = chartTimeframeOptions.find((x) => x.key === chartTimeframe) || chartTimeframeOptions[0];
  const isIntradayTimeframe = Boolean(activeTimeframe.bucketMinutes);

  const authHeaders = () => (authToken ? { Authorization: `Bearer ${authToken}` } : {});

  useEffect(() => {
    const computeIsOpen = () => {
      try {
        const now = new Date();
        const kstParts = new Intl.DateTimeFormat("en-GB", {
          timeZone: "Asia/Seoul",
          hour: "2-digit",
          minute: "2-digit",
          hour12: false,
        }).formatToParts(now);
        const hour = Number(kstParts.find((p) => p.type === "hour")?.value || 0);
        const minute = Number(kstParts.find((p) => p.type === "minute")?.value || 0);
        const totalMinutes = hour * 60 + minute;
        const isOpen = totalMinutes >= 9 * 60 && totalMinutes <= 20 * 60;
        setIsMarketOpenKst(isOpen);
      } catch (e) {
        setIsMarketOpenKst(true);
      }
    };
    computeIsOpen();
    const timer = window.setInterval(computeIsOpen, 30000);
    return () => window.clearInterval(timer);
  }, []);

  const loadAutoBuyRules = async () => {
    if (!isLoggedIn || !authToken) return;
    setAutoBuyLoading(true);
    try {
      const res = await axios.get(`${apiBaseUrl}/api/sim/auto-buy-rules`, { headers: authHeaders() });
      setAutoBuyRules(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      setAutoBuyMessage(err?.response?.data?.message || err?.message || "자동매수 규칙 조회에 실패했습니다.");
    } finally {
      setAutoBuyLoading(false);
    }
  };

  useEffect(() => {
    if (!isLoggedIn || !authToken) return;
    loadAutoBuyRules();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoggedIn, authToken, apiBaseUrl]);

  useEffect(() => {
    if (!tradeCode) return;
    setAutoBuyForm((prev) => (prev.code ? prev : { ...prev, code: tradeCode }));
  }, [tradeCode]);

  useEffect(() => {
    if (!tradeCode || !apiBaseUrl) return undefined;
    let cancelled = false;

    const loadOrderbook = async (first = false) => {
      if (first) setOrderbookLoading(true);
      try {
        const res = await axios.get(`${apiBaseUrl}/api/stock/orderbook/${tradeCode}`);
        if (cancelled) return;
        setOrderbook(res.data || null);
        setOrderbookError("");
      } catch (err) {
        if (cancelled) return;
        setOrderbookError(err?.response?.data?.message || err?.message || "호가 정보를 불러오지 못했습니다.");
      } finally {
        if (first && !cancelled) setOrderbookLoading(false);
      }
    };

    loadOrderbook(true);
    const timer = window.setInterval(() => {
      if (document.hidden) return;
      loadOrderbook(false);
    }, 2000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [apiBaseUrl, tradeCode]);

  const saveAutoBuyRule = async () => {
    try {
      setAutoBuyMessage("");
      const code = extractStockCode(autoBuyForm.code);
      const payload = {
        name: String(autoBuyForm.name || "").trim() || `${getStockNameByCode?.(code) || code} 자동매수`,
        code,
        quantity: Number(autoBuyForm.quantity || 0),
        frequency: autoBuyForm.frequency,
        enabled: Boolean(autoBuyForm.enabled),
        startDate: autoBuyForm.startDate || null,
        endDate: autoBuyForm.endDate || null,
      };
      if (editingAutoBuyRuleId) {
        await axios.patch(`${apiBaseUrl}/api/sim/auto-buy-rules/${editingAutoBuyRuleId}`, payload, { headers: authHeaders() });
        setAutoBuyMessage("자동매수 예약을 수정했습니다.");
      } else {
        await axios.post(`${apiBaseUrl}/api/sim/auto-buy-rules`, payload, { headers: authHeaders() });
        setAutoBuyMessage("자동매수 예약을 추가했습니다.");
      }
      setEditingAutoBuyRuleId(null);
      setAutoBuyForm((prev) => ({ ...prev, name: "", quantity: "1", startDate: "", endDate: "" }));
      await loadAutoBuyRules();
    } catch (err) {
      setAutoBuyMessage(err?.response?.data?.message || err?.message || "자동매수 예약 저장에 실패했습니다.");
    }
  };

  const startEditAutoBuyRule = (rule) => {
    if (!rule) return;
    setEditingAutoBuyRuleId(rule.id);
    setAutoBuyForm({
      name: rule.name || "",
      code: rule.code || (tradeableCodes[0] || "005930"),
      quantity: String(rule.quantity || 1),
      frequency: rule.frequency || "DAILY",
      enabled: Boolean(rule.enabled),
      startDate: rule.startDate || "",
      endDate: rule.endDate || "",
    });
  };

  const cancelEditAutoBuyRule = () => {
    setEditingAutoBuyRuleId(null);
    setAutoBuyForm((prev) => ({
      ...prev,
      name: "",
      code: tradeCode || prev.code || "005930",
      quantity: "1",
      frequency: "DAILY",
      enabled: true,
      startDate: "",
      endDate: "",
    }));
  };

  const toggleAutoBuyRule = async (rule) => {
    try {
      await axios.patch(
        `${apiBaseUrl}/api/sim/auto-buy-rules/${rule.id}`,
        {
          name: rule.name,
          code: rule.code,
          quantity: rule.quantity,
          frequency: rule.frequency,
          enabled: !rule.enabled,
          startDate: rule.startDate || null,
          endDate: rule.endDate || null,
        },
        { headers: authHeaders() }
      );
      await loadAutoBuyRules();
    } catch (err) {
      setAutoBuyMessage(err?.response?.data?.message || err?.message || "자동매수 상태 변경에 실패했습니다.");
    }
  };

  const deleteAutoBuyRule = async (ruleId) => {
    try {
      await axios.delete(`${apiBaseUrl}/api/sim/auto-buy-rules/${ruleId}`, { headers: authHeaders() });
      setAutoBuyMessage("자동매수 예약을 삭제했습니다.");
      await loadAutoBuyRules();
    } catch (err) {
      setAutoBuyMessage(err?.response?.data?.message || err?.message || "자동매수 예약 삭제에 실패했습니다.");
    }
  };

  if (!isLoggedIn) {
    return (
      <div className="app-card sim-page-card sim-page-wrap">
        <h3 className="sim-page-title">모의투자</h3>
        <p className="sim-page-muted">모의투자는 로그인 후 사용할 수 있습니다.</p>
      </div>
    );
  }

  const currentDate = portfolio?.valuationDate || leagueState?.currentDate || "-";

  return (
    <div className="app-card sim-page-card sim-page-wrap">
      <h3 className="sim-page-title">투자</h3>

      <div className="app-toolbar-row sim-inline-row">
        <span className="sim-page-subtle">실시간 기준으로 운영됩니다.</span>
        <button type="button" onClick={resetSimulation} disabled={simLoading}>
          초기화
        </button>
      </div>

      <div className="app-toolbar-row sim-inline-row sim-inline-row-gap-sm">
        <span>거래 기준일: {currentDate}</span>
      </div>

      <div className="app-toolbar-row sim-inline-row sim-inline-row-gap-md">
        <label>종목</label>
        <select value={tradeCode} onChange={(e) => setTradeCode(e.target.value)} disabled={simLoading}>
          {tradeableCodes.map((code) => (
            <option key={code} value={code}>
              {getStockNameByCode(code)} ({code})
            </option>
          ))}
        </select>
        <label>수량</label>
        <input type="number" min="1" value={tradeQty} onChange={(e) => setTradeQty(e.target.value)} className="sim-inline-input-sm" />
        <label>주문유형</label>
        <select value={tradeOrderType} onChange={(e) => setTradeOrderType(e.target.value)} disabled={simLoading}>
          <option value="MARKET">시장가</option>
          <option value="LIMIT">지정가</option>
        </select>
        {tradeOrderType === "LIMIT" && (
          <>
            <label>목표가</label>
            <input
              type="number"
              min="1"
              step="1"
              value={tradeLimitPrice}
              onChange={(e) => setTradeLimitPrice(e.target.value)}
              className="sim-inline-input-md"
              placeholder="가격 입력"
            />
          </>
        )}
        <button
          type="button"
          className="sim-holding-buy-btn"
          disabled={simLoading || (tradeOrderType === "MARKET" && !isMarketOpenKst)}
          onClick={() => openOrderConfirm("BUY")}
        >
          매수
        </button>
        <button
          type="button"
          className="sim-holding-sell-btn"
          disabled={simLoading || !isMarketOpenKst}
          onClick={() => openOrderConfirm("SELL")}
        >
          매도
        </button>
      </div>
      {!isMarketOpenKst && (
        <div className="sim-page-muted">장외 시간(한국시간 09:00~20:00 외)에는 매도 주문이 비활성화됩니다.</div>
      )}

      {tradeMessage && <div className="sim-trade-message">{tradeMessage}</div>}

      <div className="app-card" style={{ marginTop: 12, background: "linear-gradient(180deg, #f8fafc 0%, #ffffff 100%)" }}>
        <div className="app-toolbar-row" style={{ justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
          <div>
            <div style={{ fontWeight: 700 }}>자동매수 예약 (시뮬레이션)</div>
            <div style={{ fontSize: 12, color: "#64748b" }}>
              실시간 기준일에 맞춰 규칙 조건에 따라 자동으로 시장가 매수합니다.
            </div>
          </div>
          <button type="button" className="sim-order-mini-btn" onClick={loadAutoBuyRules} disabled={autoBuyLoading}>
            새로고침
          </button>
        </div>

        <div style={{ display: "grid", gap: 8, marginTop: 10, gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))" }}>
          <input
            placeholder="예약명 (선택)"
            value={autoBuyForm.name}
            onChange={(e) => setAutoBuyForm((p) => ({ ...p, name: e.target.value }))}
          />
          <select
            value={autoBuyForm.code}
            onChange={(e) => setAutoBuyForm((p) => ({ ...p, code: e.target.value }))}
          >
            {tradeableCodes.map((code) => (
              <option key={`auto-buy-form-code-${code}`} value={code}>
                {getStockNameByCode(code)} ({code})
              </option>
            ))}
          </select>
          <input
            type="number"
            min="1"
            value={autoBuyForm.quantity}
            onChange={(e) => setAutoBuyForm((p) => ({ ...p, quantity: e.target.value }))}
            placeholder="수량"
          />
          <select value={autoBuyForm.frequency} onChange={(e) => setAutoBuyForm((p) => ({ ...p, frequency: e.target.value }))}>
            <option value="DAILY">매일</option>
            <option value="WEEKDAYS">주중만</option>
            <option value="WEEKLY">주 1회</option>
          </select>
          <input
            type="date"
            value={autoBuyForm.startDate}
            onChange={(e) => setAutoBuyForm((p) => ({ ...p, startDate: e.target.value }))}
            placeholder="시작일"
          />
          <input
            type="date"
            value={autoBuyForm.endDate}
            onChange={(e) => setAutoBuyForm((p) => ({ ...p, endDate: e.target.value }))}
            placeholder="종료일"
          />
        </div>

        <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", marginTop: 8 }}>
          <label style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12 }}>
            <input
              type="checkbox"
              checked={Boolean(autoBuyForm.enabled)}
              onChange={(e) => setAutoBuyForm((p) => ({ ...p, enabled: e.target.checked }))}
            />
            생성 즉시 활성화
          </label>
          <span style={{ fontSize: 12, color: "#64748b" }}>미리보기: {autoBuyPreviewName}</span>
          <button
            type="button"
            onClick={saveAutoBuyRule}
            disabled={!extractStockCode(autoBuyForm.code) || Number(autoBuyForm.quantity || 0) <= 0}
          >
            {editingAutoBuyRuleId ? "자동매수 예약 수정 저장" : "자동매수 예약 추가"}
          </button>
          {editingAutoBuyRuleId && (
            <button type="button" className="sim-order-mini-btn" onClick={cancelEditAutoBuyRule}>
              수정 취소
            </button>
          )}
        </div>

        {autoBuyMessage && <div className="sim-trade-message" style={{ marginTop: 8 }}>{autoBuyMessage}</div>}

        <div style={{ marginTop: 10 }}>
          {autoBuyLoading && <div>자동매수 규칙 불러오는 중...</div>}
          {!autoBuyLoading && autoBuyRules.length === 0 && (
            <div style={{ color: "#64748b" }}>등록된 자동매수 예약이 없습니다.</div>
          )}
          {!autoBuyLoading && autoBuyRules.length > 0 && (
            <TableWrap>
              <table className="sim-order-table">
                <thead>
                  <tr>
                    <th>상태</th>
                    <th>예약명</th>
                    <th>종목</th>
                    <th className="num">수량</th>
                    <th>주기</th>
                    <th>기간</th>
                    <th>최근 실행</th>
                    <th>액션</th>
                  </tr>
                </thead>
                <tbody>
                  {autoBuyRules.map((r) => (
                    <tr key={`auto-buy-rule-${r.id}`}>
                      <td>{r.enabled ? "활성" : "비활성"}</td>
                      <td>{r.name}</td>
                      <td>{getStockNameByCode(r.code)} ({r.code})</td>
                      <td className="num">{fmt(Number(r.quantity || 0))}주</td>
                      <td>{frequencyLabel(r.frequency)}</td>
                      <td>{r.startDate || "-"} ~ {r.endDate || "-"}</td>
                      <td>{r.lastExecutedDate || "-"}</td>
                      <td>
                        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                          <button type="button" className="sim-order-mini-btn" onClick={() => toggleAutoBuyRule(r)}>
                            {r.enabled ? "비활성화" : "활성화"}
                          </button>
                          <button type="button" className="sim-order-mini-btn" onClick={() => startEditAutoBuyRule(r)}>
                            수정
                          </button>
                          <button type="button" className="sim-order-mini-btn" onClick={() => deleteAutoBuyRule(r.id)}>
                            삭제
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </TableWrap>
          )}
        </div>
      </div>

      {tradeCode && (
        <div className="sim-selected-stock-card sim-selected-stock-card-gap">
          <div className="sim-selected-stock-head">
            <strong>
              {getStockNameByCode(tradeCode)} ({tradeCode})
            </strong>
            <span>
              현재가:{" "}
              {simSelectedPrice?.price
                ? `${fmt(Number(simSelectedPrice.price))}원`
                : selectedTradeHolding?.currentPrice
                  ? `${fmt(Number(selectedTradeHolding.currentPrice))}원`
                  : "-"}
            </span>
            <span className="sim-selected-stock-date">기준일 {currentDate}</span>
          </div>

          <div className="app-toolbar-row" style={{ gap: 6, flexWrap: "wrap", marginBottom: 8 }}>
            {chartTimeframeOptions.map((tf) => (
              <button
                key={`sim-tf-${tf.key}`}
                type="button"
                className={chartTimeframe === tf.key ? "sim-order-tab active" : "sim-order-tab"}
                onClick={() => setChartTimeframe(tf.key)}
              >
                {tf.label}
              </button>
            ))}
          </div>

          {isIntradayTimeframe ? (
            <MinuteChartCard
              key={`${tradeCode}-${activeTimeframe.key}`}
              apiBaseUrl={apiBaseUrl}
              code={tradeCode}
              height={220}
              bucketMinutes={activeTimeframe.bucketMinutes}
              onLatestPriceChange={setSimSelectedPrice}
              title={`${getStockNameByCode(tradeCode)} (${tradeCode})`}
              subtitle={`실시간 ${activeTimeframe.label} 캔들`}
            />
          ) : (
            <StockChartCard
              key={`${tradeCode}-${activeTimeframe.key}-${chartEndDate || "today"}`}
              apiBaseUrl={apiBaseUrl}
              code={tradeCode}
              months={activeTimeframe.months || 6}
              endDate={chartEndDate}
              forcedPeriod={activeTimeframe.forcedPeriod}
              hidePeriodSelector
              height={220}
              onLatestPriceChange={setSimSelectedPrice}
              title={`${getStockNameByCode(tradeCode)} (${tradeCode})`}
              subtitle={`${activeTimeframe.label} 기준 차트`}
            />
          )}

          <div className="app-card" style={{ marginTop: 10 }}>
            <div className="app-toolbar-row" style={{ justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
              <div style={{ fontWeight: 700 }}>호가창 / 체결강도</div>
              {orderbook?.time ? <div style={{ fontSize: 12, color: "#64748b" }}>업데이트: {orderbook.time}</div> : null}
            </div>
            <div style={{ display: "flex", gap: 16, flexWrap: "wrap", marginTop: 8, fontSize: 13 }}>
              <span>
                현재가:{" "}
                <strong>
                  {orderbook?.currentPrice
                    ? `${fmt(orderbook.currentPrice)}원`
                    : simSelectedPrice?.price
                      ? `${fmt(Number(simSelectedPrice.price))}원`
                      : "-"}
                </strong>
              </span>
              <span>총 매도잔량: <strong>{orderbook?.totalAskQty != null ? fmt(orderbook.totalAskQty) : "-"}</strong></span>
              <span>총 매수잔량: <strong>{orderbook?.totalBidQty != null ? fmt(orderbook.totalBidQty) : "-"}</strong></span>
              <span>체결강도: <strong>{orderbook?.executionStrength != null ? `${Number(orderbook.executionStrength).toFixed(2)}%` : "-"}</strong></span>
            </div>
            {orderbookLoading && <div style={{ marginTop: 8, color: "#64748b" }}>호가 불러오는 중...</div>}
            {orderbookError && <div style={{ marginTop: 8, color: "#dc2626", fontSize: 12 }}>{orderbookError}</div>}
            <TableWrap>
              <table className="sim-order-table" style={{ marginTop: 10 }}>
                <thead>
                  <tr>
                    <th className="num">매도잔량</th>
                    <th className="num">매도호가</th>
                    <th className="num">매수호가</th>
                    <th className="num">매수잔량</th>
                  </tr>
                </thead>
                <tbody>
                  {(orderbook?.levels || []).map((lv) => (
                    <tr key={`ob-${lv.level}`}>
                      <td className="num down">{lv.askQty != null ? fmt(lv.askQty) : "-"}</td>
                      <td className="num down">{lv.askPrice != null ? fmt(lv.askPrice) : "-"}</td>
                      <td className="num up">{lv.bidPrice != null ? fmt(lv.bidPrice) : "-"}</td>
                      <td className="num up">{lv.bidQty != null ? fmt(lv.bidQty) : "-"}</td>
                    </tr>
                  ))}
                  {(!orderbook?.levels || orderbook.levels.length === 0) && (
                    <tr>
                      <td colSpan={4} style={{ textAlign: "center", color: "#64748b" }}>호가 데이터가 없습니다.</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </TableWrap>
          </div>
        </div>
      )}

      <div className="sim-order-tabs sim-order-tabs-gap">
        <button type="button" className={simOrderTab === "pending" ? "sim-order-tab active" : "sim-order-tab"} onClick={() => setSimOrderTab("pending")}>
          미체결 주문
        </button>
        <button type="button" className={simOrderTab === "executions" ? "sim-order-tab active" : "sim-order-tab"} onClick={() => setSimOrderTab("executions")}>
          체결내역
        </button>
        <button type="button" className={simOrderTab === "rankings" ? "sim-order-tab active" : "sim-order-tab"} onClick={() => setSimOrderTab("rankings")}>
          수익률 랭킹
        </button>
      </div>

      <div className="sim-order-panel">
        {simOrderTab === "pending" && (
          <>
            {pendingLoading && <div>불러오는 중...</div>}
            {!pendingLoading && pendingOrders.length === 0 && <div>미체결 주문이 없습니다.</div>}
            {!pendingLoading && pendingOrders.length > 0 && (
              <TableWrap>
                <table className="sim-order-table">
                  <thead>
                    <tr>
                      <th>구분</th>
                      <th>종목</th>
                      <th>수량</th>
                      <th>주문가</th>
                      <th>접수시간</th>
                      <th>액션</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pendingOrders.map((o) => (
                      <tr key={o.id}>
                        <td className={o.side === "BUY" ? "up" : "down"}>{o.side === "BUY" ? "매수" : "매도"}</td>
                        <td>{getStockNameByCode(o.code)} ({o.code})</td>
                        <td className="num">{fmt(o.quantity)}주</td>
                        <td className="num">{fmt(o.limitPrice || 0)}원</td>
                        <td>{fmtDateTime(o.createdAt)}</td>
                        <td>
                          <button type="button" className="sim-order-mini-btn" onClick={() => cancelPendingOrder(o.id)}>
                            취소
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </TableWrap>
            )}
          </>
        )}

        {simOrderTab === "executions" && (
          <>
            <div className="sim-order-toolbar">
              <select value={executionFilterSide} onChange={(e) => setExecutionFilterSide(e.target.value)} className="sim-order-filter">
                <option value="ALL">전체</option>
                <option value="BUY">매수</option>
                <option value="SELL">매도</option>
              </select>
              <select value={executionFilterCode} onChange={(e) => setExecutionFilterCode(e.target.value)} className="sim-order-filter">
                <option value="">전체 종목</option>
                {tradeableCodes.map((code) => (
                  <option key={`ef-${code}`} value={code}>
                    {getStockNameByCode(code)} ({code})
                  </option>
                ))}
              </select>
            </div>
            {executionsLoading && <div>불러오는 중...</div>}
            {!executionsLoading && filteredExecutions.length === 0 && <div>체결내역이 없습니다.</div>}
            {!executionsLoading && filteredExecutions.length > 0 && (
              <TableWrap>
                <table className="sim-order-table">
                  <thead>
                    <tr>
                      <th>구분</th>
                      <th>종목</th>
                      <th>수량</th>
                      <th>체결가</th>
                      <th>금액</th>
                      <th>기준일</th>
                      <th>체결시간</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredExecutions.map((o) => (
                      <tr key={o.id}>
                        <td className={o.side === "BUY" ? "up" : "down"}>{o.side === "BUY" ? "매수" : "매도"}</td>
                        <td>{getStockNameByCode(o.code)} ({o.code})</td>
                        <td className="num">{fmt(o.quantity)}주</td>
                        <td className="num">{fmt(o.price)}원</td>
                        <td className="num">{fmt(o.amount)}원</td>
                        <td>{o.valuationDate || "-"}</td>
                        <td>{fmtDateTime(o.executedAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </TableWrap>
            )}
          </>
        )}

        {simOrderTab === "rankings" && (
          <>
            <div className="sim-order-toolbar">
              <select value={rankingPeriod} onChange={(e) => setRankingPeriod(e.target.value)} className="sim-order-filter">
                <option value="ALL">전체</option>
                <option value="TODAY">오늘 (준비중)</option>
                <option value="7D">최근 7일 (준비중)</option>
              </select>
            </div>
            {rankingPeriod !== "ALL" && (
              <div className="sim-ranking-note">기간 랭킹은 스냅샷 데이터 적용 후 활성화될 예정입니다.</div>
            )}
            {rankingsLoading && <div>불러오는 중...</div>}
            {!rankingsLoading && rankings.length === 0 && <div>랭킹 데이터가 없습니다.</div>}
            {!rankingsLoading && rankings.length > 0 && (
              <TableWrap>
                <table className="sim-order-table">
                  <thead>
                    <tr>
                      <th>순위</th>
                      <th>사용자</th>
                      <th>수익률</th>
                      <th>총자산</th>
                      <th>실현손익</th>
                      <th>미실현손익</th>
                      <th>기준일</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rankingRowsTop10WithMe.map((r) => (
                      <tr key={`ranking-${r.userId}`} className={r.me ? "sim-ranking-me-row" : ""}>
                        <td className="num">{fmt(Number(r.rank))}</td>
                        <td>
                          <button
                            type="button"
                            className="sim-ranking-name-btn"
                            onClick={() => openRankingUserSummary(r.userId)}
                            disabled={rankingUserSummaryLoading}
                          >
                            {r.me ? `${r.userName} (나)` : r.userName}
                          </button>
                        </td>
                        <td className={`num ${Number(r.returnRate) >= 0 ? "up" : "down"}`}>
                          {Number(r.returnRate) > 0 ? "+" : ""}
                          {Number(r.returnRate).toFixed(2)}%
                        </td>
                        <td className="num">{fmt(Number(r.totalValue))}원</td>
                        <td className={`num ${Number(r.realizedPnl) >= 0 ? "up" : "down"}`}>{fmtSigned(Number(r.realizedPnl))}원</td>
                        <td className={`num ${Number(r.unrealizedPnl) >= 0 ? "up" : "down"}`}>{fmtSigned(Number(r.unrealizedPnl))}원</td>
                        <td>{r.valuationDate || "-"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </TableWrap>
            )}
          </>
        )}
      </div>

      {portfolio && (
        <div className="sim-portfolio-inline-summary">
          예수금 {fmt(portfolio.cash)}원 | 평가금액 {fmt(portfolio.marketValue)}원 | 총자산 {fmt(portfolio.totalValue)}원 | 실현손익{" "}
          {fmtSigned(portfolio.realizedPnl)}원 | 미실현손익 {fmtSigned(portfolio.unrealizedPnl)}원
        </div>
      )}

      {portfolio?.holdings?.length > 0 && (
        <div className="sim-holdings-list">
          <div className="sim-holdings-title">보유 종목</div>
          <div className="sim-holdings-table-wrap">
            <table className="sim-holdings-table">
              <thead>
                <tr>
                  <th>종목</th>
                  <th>보유수량</th>
                  <th>평단가</th>
                  <th>현재가</th>
                  <th>평가금액</th>
                  <th>평가손익</th>
                  <th>수익률</th>
                  <th>주문</th>
                </tr>
              </thead>
              <tbody>
                {portfolio.holdings.map((h) => {
                  const holdQty = Math.max(1, Number(h.quantity) || 1);
                  const unrealizedPnl = Number(h.unrealizedPnl) || 0;
                  const returnRate = getHoldingReturnRate(h);
                  const isUp = unrealizedPnl >= 0;
                  return (
                    <tr key={`${h.code}-${h.quantity}-${h.avgPrice}`}>
                      <td>
                        <div className="sim-holding-code">{getStockNameByCode(h.code)}</div>
                        <div className="sim-holding-meta">{h.code}</div>
                      </td>
                      <td className="num">{fmt(Number(h.quantity) || 0)}주</td>
                      <td className="num">{fmt(Number(h.avgPrice) || 0)}원</td>
                      <td className="num">{fmt(Number(h.currentPrice) || 0)}원</td>
                      <td className="num">{fmt(Number(h.marketValue) || 0)}원</td>
                      <td className={`num ${isUp ? "up" : "down"}`}>{fmtSigned(unrealizedPnl)}원</td>
                      <td className={`num ${isUp ? "up" : "down"}`}>
                        {returnRate > 0 ? "+" : ""}
                        {returnRate.toFixed(2)}%
                      </td>
                      <td>
                        <div className="sim-holding-actions">
                          <input
                            type="number"
                            min="1"
                            max={holdQty}
                            className="sim-holding-qty-input"
                            value={holdingOrderQtys[h.code] ?? 1}
                            onChange={(e) => setHoldingOrderQtys((prev) => ({ ...prev, [h.code]: e.target.value }))}
                          />
                          <button type="button" className="sim-holding-quick-btn" onClick={() => setHoldingQuickQty(h.code, holdQty, 0.25)}>
                            25%
                          </button>
                          <button type="button" className="sim-holding-quick-btn" onClick={() => setHoldingQuickQty(h.code, holdQty, 0.5)}>
                            50%
                          </button>
                          <button
                            type="button"
                            className="sim-holding-buy-btn"
                            onClick={() => {
                              const q = getHoldingOrderQty(h.code);
                              openOrderConfirm("BUY", { code: h.code, quantity: q });
                            }}
                          >
                            매수
                          </button>
                          <button
                            type="button"
                            className="sim-holding-sell-btn"
                            disabled={!isMarketOpenKst}
                            onClick={() => {
                              const q = Math.min(getHoldingOrderQty(h.code), holdQty);
                              openOrderConfirm("SELL", { code: h.code, quantity: q });
                            }}
                          >
                            매도
                          </button>
                          <button
                            type="button"
                            className="sim-holding-all-sell-btn"
                            disabled={!isMarketOpenKst}
                            onClick={() => openOrderConfirm("SELL", { code: h.code, quantity: holdQty })}
                          >
                            전량
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {orderConfirmDraft && (
        <div className="sim-modal-backdrop" role="presentation" onClick={() => setOrderConfirmDraft(null)}>
          <div className="sim-confirm-modal" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
            <div className="sim-confirm-title">주문 확인</div>
            <div className="sim-confirm-body">
              <div><strong>구분</strong> {orderConfirmDraft.side === "BUY" ? "매수" : "매도"}</div>
              <div><strong>종목</strong> {getStockNameByCode(orderConfirmDraft.code)} ({orderConfirmDraft.code})</div>
              <div><strong>수량</strong> {fmt(orderConfirmDraft.quantity)}주</div>
              <div><strong>주문유형</strong> {orderConfirmDraft.orderType === "LIMIT" ? "지정가" : "시장가"}</div>
              {orderConfirmDraft.orderType === "LIMIT" && (
                <div><strong>목표가</strong> {fmt(Number(orderConfirmDraft.limitPrice || 0))}원</div>
              )}
            </div>
            <div className="sim-confirm-actions">
              <button type="button" className="sim-order-mini-btn" onClick={() => setOrderConfirmDraft(null)}>취소</button>
              <button
                type="button"
                className={orderConfirmDraft.side === "BUY" ? "sim-holding-buy-btn" : "sim-holding-sell-btn"}
                onClick={confirmOrderDraft}
                disabled={simLoading || (orderConfirmDraft.side === "SELL" && !isMarketOpenKst)}
              >
                확인
              </button>
            </div>
          </div>
        </div>
      )}

      {rankingUserSummary && (
        <div className="sim-modal-backdrop" role="presentation" onClick={() => setRankingUserSummary(null)}>
          <div className="sim-confirm-modal" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
            <div className="sim-confirm-title">포트폴리오 요약</div>
            <div className="sim-confirm-body">
              <div><strong>사용자</strong> {rankingUserSummary.userName}</div>
              <div><strong>기준일</strong> {rankingUserSummary.portfolio?.valuationDate || "-"}</div>
              <div><strong>총자산</strong> {fmt(Number(rankingUserSummary.portfolio?.totalValue || 0))}원</div>
              <div><strong>예수금</strong> {fmt(Number(rankingUserSummary.portfolio?.cash || 0))}원</div>
            </div>
            <div className="sim-confirm-actions">
              <button type="button" className="sim-order-mini-btn" onClick={() => setRankingUserSummary(null)}>닫기</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function TableWrap({ children }) {
  return <div className="sim-order-table-wrap">{children}</div>;
}

function extractStockCode(input) {
  const text = String(input || "").trim();
  if (!text) return "";
  const m = text.match(/\b(\d{6})\b/);
  return m ? m[1] : text;
}

function frequencyLabel(value) {
  switch (String(value || "").toUpperCase()) {
    case "DAILY":
      return "매일";
    case "WEEKDAYS":
      return "주중";
    case "WEEKLY":
      return "주 1회";
    default:
      return value || "-";
  }
}
