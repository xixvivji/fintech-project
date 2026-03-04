import React, { useCallback, useEffect, useMemo, useState } from "react";
import axios from "axios";
import "./App.css";
import AuthPage from "./pages/AuthPage";
import ChartsPage from "./pages/ChartsPage";
import SimulationPage from "./pages/SimulationPage";
import HomePage from "./pages/HomePage";
import LeaguePage from "./pages/LeaguePage";
import MarketPage from "./pages/MarketPage";
import NewsPage from "./pages/NewsPage";
import ChallengesPage from "./pages/ChallengesPage";
import ChallengeDetailPage from "./pages/ChallengeDetailPage";
import ChallengeHistoryPage from "./pages/ChallengeHistoryPage";
import NotificationsPage from "./pages/NotificationsPage";
import ProfilePage from "./pages/ProfilePage";
import { STOCK_OPTIONS, getStockNameByCode } from "./constants/stocks";

const API_BASE_URL = "http://localhost:8080";
const AUTH_TOKEN_KEY = "fintech_access_token";
const MAX_ACTIVE_CHARTS = 5;

function readStoredToken() {
  try {
    return localStorage.getItem(AUTH_TOKEN_KEY) || "";
  } catch {
    return "";
  }
}

function normalizePath(pathname) {
  if (
    pathname === "/" ||
    pathname === "/login" ||
    pathname === "/signup" ||
    pathname === "/charts" ||
    pathname === "/sim" ||
    pathname === "/league" ||
    pathname === "/market" ||
    pathname === "/news" ||
    pathname === "/challenges" ||
    pathname === "/notifications" ||
    pathname === "/profile"
  ) {
    return pathname;
  }
  if (/^\/challenges\/\d+$/.test(pathname)) {
    return pathname;
  }
  if (pathname === "/challenges/history") {
    return pathname;
  }
  return "/";
}

function parseError(err, fallback) {
  const data = err?.response?.data;
  if (typeof data === "string" && data.trim()) return data;
  if (typeof data?.message === "string" && data.message.trim()) return data.message;
  if (typeof err?.message === "string" && err.message.trim()) return err.message;
  return fallback;
}

