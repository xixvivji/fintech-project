import React, { useMemo, useEffect, useRef, useState, useCallback } from 'react';
import { createChart } from 'lightweight-charts';
import axios from 'axios';
import './App.css';

const PERIOD_OPTIONS = [
    { label: '6개월', value: 6 },
    { label: '1년', value: 12 },
    { label: '2년', value: 24 },
    { label: '3년', value: 36 },
    { label: '5년', value: 60 },
];
const API_BASE_URL = 'http://localhost:8080';
const AUTH_TOKEN_KEY = 'fintech_access_token';
const INITIAL_CASH = 50000000;

const STOCK_OPTIONS = [
    { name: '삼성전자', code: '005930' },
    { name: 'SK하이닉스', code: '000660' },
    { name: 'NAVER', code: '035420' },
    { name: '카카오', code: '035720' },
    { name: 'LG에너지솔루션', code: '373220' },
    { name: '현대차', code: '005380' },
    { name: '기아', code: '000270' },
    { name: '셀트리온', code: '068270' },
    { name: '삼성바이오로직스', code: '207940' },
    { name: 'POSCO홀딩스', code: '005490' },
    { name: 'KB금융', code: '105560' },
    { name: '신한지주', code: '055550' },
    { name: '하나금융지주', code: '086790' },
    { name: '우리금융지주', code: '316140' },
    { name: '삼성물산', code: '028260' },
    { name: 'LG화학', code: '051910' },
    { name: '삼성SDI', code: '006400' },
    { name: 'LG전자', code: '066570' },
    { name: '한화에어로스페이스', code: '012450' },
    { name: 'HD현대중공업', code: '329180' },
    { name: '두산에너빌리티', code: '034020' },
    { name: '포스코퓨처엠', code: '003670' },
    { name: '에코프로비엠', code: '247540' },
    { name: '에코프로', code: '086520' },
    { name: '알테오젠', code: '196170' },
    { name: 'HMM', code: '011200' },
    { name: '대한항공', code: '003490' },
    { name: 'KT&G', code: '033780' },
    { name: '한국전력', code: '015760' },
    { name: '삼성전기', code: '009150' },
    { name: 'SK이노베이션', code: '096770' },
];

const MAX_ACTIVE_CHARTS = 5;
const APPLY_DEBOUNCE_MS = 800;

function getStockNameByCode(code) {
    const stock = STOCK_OPTIONS.find((item) => item.code === code);
    return stock ? stock.name : code;
}

function mapServerErrorMessage(message) {
    if (!message || typeof message !== 'string') return '';
    if (message.includes('EGW00133')) {
        return '토큰 발급 제한에 걸렸습니다. 잠시 후(약 1분) 다시 시도해 주세요.';
    }
    if (message.includes('EGW00201')) {
        return '요청이 너무 빈번합니다. 조회 속도를 자동으로 조절 중이니 잠시 후 다시 시도해 주세요.';
    }
    if (message.includes('output2 없음')) {
        return '해당 기간에 조회 가능한 데이터가 없습니다.';
    }
    return message;
}

function normalizeCodes(codes) {
    return Array.from(new Set(codes.filter(Boolean)));
}

function readStoredToken() {
    try {
        return localStorage.getItem(AUTH_TOKEN_KEY) || '';
    } catch (e) {
        return '';
    }
}

function normalizeAuthPath(pathname) {
    if (pathname === '/signup') return '/signup';
    if (pathname === '/login') return '/login';
    if (pathname === '/sim') return '/sim';
    return '/charts';
}

function normalizeCandleData(rawData) {
    if (!Array.isArray(rawData)) return [];

    const byTime = new Map();
    for (const item of rawData) {
        const time = item?.time;
        const open = Number(item?.open);
        const high = Number(item?.high);
        const low = Number(item?.low);
        const close = Number(item?.close);

        if (typeof time !== 'string' || time.length !== 10) continue;
        if (![open, high, low, close].every(Number.isFinite)) continue;
        if (high < low) continue;

        byTime.set(time, { time, open, high, low, close });
    }

    return Array.from(byTime.values()).sort((a, b) => a.time.localeCompare(b.time));
}

function StockChartCard({ code, months, requestDelayMs, endDate, height = 360, onLatestPriceChange = null }) {
    const chartContainerRef = useRef(null);
    const chartRef = useRef(null);
    const requestSeqRef = useRef(0);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    useEffect(() => {
        if (!chartContainerRef.current) return;

        chartContainerRef.current.innerHTML = '';
        const chart = createChart(chartContainerRef.current, {
            width: chartContainerRef.current.clientWidth,
            height,
            layout: {
                background: { color: '#ffffff' },
                textColor: '#333',
            },
            grid: {
                vertLines: { color: '#f0f0f0' },
                horzLines: { color: '#f0f0f0' },
            },
        });

        const candlestickSeries = chart.addCandlestickSeries({
            upColor: '#ef5350',
            downColor: '#26a69a',
            wickUpColor: '#ef5350',
            wickDownColor: '#26a69a',
        });

        chartRef.current = chart;
        setLoading(true);
        setError('');

        const requestSeq = ++requestSeqRef.current;
        const controller = new AbortController();

        const timer = setTimeout(() => {
            axios
                .get(`${API_BASE_URL}/api/stock/chart/${code}`, {
                    params: { months, endDate },
                    signal: controller.signal,
                })
                .then((res) => {
                    if (requestSeq !== requestSeqRef.current) return;
                    const normalized = normalizeCandleData(res.data);
                    if (normalized.length === 0) {
                        setError('데이터가 없습니다. 종목 코드를 확인해 주세요.');
                        return;
                    }
                    candlestickSeries.setData(normalized);
                    chart.timeScale().fitContent();
                    if (typeof onLatestPriceChange === 'function') {
                        const latest = normalized[normalized.length - 1];
                        onLatestPriceChange(latest ? { price: latest.close, time: latest.time } : null);
                    }
                })
                .catch((err) => {
                    if (requestSeq !== requestSeqRef.current) return;
                    if (err?.code === 'ERR_CANCELED') return;
                    if (typeof onLatestPriceChange === 'function') {
                        onLatestPriceChange(null);
                    }
                    const serverMessage = err?.response?.data?.message;
                    if (typeof serverMessage === 'string' && serverMessage.trim().length > 0) {
                        setError(mapServerErrorMessage(serverMessage));
                        return;
                    }
                    const runtimeMessage = err?.message;
                    if (typeof runtimeMessage === 'string' && runtimeMessage.trim().length > 0) {
                        setError(`조회 실패: ${runtimeMessage}`);
                        return;
                    }
                    setError('조회 실패: 백엔드 실행 상태와 종목 코드를 확인해 주세요.');
                })
                .finally(() => {
                    if (requestSeq !== requestSeqRef.current) return;
                    setLoading(false);
                });
        }, requestDelayMs);

        const handleResize = () => {
            if (chartRef.current && chartContainerRef.current) {
                chartRef.current.applyOptions({
                    width: chartContainerRef.current.clientWidth,
                });
            }
        };

        window.addEventListener('resize', handleResize);

        return () => {
            clearTimeout(timer);
            controller.abort();
            window.removeEventListener('resize', handleResize);
            if (chartRef.current) {
                chartRef.current.remove();
                chartRef.current = null;
            }
        };
    }, [code, months, requestDelayMs, endDate, height, onLatestPriceChange]);

    return (
        <div
            style={{
                width: '100%',
                minWidth: 0,
                padding: '12px',
                backgroundColor: '#fff',
                borderRadius: '10px',
                border: '1px solid #e8e8e8',
                boxShadow: '0 2px 10px rgba(0,0,0,0.04)',
            }}
        >
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                <strong>{code}</strong>
                <span style={{ color: '#666', fontSize: '13px' }}>{months}개월</span>
            </div>
            {loading && <div style={{ marginBottom: '8px', color: '#ef5350' }}>데이터 불러오는 중...</div>}
            {error && <div style={{ marginBottom: '8px', color: '#d32f2f' }}>{error}</div>}
            <div ref={chartContainerRef} style={{ minHeight: `${height}px` }} />
        </div>
    );
}

function App() {
    const [authToken, setAuthToken] = useState(readStoredToken);
    const [currentPath, setCurrentPath] = useState(() => normalizeAuthPath(window.location.pathname));
    const [loginName, setLoginName] = useState('');
    const [loginPassword, setLoginPassword] = useState('');
    const [signupName, setSignupName] = useState('');
    const [signupPassword, setSignupPassword] = useState('');
    const [authLoading, setAuthLoading] = useState(false);
    const [authMessage, setAuthMessage] = useState('');
    const [currentUser, setCurrentUser] = useState(null);
    const [viewCodes, setViewCodes] = useState([]);
    const [watchlistCodes, setWatchlistCodes] = useState([]);
    const [monthsInput, setMonthsInput] = useState(24);
    const [searchQuery, setSearchQuery] = useState('');
    const [uiMessage, setUiMessage] = useState('');
    const [lastApplyAt, setLastApplyAt] = useState(0);
    const [isNarrowScreen, setIsNarrowScreen] = useState(window.innerWidth < 1024);
    const [watchlistLoading, setWatchlistLoading] = useState(false);
    const [simLoading, setSimLoading] = useState(false);
    const [replayLoading, setReplayLoading] = useState(false);
    const [portfolio, setPortfolio] = useState(null);
    const [replayState, setReplayState] = useState(null);
    const [pendingOrders, setPendingOrders] = useState([]);
    const [executions, setExecutions] = useState([]);
    const [rankings, setRankings] = useState([]);
    const [pendingLoading, setPendingLoading] = useState(false);
    const [executionsLoading, setExecutionsLoading] = useState(false);
    const [rankingsLoading, setRankingsLoading] = useState(false);
    const [simOrderTab, setSimOrderTab] = useState('pending');
    const [executionFilterSide, setExecutionFilterSide] = useState('ALL');
    const [executionFilterCode, setExecutionFilterCode] = useState('');
    const [orderConfirmDraft, setOrderConfirmDraft] = useState(null);
    const [tradeCode, setTradeCode] = useState('');
    const [tradeQty, setTradeQty] = useState(1);
    const [simSelectedPrice, setSimSelectedPrice] = useState(null);
    const [holdingOrderQtys, setHoldingOrderQtys] = useState({});
    const [tradeMessage, setTradeMessage] = useState('');
    const [applied, setApplied] = useState({
        codes: [],
        months: 24,
    });

    const viewCodeSet = useMemo(() => new Set(viewCodes), [viewCodes]);
    const watchlistCodeSet = useMemo(() => new Set(watchlistCodes), [watchlistCodes]);

    const filteredStocks = useMemo(() => {
        const query = searchQuery.trim().toLowerCase();
        if (query.length === 0) return STOCK_OPTIONS;
        return STOCK_OPTIONS.filter((stock) => stock.name.toLowerCase().includes(query) || stock.code.includes(query));
    }, [searchQuery]);

    const chartGridColumns = useMemo(() => {
        if (isNarrowScreen) return '1fr';
        return normalizeCodes(applied.codes).length <= 1 ? '1fr' : 'repeat(2, minmax(0, 1fr))';
    }, [applied.codes, isNarrowScreen]);
    const chartCodes = useMemo(() => normalizeCodes(applied.codes), [applied.codes]);
    const chartEndDate = useMemo(() => portfolio?.valuationDate || replayState?.currentDate || null, [portfolio, replayState]);
    const tradeableCodes = useMemo(() => STOCK_OPTIONS.map((item) => item.code), []);
    const filteredExecutions = useMemo(() => {
        return executions.filter((item) => {
            if (executionFilterSide !== 'ALL' && item.side !== executionFilterSide) return false;
            if (executionFilterCode && item.code !== executionFilterCode) return false;
            return true;
        });
    }, [executions, executionFilterCode, executionFilterSide]);
    const selectedTradeHolding = useMemo(
        () => portfolio?.holdings?.find((h) => h.code === tradeCode) || null,
        [portfolio?.holdings, tradeCode]
    );
    const isLoggedIn = authToken.trim().length > 0;
    const authPage = currentPath === '/signup' ? 'signup' : 'login';
    const isAuthRoute = currentPath === '/login' || currentPath === '/signup';
    const mainPage = currentPath === '/sim' ? 'sim' : 'charts';

    useEffect(() => {
        const handleResize = () => setIsNarrowScreen(window.innerWidth < 1024);
        window.addEventListener('resize', handleResize);
        return () => window.removeEventListener('resize', handleResize);
    }, []);

    useEffect(() => {
        const onPopState = () => setCurrentPath(normalizeAuthPath(window.location.pathname));
        window.addEventListener('popstate', onPopState);
        return () => window.removeEventListener('popstate', onPopState);
    }, []);

    const navigateTo = useCallback((path, replace = false) => {
        const next = normalizeAuthPath(path);
        const current = normalizeAuthPath(window.location.pathname);
        if (current === next) {
            setCurrentPath(next);
            return;
        }
        if (replace) {
            window.history.replaceState({}, '', next);
        } else {
            window.history.pushState({}, '', next);
        }
        setCurrentPath(next);
    }, []);

    useEffect(() => {
        if (isLoggedIn) {
            axios.defaults.headers.common.Authorization = `Bearer ${authToken}`;
        } else {
            delete axios.defaults.headers.common.Authorization;
        }
    }, [authToken, isLoggedIn]);

    useEffect(() => {
        if (isLoggedIn && isAuthRoute) {
            navigateTo('/charts', true);
            return;
        }
        if (!isLoggedIn && !isAuthRoute) {
            navigateTo('/login', true);
        }
    }, [isLoggedIn, isAuthRoute, navigateTo]);

    const clearSessionState = useCallback(() => {
        setCurrentUser(null);
        setWatchlistCodes([]);
        setPortfolio(null);
        setReplayState(null);
        setPendingOrders([]);
        setExecutions([]);
        setRankings([]);
        setSimOrderTab('pending');
        setTradeMessage('');
    }, []);

    const fetchMe = useCallback(async () => {
        if (!isLoggedIn) return;
        try {
            const res = await axios.get(`${API_BASE_URL}/api/auth/me`);
            setCurrentUser(res.data || null);
            setAuthMessage('');
        } catch (err) {
            const serverMessage = err?.response?.data?.message;
            setCurrentUser(null);
            setAuthToken('');
            navigateTo('/login', true);
            try {
                localStorage.removeItem(AUTH_TOKEN_KEY);
            } catch (e) {
                // ignore
            }
            clearSessionState();
            if (typeof serverMessage === 'string' && serverMessage.trim()) {
                setAuthMessage(`로그인 세션 만료: ${serverMessage}`);
            } else {
                setAuthMessage('로그인 세션이 만료되었습니다. 다시 로그인해 주세요.');
            }
        }
    }, [clearSessionState, isLoggedIn, navigateTo]);

    useEffect(() => {
        if (!isLoggedIn) {
            clearSessionState();
            return;
        }
        fetchMe();
    }, [isLoggedIn, fetchMe, clearSessionState]);

    const handleSignup = async () => {
        const name = signupName.trim();
        const password = signupPassword;
        if (!name || !password) {
            setAuthMessage('이름/비밀번호를 입력해 주세요.');
            return;
        }
        setAuthLoading(true);
        try {
            await axios.post(`${API_BASE_URL}/api/auth/signup`, { name, password });
            setAuthMessage('회원가입 완료. 로그인 페이지로 이동합니다.');
            navigateTo('/login');
            setLoginName(name);
            setSignupPassword('');
        } catch (err) {
            const serverMessage = err?.response?.data?.message;
            if (typeof serverMessage === 'string' && serverMessage.trim()) {
                setAuthMessage(`회원가입 실패: ${serverMessage}`);
            } else {
                setAuthMessage('회원가입 실패');
            }
        } finally {
            setAuthLoading(false);
        }
    };

    const handleLogin = async () => {
        const name = loginName.trim();
        const password = loginPassword;
        if (!name || !password) {
            setAuthMessage('이름/비밀번호를 입력해 주세요.');
            return;
        }
        setAuthLoading(true);
        try {
            const res = await axios.post(`${API_BASE_URL}/api/auth/login`, { name, password });
            const token = res?.data?.accessToken;
            if (typeof token !== 'string' || !token.trim()) {
                setAuthMessage('로그인 실패: 토큰이 없습니다.');
                return;
            }
            setAuthToken(token.trim());
            try {
                localStorage.setItem(AUTH_TOKEN_KEY, token.trim());
            } catch (e) {
                // ignore
            }
            setCurrentUser({ id: res?.data?.userId, name: res?.data?.name || name });
            setAuthMessage('');
            setLoginPassword('');
        } catch (err) {
            const serverMessage = err?.response?.data?.message;
            if (typeof serverMessage === 'string' && serverMessage.trim()) {
                setAuthMessage(`로그인 실패: ${serverMessage}`);
            } else {
                setAuthMessage('로그인 실패');
            }
        } finally {
            setAuthLoading(false);
        }
    };

    const handleLogout = () => {
        setAuthToken('');
        navigateTo('/login');
        try {
            localStorage.removeItem(AUTH_TOKEN_KEY);
        } catch (e) {
            // ignore
        }
        clearSessionState();
        setAuthMessage('로그아웃되었습니다.');
    };

    const loadWatchlist = useCallback(async () => {
        if (!isLoggedIn) {
            setWatchlistCodes([]);
            return;
        }
        setWatchlistLoading(true);
        try {
            const res = await axios.get(`${API_BASE_URL}/api/watchlist`);
            const codes = Array.isArray(res.data) ? res.data.map((item) => item?.code).filter(Boolean) : [];
            const normalized = normalizeCodes(codes);
            setWatchlistCodes(normalized);
            if (viewCodes.length === 0) {
                const initial = normalized.slice(0, MAX_ACTIVE_CHARTS);
                setViewCodes(initial);
                setApplied((prev) => ({ ...prev, codes: initial }));
            }
            setUiMessage('');
        } catch (err) {
            const serverMessage = err?.response?.data?.message;
            if (typeof serverMessage === 'string' && serverMessage.trim().length > 0) {
                setUiMessage(`관심종목 로드 실패: ${serverMessage}`);
            } else {
                setUiMessage('관심종목 로드 실패: 백엔드 연결 상태를 확인해 주세요.');
            }
        } finally {
            setWatchlistLoading(false);
        }
    }, [isLoggedIn, viewCodes.length]);

    useEffect(() => {
        loadWatchlist();
    }, [loadWatchlist]);

    const loadPortfolio = useCallback(async () => {
        if (!isLoggedIn) {
            setPortfolio(null);
            return;
        }
        setSimLoading(true);
        try {
            const res = await axios.get(`${API_BASE_URL}/api/sim/portfolio`);
            setPortfolio(res.data);
            setTradeMessage('');
        } catch (err) {
            const serverMessage = err?.response?.data?.message;
            if (typeof serverMessage === 'string' && serverMessage.trim().length > 0) {
                setTradeMessage(`포트폴리오 조회 실패: ${serverMessage}`);
            } else {
                setTradeMessage('포트폴리오 조회 실패');
            }
        } finally {
            setSimLoading(false);
        }
    }, [isLoggedIn]);

    useEffect(() => {
        loadPortfolio();
    }, [loadPortfolio]);

    const loadPendingOrders = useCallback(async () => {
        if (!isLoggedIn) {
            setPendingOrders([]);
            return;
        }
        setPendingLoading(true);
        try {
            const res = await axios.get(`${API_BASE_URL}/api/sim/orders/pending`);
            setPendingOrders(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            const serverMessage = err?.response?.data?.message;
            if (typeof serverMessage === 'string' && serverMessage.trim().length > 0) {
                setTradeMessage(`미체결 주문 조회 실패: ${serverMessage}`);
            }
        } finally {
            setPendingLoading(false);
        }
    }, [isLoggedIn]);

    const loadExecutions = useCallback(async () => {
        if (!isLoggedIn) {
            setExecutions([]);
            return;
        }
        setExecutionsLoading(true);
        try {
            const res = await axios.get(`${API_BASE_URL}/api/sim/orders/executions`);
            setExecutions(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            const serverMessage = err?.response?.data?.message;
            if (typeof serverMessage === 'string' && serverMessage.trim().length > 0) {
                setTradeMessage(`체결내역 조회 실패: ${serverMessage}`);
            }
        } finally {
            setExecutionsLoading(false);
        }
    }, [isLoggedIn]);

    const loadRankings = useCallback(async () => {
        if (!isLoggedIn) {
            setRankings([]);
            return;
        }
        setRankingsLoading(true);
        try {
            const res = await axios.get(`${API_BASE_URL}/api/sim/rankings`);
            setRankings(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            const serverMessage = err?.response?.data?.message;
            if (typeof serverMessage === 'string' && serverMessage.trim().length > 0) {
                setTradeMessage(`랭킹 조회 실패: ${serverMessage}`);
            }
        } finally {
            setRankingsLoading(false);
        }
    }, [isLoggedIn]);

    useEffect(() => {
        loadPendingOrders();
    }, [loadPendingOrders]);

    useEffect(() => {
        loadExecutions();
    }, [loadExecutions]);

    useEffect(() => {
        loadRankings();
    }, [loadRankings]);

    const loadReplayState = useCallback(async () => {
        if (!isLoggedIn) {
            setReplayState(null);
            return;
        }
        try {
            const res = await axios.get(`${API_BASE_URL}/api/sim/replay/state`);
            setReplayState(res.data);
            if (res.data?.portfolio) {
                setPortfolio(res.data.portfolio);
            }
        } catch (err) {
            const serverMessage = err?.response?.data?.message;
            if (typeof serverMessage === 'string' && serverMessage.trim().length > 0) {
                setTradeMessage(`리플레이 상태 조회 실패: ${serverMessage}`);
            }
        }
    }, [isLoggedIn]);

    useEffect(() => {
        loadReplayState();
    }, [loadReplayState]);

    useEffect(() => {
        if (!replayState?.running) return;
        const timer = setInterval(() => {
            loadReplayState();
        }, 5000);
        return () => clearInterval(timer);
    }, [replayState?.running, loadReplayState]);

    useEffect(() => {
        if (!isLoggedIn) return;
        loadPendingOrders();
    }, [isLoggedIn, replayState?.currentDate, loadPendingOrders]);

    useEffect(() => {
        if (!isLoggedIn) return;
        loadExecutions();
    }, [isLoggedIn, replayState?.currentDate, loadExecutions]);

    useEffect(() => {
        if (!isLoggedIn) return;
        loadRankings();
    }, [isLoggedIn, replayState?.currentDate, loadRankings]);

    useEffect(() => {
        if (!isLoggedIn || mainPage !== 'sim') return;
        if (!replayState) return;
        if (replayState.running || replayState.anchorDate) return;
        if (replayLoading || simLoading) return;

        let cancelled = false;
        const autoStart = async () => {
            setReplayLoading(true);
            try {
                await axios.post(`${API_BASE_URL}/api/sim/replay/start`, {});
                if (cancelled) return;
                await loadReplayState();
                await loadPendingOrders();
                await loadExecutions();
                await loadRankings();
            } catch (err) {
                if (cancelled) return;
                const serverMessage = err?.response?.data?.message;
                if (typeof serverMessage === 'string' && serverMessage.trim().length > 0) {
                    setTradeMessage(serverMessage);
                }
            } finally {
                if (!cancelled) setReplayLoading(false);
            }
        };

        autoStart();
        return () => {
            cancelled = true;
        };
    }, [isLoggedIn, mainPage, replayState, replayLoading, simLoading, loadReplayState, loadPendingOrders, loadExecutions, loadRankings]);

    useEffect(() => {
        if (tradeableCodes.length === 0) {
            setTradeCode('');
            return;
        }
        if (!tradeCode || !tradeableCodes.includes(tradeCode)) {
            setTradeCode(tradeableCodes[0]);
        }
    }, [tradeableCodes, tradeCode]);

    useEffect(() => {
        setSimSelectedPrice(null);
    }, [tradeCode]);

    useEffect(() => {
        if (!portfolio?.holdings) return;
        setHoldingOrderQtys((prev) => {
            const next = { ...prev };
            const validCodes = new Set();
            for (const h of portfolio.holdings) {
                validCodes.add(h.code);
                if (!next[h.code]) {
                    next[h.code] = 1;
                }
            }
            Object.keys(next).forEach((code) => {
                if (!validCodes.has(code)) {
                    delete next[code];
                }
            });
            return next;
        });
    }, [portfolio?.holdings]);

    const applyFilter = () => {
        const now = Date.now();
        if (now - lastApplyAt < APPLY_DEBOUNCE_MS) return;
        setLastApplyAt(now);

        const normalizedSelected = normalizeCodes(viewCodes);
        const limitedCodes = normalizedSelected.slice(0, MAX_ACTIVE_CHARTS);
        if (normalizedSelected.length > MAX_ACTIVE_CHARTS) {
            setUiMessage(`동시 조회는 최대 ${MAX_ACTIVE_CHARTS}종목까지 가능합니다. 앞의 ${MAX_ACTIVE_CHARTS}종목만 적용됩니다.`);
        } else {
            setUiMessage('');
        }

        const safeMonths = Math.max(1, Math.min(Number(monthsInput) || 6, 120));
        setApplied({
            codes: limitedCodes,
            months: safeMonths,
        });
    };

    const addToView = (code) => {
        setViewCodes((prev) => {
            const normalizedPrev = normalizeCodes(prev);
            if (!normalizedPrev.includes(code) && normalizedPrev.length >= MAX_ACTIVE_CHARTS) {
                setUiMessage(`최대 ${MAX_ACTIVE_CHARTS}종목까지 조회할 수 있습니다.`);
                return normalizedPrev;
            }
            const next = normalizeCodes([...normalizedPrev, code]);
            const safeMonths = Math.max(1, Math.min(Number(monthsInput) || 6, 120));
            setApplied({ codes: next, months: safeMonths });
            setUiMessage('');
            return next;
        });
        setSearchQuery('');
    };

    const removeFromView = (code) => {
        setViewCodes((prev) => {
            const next = normalizeCodes(prev.filter((c) => c !== code));
            const safeMonths = Math.max(1, Math.min(Number(monthsInput) || 6, 120));
            setApplied({ codes: next, months: safeMonths });
            return next;
        });
        setUiMessage('');
    };

    const toggleWatchlist = async (code) => {
        if (!isLoggedIn) {
            setUiMessage('로그인 후 관심종목을 사용할 수 있습니다.');
            return;
        }
        if (watchlistLoading) return;
        const inWatchlist = watchlistCodeSet.has(code);
        setWatchlistLoading(true);
        try {
            if (inWatchlist) {
                await axios.delete(`${API_BASE_URL}/api/watchlist/${code}`);
                setWatchlistCodes((prev) => normalizeCodes(prev.filter((c) => c !== code)));
            } else {
                await axios.post(`${API_BASE_URL}/api/watchlist`, {
                    code,
                    name: getStockNameByCode(code),
                });
                setWatchlistCodes((prev) => normalizeCodes([...prev, code]));
            }
            setUiMessage('');
        } catch (err) {
            const serverMessage = err?.response?.data?.message;
            const action = inWatchlist ? '해제' : '추가';
            if (typeof serverMessage === 'string' && serverMessage.trim().length > 0) {
                setUiMessage(`관심종목 ${action} 실패: ${serverMessage}`);
            } else {
                setUiMessage(`관심종목 ${action} 실패: 백엔드 연결 상태를 확인해 주세요.`);
            }
        } finally {
            setWatchlistLoading(false);
        }
    };

    const submitOrder = async (side, overrides = {}) => {
        if (!isLoggedIn) {
            setTradeMessage('로그인 후 주문할 수 있습니다.');
            return;
        }
        const targetCode = overrides.code ?? tradeCode;
        const targetQtyInput = overrides.quantity ?? tradeQty;
        const targetOrderType = (overrides.orderType ?? 'MARKET').toUpperCase();
        const targetLimitPriceInput = overrides.limitPrice ?? null;

        if (!targetCode) {
            setTradeMessage('주문할 종목을 먼저 선택해 주세요.');
            return;
        }
        const qty = Number(targetQtyInput);
        if (!Number.isInteger(qty) || qty <= 0) {
            setTradeMessage('수량은 1 이상 정수여야 합니다.');
            return;
        }

        let limitPrice = null;
        if (targetOrderType === 'LIMIT') {
            limitPrice = Number(targetLimitPriceInput);
            if (!Number.isFinite(limitPrice) || limitPrice <= 0) {
                setTradeMessage('지정가 주문은 유효한 지정가를 입력해 주세요.');
                return;
            }
        }

        setSimLoading(true);
        try {
            const res = await axios.post(`${API_BASE_URL}/api/sim/order`, {
                code: targetCode,
                side,
                orderType: targetOrderType,
                limitPrice,
                quantity: qty,
            });
            const orderTypeLabel = res.data.orderType === 'LIMIT' ? '지정가' : '시장가';
            if (res.data.status === 'PENDING') {
                setTradeMessage(`${orderTypeLabel} ${side === 'BUY' ? '매수' : '매도'} 주문 접수: ${res.data.code} ${res.data.quantity}주 @ ${res.data.requestedLimitPrice}`);
            } else {
                setTradeMessage(`${orderTypeLabel} ${side === 'BUY' ? '매수' : '매도'} 체결: ${res.data.code} ${res.data.quantity}주 @ ${res.data.price}`);
            }
            setPortfolio((prev) => {
                if (!prev) return prev;
                return {
                    ...prev,
                    cash: typeof res.data.cashAfter === 'number' ? res.data.cashAfter : prev.cash,
                };
            });
            setSimLoading(false);
            loadReplayState();
            loadPendingOrders();
            loadExecutions();
            loadRankings();
        } catch (err) {
            const serverMessage = err?.response?.data?.message;
            if (typeof serverMessage === 'string' && serverMessage.trim().length > 0) {
                setTradeMessage(serverMessage);
            } else {
                setTradeMessage('주문 실패');
            }
            setSimLoading(false);
        }
    };

    const openOrderConfirm = (side, overrides = {}) => {
        const code = overrides.code ?? tradeCode;
        const quantity = Number(overrides.quantity ?? tradeQty);
        const orderType = (overrides.orderType ?? 'MARKET').toUpperCase();
        const limitPriceRaw = overrides.limitPrice ?? null;
        const limitPrice = orderType === 'LIMIT' ? Number(limitPriceRaw) : null;

        if (!code) {
            setTradeMessage('주문할 종목을 선택해 주세요.');
            return;
        }
        if (!Number.isInteger(quantity) || quantity <= 0) {
            setTradeMessage('수량은 1주 이상 정수여야 합니다.');
            return;
        }
        if (orderType === 'LIMIT' && (!Number.isFinite(limitPrice) || limitPrice <= 0)) {
            setTradeMessage('지정가 주문 가격을 확인해 주세요.');
            return;
        }

        setOrderConfirmDraft({
            side,
            code,
            quantity,
            orderType,
            limitPrice: orderType === 'LIMIT' ? limitPrice : null,
        });
    };

    const confirmOrderDraft = async () => {
        if (!orderConfirmDraft) return;
        const draft = orderConfirmDraft;
        setOrderConfirmDraft(null);
        await submitOrder(draft.side, {
            code: draft.code,
            quantity: draft.quantity,
            orderType: draft.orderType,
            limitPrice: draft.limitPrice,
        });
    };

    const resetSimulation = async () => {
        if (!isLoggedIn) {
            setTradeMessage('로그인 후 사용할 수 있습니다.');
            return;
        }
        setSimLoading(true);
        try {
            await axios.post(`${API_BASE_URL}/api/sim/reset`);
            setTradeMessage('모의투자 계좌를 초기화했습니다.');
            setPortfolio((prev) => {
                if (!prev) return prev;
                return {
                    ...prev,
                    cash: INITIAL_CASH,
                    marketValue: 0,
                    totalValue: INITIAL_CASH,
                    realizedPnl: 0,
                    unrealizedPnl: 0,
                    holdings: [],
                };
            });
            setSimLoading(false);
            loadReplayState();
            loadPendingOrders();
            loadExecutions();
            loadRankings();
        } catch (err) {
            const serverMessage = err?.response?.data?.message;
            if (typeof serverMessage === 'string' && serverMessage.trim().length > 0) {
                setTradeMessage(serverMessage);
            } else {
                setTradeMessage('초기화 실패');
            }
            setSimLoading(false);
        }
    };

    const startReplay = async () => {
        if (!isLoggedIn) {
            setTradeMessage('로그인 후 사용할 수 있습니다.');
            return;
        }
        setReplayLoading(true);
        try {
            await axios.post(`${API_BASE_URL}/api/sim/replay/start`, {});
            await loadReplayState();
            await loadPendingOrders();
            await loadExecutions();
            await loadRankings();
            setTradeMessage('리플레이를 시작했습니다. 1분마다 1영업일씩 진행됩니다.');
        } catch (err) {
            const serverMessage = err?.response?.data?.message;
            if (typeof serverMessage === 'string' && serverMessage.trim().length > 0) {
                setTradeMessage(serverMessage);
            } else {
                setTradeMessage('리플레이 시작 실패');
            }
        } finally {
            setReplayLoading(false);
        }
    };

    const pauseReplay = async () => {
        if (!isLoggedIn) {
            setTradeMessage('로그인 후 사용할 수 있습니다.');
            return;
        }
        setReplayLoading(true);
        try {
            await axios.post(`${API_BASE_URL}/api/sim/replay/pause`);
            await loadReplayState();
            await loadPendingOrders();
            await loadExecutions();
            await loadRankings();
            setTradeMessage('리플레이를 일시정지했습니다.');
        } catch (err) {
            const serverMessage = err?.response?.data?.message;
            if (typeof serverMessage === 'string' && serverMessage.trim().length > 0) {
                setTradeMessage(serverMessage);
            } else {
                setTradeMessage('리플레이 일시정지 실패');
            }
        } finally {
            setReplayLoading(false);
        }
    };

    const cancelPendingOrder = async (orderId) => {
        if (!isLoggedIn) {
            setTradeMessage('로그인 후 사용할 수 있습니다.');
            return;
        }
        setPendingLoading(true);
        try {
            await axios.delete(`${API_BASE_URL}/api/sim/orders/pending/${orderId}`);
            await loadPendingOrders();
            await loadExecutions();
            await loadRankings();
            setTradeMessage('미체결 주문을 취소했습니다.');
        } catch (err) {
            const serverMessage = err?.response?.data?.message;
            if (typeof serverMessage === 'string' && serverMessage.trim().length > 0) {
                setTradeMessage(`주문 취소 실패: ${serverMessage}`);
            } else {
                setTradeMessage('주문 취소 실패');
            }
        } finally {
            setPendingLoading(false);
        }
    };

    const fmt = (value) => {
        if (typeof value !== 'number') return '-';
        return value.toLocaleString('ko-KR');
    };

    const fmtSigned = (value) => {
        if (typeof value !== 'number' || !Number.isFinite(value)) return '-';
        return `${value > 0 ? '+' : ''}${value.toLocaleString('ko-KR')}`;
    };

    const fmtDateTime = (value) => {
        if (!value) return '-';
        try {
            return new Date(value).toLocaleString('ko-KR');
        } catch (e) {
            return String(value);
        }
    };

    const getHoldingOrderQty = (code) => {
        const value = Number(holdingOrderQtys[code]);
        return Number.isInteger(value) && value > 0 ? value : 1;
    };

    const getHoldingReturnRate = (holding) => {
        const qty = Number(holding?.quantity) || 0;
        const avgPrice = Number(holding?.avgPrice) || 0;
        const unrealizedPnl = Number(holding?.unrealizedPnl) || 0;
        const cost = qty * avgPrice;
        if (cost <= 0) return 0;
        return (unrealizedPnl / cost) * 100;
    };

    const setHoldingQuickQty = (code, holdQty, ratio) => {
        const qty = Math.max(1, Math.ceil(Math.max(1, Number(holdQty) || 1) * ratio));
        setHoldingOrderQtys((prev) => ({ ...prev, [code]: qty }));
        setTradeCode(code);
        setTradeQty(qty);
        return qty;
    };

    if (!isLoggedIn) {
        return (
            <div style={{ padding: '20px', fontFamily: 'sans-serif', backgroundColor: '#f9f9f9', minHeight: '100vh' }}>
                <header style={{ marginBottom: '20px' }}>
                    <h1 style={{ color: '#333', margin: 0 }}>Gen-Z 투자 플랫폼</h1>
                    <div style={{ marginTop: '6px', color: '#777', fontSize: '14px' }}>
                        {authPage === 'login' ? '로그인 페이지' : '회원가입 페이지'}
                    </div>
                </header>
                <div
                    style={{
                        maxWidth: '420px',
                        margin: '40px auto',
                        padding: '20px',
                        backgroundColor: '#fff',
                        borderRadius: '10px',
                        border: '1px solid #e5e5e5',
                        boxShadow: '0 3px 10px rgba(0,0,0,0.05)',
                    }}
                >
                    <div style={{ display: 'flex', gap: '8px', marginBottom: '14px' }}>
                        <button
                            type="button"
                            onClick={() => navigateTo('/login')}
                            style={{
                                flex: 1,
                                padding: '10px',
                                borderRadius: '6px',
                                border: authPage === 'login' ? 'none' : '1px solid #d0d0d0',
                                backgroundColor: authPage === 'login' ? '#1976d2' : '#fff',
                                color: authPage === 'login' ? '#fff' : '#444',
                                fontWeight: 700,
                                cursor: 'pointer',
                            }}
                        >
                            로그인
                        </button>
                        <button
                            type="button"
                            onClick={() => navigateTo('/signup')}
                            style={{
                                flex: 1,
                                padding: '10px',
                                borderRadius: '6px',
                                border: authPage === 'signup' ? 'none' : '1px solid #d0d0d0',
                                backgroundColor: authPage === 'signup' ? '#1976d2' : '#fff',
                                color: authPage === 'signup' ? '#fff' : '#444',
                                fontWeight: 700,
                                cursor: 'pointer',
                            }}
                        >
                            회원가입
                        </button>
                    </div>
                    {authPage === 'login' ? (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                            <input
                                type="text"
                                placeholder="이름"
                                value={loginName}
                                onChange={(e) => setLoginName(e.target.value)}
                                style={{ padding: '10px', borderRadius: '6px', border: '1px solid #ddd' }}
                            />
                            <input
                                type="password"
                                placeholder="비밀번호"
                                value={loginPassword}
                                onChange={(e) => setLoginPassword(e.target.value)}
                                onKeyDown={(e) => {
                                    if (e.key === 'Enter') handleLogin();
                                }}
                                style={{ padding: '10px', borderRadius: '6px', border: '1px solid #ddd' }}
                            />
                            <button
                                type="button"
                                onClick={handleLogin}
                                disabled={authLoading}
                                style={{
                                    padding: '10px 12px',
                                    borderRadius: '6px',
                                    border: 'none',
                                    backgroundColor: '#1976d2',
                                    color: '#fff',
                                    fontWeight: 700,
                                    cursor: authLoading ? 'not-allowed' : 'pointer',
                                }}
                            >
                                로그인
                            </button>
                            <button
                                type="button"
                                onClick={() => setSimOrderTab('rankings')}
                                style={{
                                    padding: '6px 10px',
                                    borderRadius: '999px',
                                    border: simOrderTab === 'rankings' ? 'none' : '1px solid #cfd8dc',
                                    backgroundColor: simOrderTab === 'rankings' ? '#3949ab' : '#fff',
                                    color: simOrderTab === 'rankings' ? '#fff' : '#455a64',
                                    fontWeight: 700,
                                    cursor: 'pointer',
                                }}
                            >
                                랭킹
                            </button>
                        </div>
                    ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                            <input
                                type="text"
                                placeholder="이름"
                                value={signupName}
                                onChange={(e) => setSignupName(e.target.value)}
                                style={{ padding: '10px', borderRadius: '6px', border: '1px solid #ddd' }}
                            />
                            <input
                                type="password"
                                placeholder="비밀번호"
                                value={signupPassword}
                                onChange={(e) => setSignupPassword(e.target.value)}
                                onKeyDown={(e) => {
                                    if (e.key === 'Enter') handleSignup();
                                }}
                                style={{ padding: '10px', borderRadius: '6px', border: '1px solid #ddd' }}
                            />
                            <button
                                type="button"
                                onClick={handleSignup}
                                disabled={authLoading}
                                style={{
                                    padding: '10px 12px',
                                    borderRadius: '6px',
                                    border: '1px solid #1976d2',
                                    backgroundColor: '#fff',
                                    color: '#1976d2',
                                    fontWeight: 700,
                                    cursor: authLoading ? 'not-allowed' : 'pointer',
                                }}
                            >
                                회원가입
                            </button>
                        </div>
                    )}
                    {authMessage && <div style={{ marginTop: '12px', color: '#c62828', fontWeight: 600 }}>{authMessage}</div>}
                </div>
            </div>
        );
    }

    return (
        <div style={{ padding: '20px', fontFamily: 'sans-serif', backgroundColor: '#f9f9f9', minHeight: '100vh' }}>
            <header style={{ marginBottom: '20px' }}>
                <h1 style={{ color: '#333', margin: 0 }}>Gen-Z 투자 플랫폼</h1>
                <div style={{ marginTop: '6px', color: '#777', fontSize: '14px' }}>
                    여러 종목 + 장기 기간 캔들 차트
                </div>
            </header>

            <div
                style={{
                    marginBottom: '20px',
                    padding: '15px',
                    backgroundColor: '#fff',
                    borderRadius: '8px',
                    boxShadow: '0 2px 4px rgba(0,0,0,0.05)',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '10px',
                }}
            >
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', alignItems: 'center' }}>
                    <div style={{ fontWeight: 700, color: '#333' }}>로그인 사용자: {currentUser?.name || loginName || '사용자'}</div>
                    <button
                        type="button"
                        onClick={handleLogout}
                        style={{
                            padding: '8px 12px',
                            borderRadius: '6px',
                            border: '1px solid #ccc',
                            backgroundColor: '#fff',
                            cursor: 'pointer',
                        }}
                    >
                        로그아웃
                    </button>
                </div>
            </div>

            <div
                style={{
                    marginBottom: '20px',
                    display: 'flex',
                    gap: '8px',
                }}
            >
                <button
                    type="button"
                    onClick={() => navigateTo('/charts')}
                    style={{
                        padding: '10px 14px',
                        borderRadius: '8px',
                        border: mainPage === 'charts' ? 'none' : '1px solid #cfd8dc',
                        backgroundColor: mainPage === 'charts' ? '#1e88e5' : '#fff',
                        color: mainPage === 'charts' ? '#fff' : '#455a64',
                        fontWeight: 700,
                        cursor: 'pointer',
                    }}
                >
                    차트 페이지
                </button>
                <button
                    type="button"
                    onClick={() => navigateTo('/sim')}
                    style={{
                        padding: '10px 14px',
                        borderRadius: '8px',
                        border: mainPage === 'sim' ? 'none' : '1px solid #cfd8dc',
                        backgroundColor: mainPage === 'sim' ? '#3949ab' : '#fff',
                        color: mainPage === 'sim' ? '#fff' : '#455a64',
                        fontWeight: 700,
                        cursor: 'pointer',
                    }}
                >
                    모의투자 페이지
                </button>
            </div>

            {mainPage === 'charts' && (
                <>
            <div
                style={{
                    marginBottom: '20px',
                    padding: '15px',
                    backgroundColor: '#fff',
                    borderRadius: '8px',
                    boxShadow: '0 2px 4px rgba(0,0,0,0.05)',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '10px',
                    alignItems: 'flex-start',
                }}
            >
                {watchlistLoading && <div style={{ color: '#1565c0', fontWeight: 600 }}>관심종목 불러오는 중...</div>}
                {uiMessage && <div style={{ color: '#c62828', fontWeight: 600 }}>{uiMessage}</div>}
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '10px', alignItems: 'center' }}>
                    <label style={{ fontWeight: 'bold' }}>종목 검색</label>
                    <input
                        type="text"
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        placeholder="종목명 또는 코드 입력"
                        style={{
                            padding: '10px',
                            borderRadius: '4px',
                            border: '1px solid #ddd',
                            minWidth: '220px',
                            fontSize: '14px',
                        }}
                    />
                    <label style={{ fontWeight: 'bold' }}>기간:</label>
                    <select
                        value={monthsInput}
                        onChange={(e) => setMonthsInput(Number(e.target.value))}
                        style={{ padding: '10px', borderRadius: '4px', border: '1px solid #ddd' }}
                    >
                        {PERIOD_OPTIONS.map((option) => (
                            <option key={option.value} value={option.value}>
                                {option.label}
                            </option>
                        ))}
                    </select>
                    <button
                        type="button"
                        onClick={applyFilter}
                        style={{
                            padding: '10px 14px',
                            borderRadius: '6px',
                            border: 'none',
                            backgroundColor: '#1e88e5',
                            color: '#fff',
                            fontWeight: 600,
                            cursor: 'pointer',
                        }}
                    >
                        적용
                    </button>
                </div>

                <div
                    style={{
                        width: '100%',
                        maxHeight: '220px',
                        overflowY: 'auto',
                        border: '1px solid #e6e6e6',
                        borderRadius: '8px',
                        backgroundColor: '#fcfcfc',
                    }}
                >
                    {filteredStocks.map((stock) => {
                        const inView = viewCodeSet.has(stock.code);
                        const inWatchlist = watchlistCodeSet.has(stock.code);
                        return (
                            <div
                                key={stock.code}
                                style={{
                                    display: 'flex',
                                    justifyContent: 'space-between',
                                    gap: '8px',
                                    padding: '8px 10px',
                                    borderBottom: '1px solid #f0f0f0',
                                    backgroundColor: inView ? '#e3f2fd' : '#fff',
                                }}
                            >
                                <button
                                    type="button"
                                    onClick={() => (inView ? removeFromView(stock.code) : addToView(stock.code))}
                                    style={{
                                        border: 'none',
                                        background: 'transparent',
                                        color: inView ? '#0d47a1' : '#333',
                                        textAlign: 'left',
                                        cursor: 'pointer',
                                        fontWeight: inView ? 700 : 500,
                                    }}
                                >
                                    {inView ? '제거 ' : '+ '}
                                    {stock.name} ({stock.code})
                                </button>
                                <button
                                    type="button"
                                    onClick={() => toggleWatchlist(stock.code)}
                                    disabled={watchlistLoading || !isLoggedIn}
                                    style={{
                                        border: '1px solid #d9d9d9',
                                        borderRadius: '6px',
                                        backgroundColor: inWatchlist ? '#fff8e1' : '#fff',
                                        color: inWatchlist ? '#c49000' : '#666',
                                        cursor: watchlistLoading || !isLoggedIn ? 'not-allowed' : 'pointer',
                                        padding: '2px 8px',
                                        fontWeight: 700,
                                    }}
                                >
                                    {inWatchlist ? '해제' : '추가'}
                                </button>
                            </div>
                        );
                    })}
                </div>
            </div>

            <div
                style={{
                    marginBottom: '20px',
                    padding: '15px',
                    backgroundColor: '#fff',
                    borderRadius: '8px',
                    boxShadow: '0 2px 4px rgba(0,0,0,0.05)',
                    display: 'flex',
                    flexWrap: 'wrap',
                    gap: '8px',
                    alignItems: 'center',
                }}
            >
                <strong style={{ color: '#333' }}>조회 종목:</strong>
                {viewCodes.length === 0 && <span style={{ color: '#666' }}>조회 종목이 없습니다.</span>}
                {viewCodes.map((code) => (
                    <button
                        key={code}
                        type="button"
                        onClick={() => removeFromView(code)}
                        style={{
                            padding: '8px 12px',
                            borderRadius: '999px',
                            border: '1px solid #1565c0',
                            backgroundColor: '#e3f2fd',
                            color: '#0d47a1',
                            fontWeight: 600,
                            cursor: 'pointer',
                        }}
                    >
                        {getStockNameByCode(code)} ({code}) x
                    </button>
                ))}
            </div>

            <div
                style={{
                    marginBottom: '20px',
                    padding: '15px',
                    backgroundColor: '#fff',
                    borderRadius: '8px',
                    boxShadow: '0 2px 4px rgba(0,0,0,0.05)',
                    display: 'flex',
                    flexWrap: 'wrap',
                    gap: '8px',
                    alignItems: 'center',
                }}
            >
                <strong style={{ color: '#333' }}>관심종목</strong>
                {!isLoggedIn && <span style={{ color: '#666' }}>로그인 후 사용자별 관심종목을 사용할 수 있습니다.</span>}
                {watchlistCodes.length === 0 && <span style={{ color: '#666' }}>등록된 관심종목이 없습니다.</span>}
                {watchlistCodes.map((code) => (
                    <span
                        key={code}
                        style={{
                            padding: '8px 12px',
                            borderRadius: '999px',
                            border: '1px solid #d9d9d9',
                            backgroundColor: '#fff8e1',
                            color: '#8a6d00',
                            fontWeight: 600,
                        }}
                    >
                        {getStockNameByCode(code)} ({code})
                    </span>
                ))}
            </div>

            <div
                style={{
                    display: 'grid',
                    gridTemplateColumns: chartGridColumns,
                    gap: '14px',
                    alignItems: 'start',
                }}
            >
                {chartCodes.map((code, idx) => (
                    <StockChartCard
                        key={`${code}-${applied.months}`}
                        code={code}
                        months={applied.months}
                        requestDelayMs={idx * 500}
                        endDate={chartEndDate}
                    />
                ))}
            </div>

            {chartCodes.length === 0 && (
                <div style={{ marginTop: '16px', color: '#666' }}>
                    조회 종목을 추가하면 차트가 표시됩니다.
                </div>
            )}
                </>
            )}

            
            
            {mainPage === 'sim' && (
                <div
                    style={{
                        marginTop: '20px',
                        marginBottom: '20px',
                        padding: '15px',
                        backgroundColor: '#fff',
                        borderRadius: '8px',
                        boxShadow: '0 2px 4px rgba(0,0,0,0.05)',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '10px',
                    }}
                >
                    <h3 style={{ margin: 0 }}>모의투자</h3>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', alignItems: 'center' }}>
                        <span style={{ color: '#607d8b', fontSize: '13px' }}>자동 재생 시작 (기준일 고정)</span>
                        <button
                            type="button"
                            onClick={startReplay}
                            disabled={replayLoading || simLoading || !isLoggedIn}
                            style={{ padding: '8px 12px', border: 'none', borderRadius: '6px', backgroundColor: '#3949ab', color: '#fff' }}
                        >
                            리플레이 시작
                        </button>
                        <button
                            type="button"
                            onClick={pauseReplay}
                            disabled={replayLoading || simLoading || !isLoggedIn}
                            style={{ padding: '8px 12px', border: '1px solid #ccc', borderRadius: '6px', backgroundColor: '#fff' }}
                        >
                            일시정지
                        </button>
                        <span style={{ color: '#555' }}>
                            진행상태: {replayState?.running ? '진행중' : '정지'} / 기준일: {portfolio?.valuationDate || replayState?.currentDate || '-'}
                        </span>
                        {replayState?.anchorDate && (
                            <span style={{ color: '#555' }}>
                                시작 고정일: {replayState.anchorDate}
                            </span>
                        )}
                    </div>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', alignItems: 'center' }}>
                        <label>종목:</label>
                        <select
                            value={tradeCode}
                            onChange={(e) => setTradeCode(e.target.value)}
                            disabled={simLoading || tradeableCodes.length === 0 || !isLoggedIn}
                            style={{ padding: '8px', borderRadius: '4px', border: '1px solid #ddd' }}
                        >
                            {tradeableCodes.map((code) => (
                                <option key={code} value={code}>
                                    {getStockNameByCode(code)} ({code})
                                </option>
                            ))}
                        </select>
                        <label>수량:</label>
                        <input
                            type="number"
                            min="1"
                            value={tradeQty}
                            onChange={(e) => setTradeQty(e.target.value)}
                            disabled={simLoading || !isLoggedIn}
                            style={{ width: '100px', padding: '8px', borderRadius: '4px', border: '1px solid #ddd' }}
                        />
                        <button
                            type="button"
                            onClick={() => openOrderConfirm('BUY')}
                            disabled={simLoading || !tradeCode || !isLoggedIn}
                            style={{ padding: '8px 12px', border: 'none', borderRadius: '6px', backgroundColor: '#e53935', color: '#fff' }}
                        >
                            매수
                        </button>
                        <button
                            type="button"
                            onClick={() => openOrderConfirm('SELL')}
                            disabled={simLoading || !tradeCode || !isLoggedIn}
                            style={{ padding: '8px 12px', border: 'none', borderRadius: '6px', backgroundColor: '#00897b', color: '#fff' }}
                        >
                            매도
                        </button>
                        <button
                            type="button"
                            onClick={resetSimulation}
                            disabled={simLoading || !isLoggedIn}
                            style={{ padding: '8px 12px', border: '1px solid #ccc', borderRadius: '6px', backgroundColor: '#fff' }}
                        >
                            초기화
                        </button>
                    </div>
                    {tradeMessage && <div style={{ color: '#555' }}>{tradeMessage}</div>}

                    {tradeCode && (
                        <div
                            style={{
                                border: '1px solid #e6edf5',
                                borderRadius: '10px',
                                padding: '10px',
                                background: '#fbfdff',
                                display: 'flex',
                                flexDirection: 'column',
                                gap: '8px',
                            }}
                        >
                            <div style={{ display: 'flex', justifyContent: 'space-between', gap: '10px', alignItems: 'center', flexWrap: 'wrap' }}>
                                <div style={{ fontWeight: 700, color: '#1f2937' }}>
                                    {getStockNameByCode(tradeCode)} ({tradeCode})
                                </div>
                                <div style={{ color: '#475569', fontSize: '13px' }}>
                                    현재가:{' '}
                                    {simSelectedPrice?.price
                                        ? `${fmt(Number(simSelectedPrice.price))}원`
                                        : selectedTradeHolding?.currentPrice
                                            ? `${fmt(Number(selectedTradeHolding.currentPrice))}원`
                                            : '-'}
                                </div>
                            </div>
                            <StockChartCard
                                code={tradeCode}
                                months={6}
                                requestDelayMs={0}
                                endDate={chartEndDate}
                                height={220}
                                onLatestPriceChange={setSimSelectedPrice}
                            />
                        </div>
                    )}

                    <div className="sim-order-panel">
                        <div style={{ display: 'flex', gap: '8px', marginBottom: '8px' }}>
                            <button
                                type="button"
                                onClick={() => setSimOrderTab('pending')}
                                style={{
                                    padding: '6px 10px',
                                    borderRadius: '999px',
                                    border: simOrderTab === 'pending' ? 'none' : '1px solid #cfd8dc',
                                    backgroundColor: simOrderTab === 'pending' ? '#3949ab' : '#fff',
                                    color: simOrderTab === 'pending' ? '#fff' : '#455a64',
                                    fontWeight: 700,
                                    cursor: 'pointer',
                                }}
                            >
                                미체결 주문
                            </button>
                            <button
                                type="button"
                                onClick={() => setSimOrderTab('executions')}
                                style={{
                                    padding: '6px 10px',
                                    borderRadius: '999px',
                                    border: simOrderTab === 'executions' ? 'none' : '1px solid #cfd8dc',
                                    backgroundColor: simOrderTab === 'executions' ? '#3949ab' : '#fff',
                                    color: simOrderTab === 'executions' ? '#fff' : '#455a64',
                                    fontWeight: 700,
                                    cursor: 'pointer',
                                }}
                            >
                                체결내역
                            </button>
                        </div>

                        {simOrderTab === 'pending' && (
                            <>
                                {pendingLoading && <div style={{ color: '#607d8b' }}>불러오는 중...</div>}
                                {!pendingLoading && pendingOrders.length === 0 && (
                                    <div style={{ color: '#78909c' }}>현재 미체결 주문이 없습니다.</div>
                                )}
                                {!pendingLoading && pendingOrders.length > 0 && (
                                    <div className="sim-order-table-wrap">
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
                                                        <td className={o.side === 'BUY' ? 'up' : 'down'}>{o.side === 'BUY' ? '매수' : '매도'}</td>
                                                        <td>{getStockNameByCode(o.code)} ({o.code})</td>
                                                        <td className="num">{fmt(o.quantity)}주</td>
                                                        <td className="num">{fmt(o.limitPrice)}원</td>
                                                        <td>{fmtDateTime(o.createdAt)}</td>
                                                        <td>
                                                            <button
                                                                type="button"
                                                                className="sim-order-mini-btn"
                                                                onClick={() => cancelPendingOrder(o.id)}
                                                                disabled={pendingLoading || simLoading || !isLoggedIn}
                                                            >
                                                                취소
                                                            </button>
                                                        </td>
                                                    </tr>
                                                ))}
                                            </tbody>
                                        </table>
                                    </div>
                                )}
                            </>
                        )}

                        {simOrderTab === 'executions' && (
                            <>
                                <div className="sim-order-toolbar">
                                    <select
                                        value={executionFilterSide}
                                        onChange={(e) => setExecutionFilterSide(e.target.value)}
                                        className="sim-order-filter"
                                        disabled={executionsLoading}
                                    >
                                        <option value="ALL">전체</option>
                                        <option value="BUY">매수</option>
                                        <option value="SELL">매도</option>
                                    </select>
                                    <select
                                        value={executionFilterCode}
                                        onChange={(e) => setExecutionFilterCode(e.target.value)}
                                        className="sim-order-filter"
                                        disabled={executionsLoading}
                                    >
                                        <option value="">전체 종목</option>
                                        {tradeableCodes.map((code) => (
                                            <option key={`exec-filter-${code}`} value={code}>
                                                {getStockNameByCode(code)} ({code})
                                            </option>
                                        ))}
                                    </select>
                                </div>
                                {executionsLoading && <div style={{ color: '#607d8b' }}>불러오는 중...</div>}
                                {!executionsLoading && filteredExecutions.length === 0 && (
                                    <div style={{ color: '#78909c' }}>체결내역이 없습니다.</div>
                                )}
                                {!executionsLoading && filteredExecutions.length > 0 && (
                                    <div className="sim-order-table-wrap">
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
                                                        <td className={o.side === 'BUY' ? 'up' : 'down'}>{o.side === 'BUY' ? '매수' : '매도'}</td>
                                                        <td>{getStockNameByCode(o.code)} ({o.code})</td>
                                                        <td className="num">{fmt(o.quantity)}주</td>
                                                        <td className="num">{fmt(o.price)}원</td>
                                                        <td className="num">{fmt(o.amount)}원</td>
                                                        <td>{o.valuationDate || '-'}</td>
                                                        <td>{fmtDateTime(o.executedAt)}</td>
                                                    </tr>
                                                ))}
                                            </tbody>
                                        </table>
                                    </div>
                                )}
                            </>
                        )}
                        {simOrderTab === 'rankings' && (
                            <>
                                <div style={{ color: '#64748b', fontSize: '12px', marginBottom: '6px' }}>
                                    랭킹은 사용자별 현재 리플레이 기준일 기준으로 계산됩니다.
                                </div>
                                {rankingsLoading && <div style={{ color: '#607d8b' }}>불러오는 중...</div>}
                                {!rankingsLoading && rankings.length === 0 && (
                                    <div style={{ color: '#78909c' }}>표시할 랭킹이 없습니다.</div>
                                )}
                                {!rankingsLoading && rankings.length > 0 && (
                                    <div className="sim-order-table-wrap">
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
                                                {rankings.map((r) => (
                                                    <tr key={`ranking-${r.userId}`}>
                                                        <td className="num">{fmt(Number(r.rank))}</td>
                                                        <td>{r.me ? `${r.userName} (나)` : r.userName}</td>
                                                        <td className={`num ${Number(r.returnRate) >= 0 ? 'up' : 'down'}`}>
                                                            {Number(r.returnRate) > 0 ? '+' : ''}{Number(r.returnRate).toFixed(2)}%
                                                        </td>
                                                        <td className="num">{fmt(Number(r.totalValue))}원</td>
                                                        <td className={`num ${Number(r.realizedPnl) >= 0 ? 'up' : 'down'}`}>{fmtSigned(Number(r.realizedPnl))}원</td>
                                                        <td className={`num ${Number(r.unrealizedPnl) >= 0 ? 'up' : 'down'}`}>{fmtSigned(Number(r.unrealizedPnl))}원</td>
                                                        <td>{r.valuationDate || '-'}</td>
                                                    </tr>
                                                ))}
                                            </tbody>
                                        </table>
                                    </div>
                                )}
                            </>
                        )}
                    </div>

                    {portfolio && (
                        <div style={{ fontSize: '14px', color: '#333' }}>
                            현금: {fmt(portfolio.cash)}원 | 평가금액: {fmt(portfolio.marketValue)}원 | 총자산: {fmt(portfolio.totalValue)}원 | 실현손익: {fmt(portfolio.realizedPnl)}원 | 미실현손익: {fmt(portfolio.unrealizedPnl)}원
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
                                                    <td className={`num ${isUp ? 'up' : 'down'}`}>{fmtSigned(unrealizedPnl)}원</td>
                                                    <td className={`num ${isUp ? 'up' : 'down'}`}>
                                                        {returnRate > 0 ? '+' : ''}{returnRate.toFixed(2)}%
                                                    </td>
                                                    <td>
                                                        <div className="sim-holding-actions">
                                                            <input
                                                                type="number"
                                                                min="1"
                                                                max={holdQty}
                                                                className="sim-holding-qty-input"
                                                                value={holdingOrderQtys[h.code] ?? 1}
                                                                disabled={simLoading || !isLoggedIn}
                                                                onChange={(e) => {
                                                                    const raw = e.target.value;
                                                                    setHoldingOrderQtys((prev) => ({ ...prev, [h.code]: raw }));
                                                                }}
                                                            />
                                                            <button type="button" className="sim-holding-quick-btn" disabled={simLoading || !isLoggedIn} onClick={() => setHoldingQuickQty(h.code, holdQty, 0.25)}>25%</button>
                                                            <button type="button" className="sim-holding-quick-btn" disabled={simLoading || !isLoggedIn} onClick={() => setHoldingQuickQty(h.code, holdQty, 0.5)}>50%</button>
                                                            <button
                                                                type="button"
                                                                className="sim-holding-buy-btn"
                                                                disabled={simLoading || !isLoggedIn}
                                                                onClick={() => {
                                                                    const qty = getHoldingOrderQty(h.code);
                                                                    setTradeCode(h.code);
                                                                    setTradeQty(qty);
                                                                    openOrderConfirm('BUY', { code: h.code, quantity: qty, orderType: 'MARKET', limitPrice: null });
                                                                }}
                                                            >
                                                                매수
                                                            </button>
                                                            <button
                                                                type="button"
                                                                className="sim-holding-sell-btn"
                                                                disabled={simLoading || !isLoggedIn}
                                                                onClick={() => {
                                                                    const qty = Math.min(getHoldingOrderQty(h.code), holdQty);
                                                                    setTradeCode(h.code);
                                                                    setTradeQty(qty);
                                                                    openOrderConfirm('SELL', { code: h.code, quantity: qty, orderType: 'MARKET', limitPrice: null });
                                                                }}
                                                            >
                                                                매도
                                                            </button>
                                                            <button
                                                                type="button"
                                                                className="sim-holding-all-sell-btn"
                                                                disabled={simLoading || !isLoggedIn}
                                                                onClick={() => {
                                                                    setHoldingOrderQtys((prev) => ({ ...prev, [h.code]: holdQty }));
                                                                    setTradeCode(h.code);
                                                                    setTradeQty(holdQty);
                                                                    openOrderConfirm('SELL', { code: h.code, quantity: holdQty, orderType: 'MARKET', limitPrice: null });
                                                                }}
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
                                    <div><strong>구분</strong> {orderConfirmDraft.side === 'BUY' ? '매수' : '매도'}</div>
                                    <div><strong>종목</strong> {getStockNameByCode(orderConfirmDraft.code)} ({orderConfirmDraft.code})</div>
                                    <div><strong>수량</strong> {fmt(orderConfirmDraft.quantity)}주</div>
                                    <div><strong>주문유형</strong> {orderConfirmDraft.orderType === 'LIMIT' ? '지정가' : '시장가'}</div>
                                    {orderConfirmDraft.orderType === 'LIMIT' && (
                                        <div><strong>주문가</strong> {fmt(orderConfirmDraft.limitPrice)}원</div>
                                    )}
                                    {orderConfirmDraft.orderType === 'MARKET' && (
                                        <div><strong>예상 기준가</strong> {fmt(Number(portfolio?.holdings?.find((h) => h.code === orderConfirmDraft.code)?.currentPrice) || 0)}원</div>
                                    )}
                                </div>
                                <div className="sim-confirm-actions">
                                    <button type="button" className="sim-order-mini-btn" onClick={() => setOrderConfirmDraft(null)}>취소</button>
                                    <button
                                        type="button"
                                        className={orderConfirmDraft.side === 'BUY' ? 'sim-holding-buy-btn' : 'sim-holding-sell-btn'}
                                        onClick={confirmOrderDraft}
                                        disabled={simLoading}
                                    >
                                        확인
                                    </button>
                                </div>
                            </div>
                        </div>
                    )}
                </div>
            )}


            <footer style={{ marginTop: '20px', fontSize: '13px', color: '#888', textAlign: 'center' }}>
                Backend: Spring Boot (8080) | Frontend: React (3000)
            </footer>
        </div>
    );
}

export default App;