export default function App() {
  const [path, setPath] = useState(() => normalizePath(window.location.pathname));
  const [authToken, setAuthToken] = useState(readStoredToken);
  const [currentUser, setCurrentUser] = useState(null);
  const [authLoading, setAuthLoading] = useState(false);
  const [authMessage, setAuthMessage] = useState("");
  const [loginName, setLoginName] = useState("");
  const [loginPassword, setLoginPassword] = useState("");
  const [signupName, setSignupName] = useState("");
  const [signupPassword, setSignupPassword] = useState("");

  const [searchQuery, setSearchQuery] = useState("");
  const [monthsInput, setMonthsInput] = useState(12);
  const [applied, setApplied] = useState({ months: 12 });
  const [chartDisplayMode, setChartDisplayMode] = useState("normal");
  const [viewCodes, setViewCodes] = useState(["005930", "000660"]);
  const [watchlistCodes, setWatchlistCodes] = useState([]);
  const [watchlistLoading, setWatchlistLoading] = useState(false);
  const [uiMessage, setUiMessage] = useState("");
  const [isNarrowScreen, setIsNarrowScreen] = useState(window.innerWidth < 1024);

  const [simLoading, setSimLoading] = useState(false);
  const [tradeMessage, setTradeMessage] = useState("");
  const [portfolio, setPortfolio] = useState(null);
  const [leagueState, setLeagueState] = useState(null);
  const [tradeCode, setTradeCode] = useState("005930");
  const [tradeQty, setTradeQty] = useState("1");
  const [tradeOrderType, setTradeOrderType] = useState("MARKET");
  const [tradeLimitPrice, setTradeLimitPrice] = useState("");
  const [simSelectedPrice, setSimSelectedPrice] = useState(null);
  const [simOrderTab, setSimOrderTab] = useState("pending");
  const [pendingLoading, setPendingLoading] = useState(false);
  const [pendingOrders, setPendingOrders] = useState([]);
  const [executionsLoading, setExecutionsLoading] = useState(false);
  const [executions, setExecutions] = useState([]);
  const [executionFilterSide, setExecutionFilterSide] = useState("ALL");
  const [executionFilterCode, setExecutionFilterCode] = useState("");
  const [holdingOrderQtys, setHoldingOrderQtys] = useState({});
  const [orderConfirmDraft, setOrderConfirmDraft] = useState(null);

  const [rankingsLoading, setRankingsLoading] = useState(false);
  const [rankings, setRankings] = useState([]);
  const [rankingPeriod, setRankingPeriod] = useState("ALL");
  const [rankingUserSummaryLoading, setRankingUserSummaryLoading] = useState(false);
  const [rankingUserSummary, setRankingUserSummary] = useState(null);

  const isLoggedIn = Boolean(authToken);
  const isLeagueAdmin = String(currentUser?.name || "").trim().toLowerCase() === "admin";
  const tradeableCodes = useMemo(() => STOCK_OPTIONS.map((s) => s.code), []);
  const viewCodeSet = useMemo(() => new Set(viewCodes), [viewCodes]);
  const watchlistCodeSet = useMemo(() => new Set(watchlistCodes), [watchlistCodes]);
  const chartEndDate = undefined;

  const navigateTo = useCallback((next) => {
    const normalized = normalizePath(next);
    window.history.pushState({}, "", normalized);
    setPath(normalized);
    setAuthMessage("");
    setUiMessage("");
  }, []);

  const authHeaders = useCallback(() => {
    return authToken ? { Authorization: `Bearer ${authToken}` } : {};
  }, [authToken]);

  const persistToken = useCallback((token) => {
    try {
      if (token) localStorage.setItem(AUTH_TOKEN_KEY, token);
      else localStorage.removeItem(AUTH_TOKEN_KEY);
    } catch {}
  }, []);

  const clearSession = useCallback(() => {
    setAuthToken("");
    persistToken("");
    setCurrentUser(null);
  }, [persistToken]);

  const loadMe = useCallback(async () => {
    if (!authToken) return;
    try {
      const res = await axios.get(`${API_BASE_URL}/api/auth/me`, { headers: authHeaders() });
      setCurrentUser(res.data);
    } catch (err) {
      setCurrentUser(null);
      setAuthMessage(`로그인 세션 만료: ${parseError(err, "인증에 실패했습니다.")}`);
      clearSession();
      navigateTo("/login");
    }
  }, [authToken, authHeaders, clearSession, navigateTo]);

  const loadWatchlist = useCallback(async () => {
    if (!authToken) {
      setWatchlistCodes([]);
      return;
    }
    setWatchlistLoading(true);
    try {
      const res = await axios.get(`${API_BASE_URL}/api/watchlist`, { headers: authHeaders() });
      const codes = Array.isArray(res.data) ? res.data.map((x) => x.code).filter(Boolean) : [];
      setWatchlistCodes(Array.from(new Set(codes)));
    } catch (err) {
      setUiMessage(`관심종목 로드 실패: ${parseError(err, "서버 오류")}`);
    } finally {
      setWatchlistLoading(false);
    }
  }, [authToken, authHeaders]);

  const loadPortfolio = useCallback(async () => {
    if (!authToken) return;
    try {
      const res = await axios.get(`${API_BASE_URL}/api/sim/portfolio`, { headers: authHeaders() });
      setPortfolio(res.data);
      if (res.data?.holdings?.length && !tradeableCodes.includes(tradeCode)) {
        setTradeCode(res.data.holdings[0].code);
      }
    } catch (err) {
      setTradeMessage(`포트폴리오 조회 실패: ${parseError(err, "서버 오류")}`);
    }
  }, [authToken, authHeaders, tradeCode, tradeableCodes]);

  const loadLeagueState = useCallback(async () => {
    if (!authToken) return;
    try {
      const res = await axios.get(`${API_BASE_URL}/api/sim/league-state`, { headers: authHeaders() });
      setLeagueState(res.data);
    } catch {}
  }, [authToken, authHeaders]);

  const loadPendingOrders = useCallback(async (options = {}) => {
    if (!authToken) return;
    const silent = Boolean(options?.silent);
    if (!silent) setPendingLoading(true);
    try {
      const res = await axios.get(`${API_BASE_URL}/api/sim/orders/pending`, { headers: authHeaders() });
      setPendingOrders(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      setTradeMessage(`미체결 주문 조회 실패: ${parseError(err, "서버 오류")}`);
    } finally {
      if (!silent) setPendingLoading(false);
    }
  }, [authToken, authHeaders]);

  const loadExecutions = useCallback(async () => {
    if (!authToken) return;
    setExecutionsLoading(true);
    try {
      const res = await axios.get(`${API_BASE_URL}/api/sim/orders/executions`, { headers: authHeaders() });
      setExecutions(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      setTradeMessage(`체결내역 조회 실패: ${parseError(err, "서버 오류")}`);
    } finally {
      setExecutionsLoading(false);
    }
  }, [authToken, authHeaders]);

  const loadRankings = useCallback(async () => {
    if (!authToken) return;
    setRankingsLoading(true);
    try {
      const res = await axios.get(`${API_BASE_URL}/api/sim/rankings`, { headers: authHeaders() });
      setRankings(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      setTradeMessage(`랭킹 조회 실패: ${parseError(err, "서버 오류")}`);
    } finally {
      setRankingsLoading(false);
    }
  }, [authToken, authHeaders]);

  const refreshSimData = useCallback(async () => {
    await Promise.all([loadPortfolio(), loadLeagueState(), loadPendingOrders(), loadExecutions(), loadRankings()]);
  }, [loadPortfolio, loadLeagueState, loadPendingOrders, loadExecutions, loadRankings]);

  useEffect(() => {
    const onResize = () => setIsNarrowScreen(window.innerWidth < 1024);
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  useEffect(() => {
    const onPop = () => setPath(normalizePath(window.location.pathname));
    window.addEventListener("popstate", onPop);
    return () => window.removeEventListener("popstate", onPop);
  }, []);

  useEffect(() => {
    if (authToken) {
      loadMe();
      loadWatchlist();
      loadLeagueState();
      loadPortfolio();
    } else {
      setCurrentUser(null);
      setWatchlistCodes([]);
    }
  }, [authToken, loadLeagueState, loadMe, loadPortfolio, loadWatchlist]);

  useEffect(() => {
    if (!authToken || path !== "/sim") return;
    refreshSimData();
  }, [authToken, path, refreshSimData]);

  useEffect(() => {
    if (!authToken) return;
    const id = window.setInterval(() => {
      loadLeagueState();
      loadPortfolio();
      if (path === "/sim") {
        loadPendingOrders({ silent: true });
        loadExecutions();
        if (simOrderTab === "rankings") loadRankings();
      }
    }, 2000);
    return () => window.clearInterval(id);
  }, [authToken, path, simOrderTab, loadLeagueState, loadPortfolio, loadPendingOrders, loadExecutions, loadRankings]);

  const handleLogin = useCallback(async () => {
    if (!loginName.trim() || !loginPassword.trim()) {
      setAuthMessage("이름과 비밀번호를 입력하세요.");
      return;
    }
    setAuthLoading(true);
    setAuthMessage("");
    try {
      const res = await axios.post(`${API_BASE_URL}/api/auth/login`, { name: loginName.trim(), password: loginPassword });
      const token = res.data?.accessToken || "";
      if (!token) throw new Error("토큰이 없습니다.");
      setAuthToken(token);
      persistToken(token);
      setCurrentUser({ id: res.data?.userId, name: res.data?.name });
      setLoginPassword("");
      navigateTo("/");
    } catch (err) {
      setAuthMessage(`로그인 실패: ${parseError(err, "서버 오류")}`);
    } finally {
      setAuthLoading(false);
    }
  }, [loginName, loginPassword, navigateTo, persistToken]);

  const handleSignup = useCallback(async () => {
    if (!signupName.trim() || !signupPassword.trim()) {
      setAuthMessage("이름과 비밀번호를 입력하세요.");
      return;
    }
    setAuthLoading(true);
    setAuthMessage("");
    try {
      await axios.post(`${API_BASE_URL}/api/auth/signup`, { name: signupName.trim(), password: signupPassword });
      setSignupPassword("");
      setAuthMessage("회원가입이 완료되었습니다. 로그인해 주세요.");
      navigateTo("/login");
      setLoginName(signupName.trim());
    } catch (err) {
      setAuthMessage(`회원가입 실패: ${parseError(err, "서버 오류")}`);
    } finally {
      setAuthLoading(false);
    }
  }, [navigateTo, signupName, signupPassword]);

  const handleLogout = useCallback(() => {
    clearSession();
    setPortfolio(null);
    setLeagueState(null);
    setPendingOrders([]);
    setExecutions([]);
    setRankings([]);
    navigateTo("/login");
  }, [clearSession, navigateTo]);

  const openTradeFromMarket = useCallback((code) => {
    if (code) setTradeCode(code);
    navigateTo("/sim");
  }, [navigateTo]);

  const filteredStocks = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return STOCK_OPTIONS;
    return STOCK_OPTIONS.filter((s) => s.code.includes(q) || s.name.toLowerCase().includes(q));
  }, [searchQuery]);

  const applyFilter = useCallback(() => {
    setApplied({ months: monthsInput });
  }, [monthsInput]);

  const addToView = useCallback((code) => {
    setViewCodes((prev) => {
      if (prev.includes(code)) return prev;
      return [...prev.slice(-(MAX_ACTIVE_CHARTS - 1)), code];
    });
  }, []);

  const removeFromView = useCallback((code) => {
    setViewCodes((prev) => prev.filter((c) => c !== code));
  }, []);

  const reorderViewCodes = useCallback((fromCode, toCode) => {
    if (!fromCode || !toCode || fromCode === toCode) return;
    setViewCodes((prev) => {
      const fromIdx = prev.indexOf(fromCode);
      const toIdx = prev.indexOf(toCode);
      if (fromIdx < 0 || toIdx < 0) return prev;
      const next = [...prev];
      const [moved] = next.splice(fromIdx, 1);
      next.splice(toIdx, 0, moved);
      return next;
    });
  }, []);

  const toggleWatchlist = useCallback(async (code) => {
    if (!authToken) {
      setUiMessage("로그인 후 관심종목을 사용할 수 있습니다.");
      return;
    }
    setWatchlistLoading(true);
    setUiMessage("");
    try {
      if (watchlistCodeSet.has(code)) {
        await axios.delete(`${API_BASE_URL}/api/watchlist/${code}`, { headers: authHeaders() });
        setWatchlistCodes((prev) => prev.filter((c) => c !== code));
      } else {
        await axios.post(`${API_BASE_URL}/api/watchlist`, { code, name: getStockNameByCode(code) }, { headers: authHeaders() });
        setWatchlistCodes((prev) => Array.from(new Set([...prev, code])));
      }
    } catch (err) {
      setUiMessage(`관심종목 저장 실패: ${parseError(err, "서버 오류")}`);
    } finally {
      setWatchlistLoading(false);
    }
  }, [authHeaders, authToken, watchlistCodeSet]);

  const resetSimulation = useCallback(async () => {
    if (!authToken) return;
    setSimLoading(true);
    try {
      const res = await axios.post(`${API_BASE_URL}/api/sim/reset`, {}, { headers: authHeaders() });
      setPortfolio(res.data);
      await Promise.all([loadLeagueState(), loadPendingOrders(), loadExecutions(), loadRankings()]);
      setTradeMessage("모의투자 계좌를 초기화했습니다.");
    } catch (err) {
      setTradeMessage(`초기화 실패: ${parseError(err, "서버 오류")}`);
    } finally {
      setSimLoading(false);
    }
  }, [authHeaders, authToken, loadExecutions, loadLeagueState, loadPendingOrders, loadRankings]);

  const parseQty = useCallback((value) => {
    const n = Number(value);
    if (!Number.isFinite(n)) return 1;
    return Math.max(1, Math.floor(n));
  }, []);

  const openOrderConfirm = useCallback((side, override = {}) => {
    const code = override.code || tradeCode;
    const quantity = parseQty(override.quantity ?? tradeQty);
    const orderType = override.orderType || tradeOrderType;
    const limitPrice = override.limitPrice ?? tradeLimitPrice;
    if (orderType === "LIMIT" && (!String(limitPrice ?? "").trim() || Number(limitPrice) <= 0)) {
      setTradeMessage("지정가 주문은 목표가를 입력해야 합니다.");
      return;
    }
    setTradeCode(code);
    setTradeQty(String(quantity));
    setOrderConfirmDraft({
      side,
      code,
      quantity,
      orderType,
      limitPrice: orderType === "LIMIT" ? Number(limitPrice) : null,
    });
  }, [parseQty, tradeCode, tradeLimitPrice, tradeOrderType, tradeQty]);

  const confirmOrderDraft = useCallback(async () => {
    if (!orderConfirmDraft || !authToken) return;
    setSimLoading(true);
    try {
      const res = await axios.post(
        `${API_BASE_URL}/api/sim/order`,
        {
          code: orderConfirmDraft.code,
          side: orderConfirmDraft.side,
          orderType: orderConfirmDraft.orderType || "MARKET",
          limitPrice: orderConfirmDraft.orderType === "LIMIT" ? Number(orderConfirmDraft.limitPrice) : null,
          quantity: orderConfirmDraft.quantity,
        },
        { headers: authHeaders() }
      );
      setTradeMessage(res.data?.message || "주문이 처리되었습니다.");
      setOrderConfirmDraft(null);
      await Promise.all([loadPortfolio(), loadPendingOrders(), loadExecutions(), loadRankings()]);
    } catch (err) {
      setTradeMessage(`주문 실패: ${parseError(err, "서버 오류")}`);
    } finally {
      setSimLoading(false);
    }
  }, [authHeaders, authToken, loadExecutions, loadPendingOrders, loadPortfolio, loadRankings, orderConfirmDraft]);

  const cancelPendingOrder = useCallback(async (orderId) => {
    if (!authToken) return;
    try {
      await axios.delete(`${API_BASE_URL}/api/sim/orders/pending/${orderId}`, { headers: authHeaders() });
      setTradeMessage("미체결 주문을 취소했습니다.");
      await Promise.all([loadPendingOrders(), loadPortfolio(), loadExecutions(), loadRankings()]);
    } catch (err) {
      setTradeMessage(`주문 취소 실패: ${parseError(err, "서버 오류")}`);
    }
  }, [authHeaders, authToken, loadExecutions, loadPendingOrders, loadPortfolio, loadRankings]);

  const openRankingUserSummary = useCallback(async (targetUserId) => {
    if (!authToken) return;
    setRankingUserSummaryLoading(true);
    try {
      const res = await axios.get(`${API_BASE_URL}/api/sim/rankings/${targetUserId}/portfolio`, { headers: authHeaders() });
      setRankingUserSummary(res.data);
    } catch (err) {
      setTradeMessage(`랭킹 사용자 조회 실패: ${parseError(err, "서버 오류")}`);
    } finally {
      setRankingUserSummaryLoading(false);
    }
  }, [authHeaders, authToken]);

  const selectedTradeHolding = useMemo(() => portfolio?.holdings?.find((h) => h.code === tradeCode) || null, [portfolio, tradeCode]);

  const filteredExecutions = useMemo(() => {
    return (executions || []).filter((e) => {
      if (executionFilterSide !== "ALL" && e.side !== executionFilterSide) return false;
      if (executionFilterCode && e.code !== executionFilterCode) return false;
      return true;
    });
  }, [executions, executionFilterCode, executionFilterSide]);

  const rankingRowsTop10WithMe = useMemo(() => {
    const rows = Array.isArray(rankings) ? rankings : [];
    return rows;
  }, [rankings]);

  const setHoldingQuickQty = useCallback((code, holdQty, ratio) => {
    const qty = Math.max(1, Math.floor(Number(holdQty) * ratio));
    setHoldingOrderQtys((prev) => ({ ...prev, [code]: qty }));
    setTradeCode(code);
    setTradeQty(String(qty));
  }, []);

  const getHoldingOrderQty = useCallback((code) => {
    return parseQty(holdingOrderQtys[code] ?? 1);
  }, [holdingOrderQtys, parseQty]);

  const fmt = useCallback((v) => {
    const n = Number(v || 0);
    return new Intl.NumberFormat("ko-KR").format(Math.round(n));
  }, []);

  const fmtSigned = useCallback((v) => {
    const n = Number(v || 0);
    return `${n > 0 ? "+" : ""}${fmt(n)}`;
  }, [fmt]);

  const fmtDateTime = useCallback((v) => {
    if (!v) return "-";
    const d = new Date(Number(v));
    if (Number.isNaN(d.getTime())) return String(v);
    return d.toLocaleString("ko-KR");
  }, []);

  const getHoldingReturnRate = useCallback((h) => {
    const avg = Number(h?.avgPrice || 0);
    const cur = Number(h?.currentPrice || 0);
    if (!avg) return 0;
    return ((cur - avg) / avg) * 100;
  }, []);

  const authPage = path === "/signup" ? "signup" : "login";
  const challengeDetailId = useMemo(() => {
    const m = String(path || "").match(/^\/challenges\/(\d+)$/);
    return m ? Number(m[1]) : null;
  }, [path]);
  const chartGridColumns = isNarrowScreen ? "1fr" : "repeat(2, minmax(0, 1fr))";
  const chartDisplayMonths = useMemo(() => {
    if (chartDisplayMode === "compact") return Math.min(120, Math.max(applied.months * 2, applied.months + 12));
    if (chartDisplayMode === "expanded") return Math.max(1, Math.floor(applied.months / 2));
    return applied.months;
  }, [applied.months, chartDisplayMode]);
  const chartCardHeight = chartDisplayMode === "expanded" ? 340 : chartDisplayMode === "compact" ? 220 : 260;

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="app-header-brand">
          <div className="app-title">Fintech Mock Investing</div>
          <div className="app-subtitle">차트 조회 + 모의투자 + 랭킹</div>
        </div>        <nav className="app-nav">
          <button type="button" onClick={() => navigateTo("/")} className={path === "/" ? "app-nav-btn active" : "app-nav-btn"}>Home</button>
          <button type="button" onClick={() => navigateTo("/charts")} className={path === "/charts" ? "app-nav-btn active" : "app-nav-btn"}>Charts</button>
          <button type="button" onClick={() => navigateTo("/sim")} className={path === "/sim" ? "app-nav-btn active" : "app-nav-btn"}>Simulation</button>
          <button type="button" onClick={() => navigateTo("/market")} className={path === "/market" ? "app-nav-btn active" : "app-nav-btn"}>Market</button>
          <button type="button" onClick={() => navigateTo("/news")} className={path === "/news" ? "app-nav-btn active" : "app-nav-btn"}>News</button>
          <button type="button" onClick={() => navigateTo("/league")} className={path === "/league" ? "app-nav-btn active" : "app-nav-btn"}>League</button>
          {isLoggedIn && <button type="button" onClick={() => navigateTo("/challenges")} className={path === "/challenges" ? "app-nav-btn active" : "app-nav-btn"}>Challenges</button>}
          {isLoggedIn && <button type="button" onClick={() => navigateTo("/notifications")} className={path === "/notifications" ? "app-nav-btn active" : "app-nav-btn"}>Notifications</button>}
          {isLoggedIn && <button type="button" onClick={() => navigateTo("/profile")} className={path === "/profile" ? "app-nav-btn active" : "app-nav-btn"}>Profile</button>}
          {!isLoggedIn && <button type="button" onClick={() => navigateTo("/login")} className={path === "/login" ? "app-nav-btn active" : "app-nav-btn"}>Login</button>}
          {!isLoggedIn && <button type="button" onClick={() => navigateTo("/signup")} className={path === "/signup" ? "app-nav-btn active" : "app-nav-btn"}>Sign Up</button>}
          {isLoggedIn && (
            <>
              <span className="app-user-name">{currentUser?.name || "User"}</span>
              <button type="button" onClick={handleLogout} className="app-nav-btn">Logout</button>
            </>
          )}
        </nav>
      </header>

      <main className="app-main">
        {(path === "/login" || path === "/signup") && (
          <AuthPage
            authPage={authPage}
            authLoading={authLoading}
            authMessage={authMessage}
            loginName={loginName}
            setLoginName={setLoginName}
            loginPassword={loginPassword}
            setLoginPassword={setLoginPassword}
            signupName={signupName}
            setSignupName={setSignupName}
            signupPassword={signupPassword}
            setSignupPassword={setSignupPassword}
            onLogin={handleLogin}
            onSignup={handleSignup}
            navigateTo={navigateTo}
          />
        )}

        {path === "/" && (
          <HomePage
            apiBaseUrl={API_BASE_URL}
            isLoggedIn={isLoggedIn}
            currentUser={currentUser}
            leagueState={leagueState}
            portfolio={portfolio}
            rankings={rankings}
            rankingsLoading={rankingsLoading}
            loadRankings={loadRankings}
            watchlistCodes={watchlistCodes}
            getStockNameByCode={getStockNameByCode}
            navigateTo={navigateTo}
            openTradeFromMarket={openTradeFromMarket}
            chartEndDate={chartEndDate}
            fmt={fmt}
            openRankingUserSummary={openRankingUserSummary}
            rankingUserSummary={rankingUserSummary}
            setRankingUserSummary={setRankingUserSummary}
            rankingUserSummaryLoading={rankingUserSummaryLoading}
          />
        )}

        {path === "/charts" && (
          <ChartsPage
            apiBaseUrl={API_BASE_URL}
            filteredStocks={filteredStocks}
            viewCodeSet={viewCodeSet}
            watchlistCodeSet={watchlistCodeSet}
            searchQuery={searchQuery}
            setSearchQuery={setSearchQuery}
            monthsInput={monthsInput}
            setMonthsInput={setMonthsInput}
            applyFilter={applyFilter}
            chartDisplayMode={chartDisplayMode}
            setChartDisplayMode={setChartDisplayMode}
            chartDisplayMonths={chartDisplayMonths}
            chartCardHeight={chartCardHeight}
            addToView={addToView}
            removeFromView={removeFromView}
            reorderViewCodes={reorderViewCodes}
            toggleWatchlist={toggleWatchlist}
            watchlistLoading={watchlistLoading}
            uiMessage={uiMessage}
            isLoggedIn={isLoggedIn}
            watchlistCodes={watchlistCodes}
            applied={applied}
            chartCodes={viewCodes}
            chartGridColumns={chartGridColumns}
            chartEndDate={chartEndDate}
            leagueState={leagueState}
            getStockNameByCode={getStockNameByCode}
          />
        )}


        {path === "/market" && (
          <MarketPage
            apiBaseUrl={API_BASE_URL}
            chartEndDate={chartEndDate}
            watchlistCodeSet={watchlistCodeSet}
            toggleWatchlist={toggleWatchlist}
            watchlistLoading={watchlistLoading}
            isLoggedIn={isLoggedIn}
            getStockNameByCode={getStockNameByCode}
            selectedTradeCode={tradeCode}
            openTradeFromMarket={openTradeFromMarket}
            fmt={fmt}
          />
        )}

        {path === "/news" && (
          <NewsPage chartEndDate={chartEndDate} />
        )}

        {path === "/challenges" && (
          <ChallengesPage
            apiBaseUrl={API_BASE_URL}
            authToken={authToken}
            isLoggedIn={isLoggedIn}
            currentUser={currentUser}
            leagueState={leagueState}
            tradeableCodes={tradeableCodes}
            getStockNameByCode={getStockNameByCode}
            fmt={fmt}
            fmtDateTime={fmtDateTime}
            navigateTo={navigateTo}
          />
        )}

        {path === "/challenges/history" && (
          <ChallengeHistoryPage
            apiBaseUrl={API_BASE_URL}
            authToken={authToken}
            isLoggedIn={isLoggedIn}
            leagueState={leagueState}
            fmt={fmt}
            navigateTo={navigateTo}
          />
        )}

        {challengeDetailId && (
          <ChallengeDetailPage
            apiBaseUrl={API_BASE_URL}
            authToken={authToken}
            isLoggedIn={isLoggedIn}
            challengeId={challengeDetailId}
            currentUser={currentUser}
            leagueState={leagueState}
            fmt={fmt}
            fmtDateTime={fmtDateTime}
            getStockNameByCode={getStockNameByCode}
            navigateTo={navigateTo}
          />
        )}

        {path === "/notifications" && (
          <NotificationsPage
            apiBaseUrl={API_BASE_URL}
            authToken={authToken}
            isLoggedIn={isLoggedIn}
            fmtDateTime={fmtDateTime}
            navigateTo={navigateTo}
          />
        )}

        {path === "/profile" && (
          <ProfilePage
            apiBaseUrl={API_BASE_URL}
            authToken={authToken}
            isLoggedIn={isLoggedIn}
          />
        )}

        {path === "/league" && (
          <LeaguePage
            isLoggedIn={isLoggedIn}
            leagueState={leagueState}
            rankings={rankings}
            rankingsLoading={rankingsLoading}
            loadRankings={loadRankings}
            fmt={fmt}
            getStockNameByCode={getStockNameByCode}
            openRankingUserSummary={openRankingUserSummary}
            rankingUserSummary={rankingUserSummary}
            setRankingUserSummary={setRankingUserSummary}
            rankingUserSummaryLoading={rankingUserSummaryLoading}
            navigateTo={navigateTo}
          />
        )}

        {path === "/sim" && (
          <SimulationPage
            apiBaseUrl={API_BASE_URL}
            authToken={authToken}
            isLoggedIn={isLoggedIn}
            isLeagueAdmin={isLeagueAdmin}
            simLoading={simLoading}
            resetSimulation={resetSimulation}
            leagueState={leagueState}
            portfolio={portfolio}
            tradeCode={tradeCode}
            setTradeCode={setTradeCode}
            tradeQty={tradeQty}
            setTradeQty={setTradeQty}
            tradeOrderType={tradeOrderType}
            setTradeOrderType={setTradeOrderType}
            tradeLimitPrice={tradeLimitPrice}
            setTradeLimitPrice={setTradeLimitPrice}
            tradeableCodes={tradeableCodes}
            getStockNameByCode={getStockNameByCode}
            tradeMessage={tradeMessage}
            chartEndDate={chartEndDate}
            setSimSelectedPrice={setSimSelectedPrice}
            simSelectedPrice={simSelectedPrice}
            selectedTradeHolding={selectedTradeHolding}
            simOrderTab={simOrderTab}
            setSimOrderTab={setSimOrderTab}
            pendingLoading={pendingLoading}
            pendingOrders={pendingOrders}
            cancelPendingOrder={cancelPendingOrder}
            executionsLoading={executionsLoading}
            filteredExecutions={filteredExecutions}
            executionFilterSide={executionFilterSide}
            setExecutionFilterSide={setExecutionFilterSide}
            executionFilterCode={executionFilterCode}
            setExecutionFilterCode={setExecutionFilterCode}
            rankingsLoading={rankingsLoading}
            rankings={rankings}
            rankingRowsTop10WithMe={rankingRowsTop10WithMe}
            rankingPeriod={rankingPeriod}
            setRankingPeriod={setRankingPeriod}
            openRankingUserSummary={openRankingUserSummary}
            rankingUserSummary={rankingUserSummary}
            setRankingUserSummary={setRankingUserSummary}
            rankingUserSummaryLoading={rankingUserSummaryLoading}
            fmt={fmt}
            fmtDateTime={fmtDateTime}
            fmtSigned={fmtSigned}
            getHoldingReturnRate={getHoldingReturnRate}
            holdingOrderQtys={holdingOrderQtys}
            setHoldingOrderQtys={setHoldingOrderQtys}
            setHoldingQuickQty={setHoldingQuickQty}
            getHoldingOrderQty={getHoldingOrderQty}
            openOrderConfirm={openOrderConfirm}
            orderConfirmDraft={orderConfirmDraft}
            setOrderConfirmDraft={setOrderConfirmDraft}
            confirmOrderDraft={confirmOrderDraft}
          />
        )}
      </main>
    </div>
  );
}
