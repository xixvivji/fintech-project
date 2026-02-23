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

function StockChartCard({ code, months, requestDelayMs, endDate }) {
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
            height: 360,
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
                })
                .catch((err) => {
                    if (requestSeq !== requestSeqRef.current) return;
                    if (err?.code === 'ERR_CANCELED') return;
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
    }, [code, months, requestDelayMs, endDate]);

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
            <div ref={chartContainerRef} style={{ minHeight: '360px' }} />
        </div>
    );
}

function App() {
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
    const [replayStartDate, setReplayStartDate] = useState('');
    const [tradeCode, setTradeCode] = useState('');
    const [tradeQty, setTradeQty] = useState(1);
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

    useEffect(() => {
        const handleResize = () => setIsNarrowScreen(window.innerWidth < 1024);
        window.addEventListener('resize', handleResize);
        return () => window.removeEventListener('resize', handleResize);
    }, []);

    const loadWatchlist = useCallback(async () => {
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
    }, [viewCodes.length]);

    useEffect(() => {
        loadWatchlist();
    }, [loadWatchlist]);

    const loadPortfolio = useCallback(async () => {
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
    }, []);

    useEffect(() => {
        loadPortfolio();
    }, [loadPortfolio]);

    useEffect(() => {
        if (!replayStartDate) {
            const d = new Date();
            d.setMonth(d.getMonth() - 1);
            setReplayStartDate(d.toISOString().slice(0, 10));
        }
    }, [replayStartDate]);

    const loadReplayState = useCallback(async () => {
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
    }, []);

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
        if (chartCodes.length === 0) {
            setTradeCode('');
            return;
        }
        if (!tradeCode || !chartCodes.includes(tradeCode)) {
            setTradeCode(chartCodes[0]);
        }
    }, [chartCodes, tradeCode]);

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
        const targetCode = overrides.code ?? tradeCode;
        const targetQtyInput = overrides.quantity ?? tradeQty;

        if (!targetCode) {
            setTradeMessage('주문할 종목을 먼저 선택해 주세요.');
            return;
        }
        const qty = Number(targetQtyInput);
        if (!Number.isInteger(qty) || qty <= 0) {
            setTradeMessage('수량은 1 이상 정수여야 합니다.');
            return;
        }

        setSimLoading(true);
        try {
            const res = await axios.post(`${API_BASE_URL}/api/sim/order`, {
                code: targetCode,
                side,
                quantity: qty,
            });
            setTradeMessage(`${side === 'BUY' ? '매수' : '매도'} 체결: ${res.data.code} ${res.data.quantity}주 @ ${res.data.price}`);
            setPortfolio((prev) => {
                if (!prev) return prev;
                return {
                    ...prev,
                    cash: res.data.cashAfter,
                };
            });
            setSimLoading(false);
            loadReplayState();
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

    const resetSimulation = async () => {
        setSimLoading(true);
        try {
            await axios.post(`${API_BASE_URL}/api/sim/reset`);
            setTradeMessage('모의투자 계좌를 초기화했습니다.');
            setPortfolio((prev) => {
                if (!prev) return prev;
                return {
                    ...prev,
                    cash: 10000000,
                    marketValue: 0,
                    totalValue: 10000000,
                    realizedPnl: 0,
                    unrealizedPnl: 0,
                    holdings: [],
                };
            });
            setSimLoading(false);
            loadReplayState();
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
        setReplayLoading(true);
        try {
            await axios.post(`${API_BASE_URL}/api/sim/replay/start`, {
                startDate: replayStartDate,
            });
            await loadReplayState();
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
        setReplayLoading(true);
        try {
            await axios.post(`${API_BASE_URL}/api/sim/replay/pause`);
            await loadReplayState();
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

    const fmt = (value) => {
        if (typeof value !== 'number') return '-';
        return value.toLocaleString('ko-KR');
    };

    const getHoldingOrderQty = (code) => {
        const value = Number(holdingOrderQtys[code]);
        return Number.isInteger(value) && value > 0 ? value : 1;
    };

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
                                    disabled={watchlistLoading}
                                    style={{
                                        border: '1px solid #d9d9d9',
                                        borderRadius: '6px',
                                        backgroundColor: inWatchlist ? '#fff8e1' : '#fff',
                                        color: inWatchlist ? '#c49000' : '#666',
                                        cursor: watchlistLoading ? 'not-allowed' : 'pointer',
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
                    <label>리플레이 시작일</label>
                    <input
                        type="date"
                        value={replayStartDate}
                        onChange={(e) => setReplayStartDate(e.target.value)}
                        disabled={replayLoading}
                        style={{ padding: '8px', borderRadius: '4px', border: '1px solid #ddd' }}
                    />
                    <button
                        type="button"
                        onClick={startReplay}
                        disabled={replayLoading || simLoading}
                        style={{ padding: '8px 12px', border: 'none', borderRadius: '6px', backgroundColor: '#3949ab', color: '#fff' }}
                    >
                        리플레이 시작
                    </button>
                    <button
                        type="button"
                        onClick={pauseReplay}
                        disabled={replayLoading || simLoading}
                        style={{ padding: '8px 12px', border: '1px solid #ccc', borderRadius: '6px', backgroundColor: '#fff' }}
                    >
                        일시정지
                    </button>
                    <span style={{ color: '#555' }}>
                        상태: {replayState?.running ? '진행중' : '정지'} / 기준일: {portfolio?.valuationDate || replayState?.currentDate || '-'}
                    </span>
                </div>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', alignItems: 'center' }}>
                    <label>종목:</label>
                    <select
                        value={tradeCode}
                        onChange={(e) => setTradeCode(e.target.value)}
                        disabled={simLoading || chartCodes.length === 0}
                        style={{ padding: '8px', borderRadius: '4px', border: '1px solid #ddd' }}
                    >
                        {chartCodes.map((code) => (
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
                        disabled={simLoading}
                        style={{ width: '100px', padding: '8px', borderRadius: '4px', border: '1px solid #ddd' }}
                    />
                    <button
                        type="button"
                        onClick={() => submitOrder('BUY')}
                        disabled={simLoading || !tradeCode}
                        style={{ padding: '8px 12px', border: 'none', borderRadius: '6px', backgroundColor: '#e53935', color: '#fff' }}
                    >
                        매수
                    </button>
                    <button
                        type="button"
                        onClick={() => submitOrder('SELL')}
                        disabled={simLoading || !tradeCode}
                        style={{ padding: '8px 12px', border: 'none', borderRadius: '6px', backgroundColor: '#00897b', color: '#fff' }}
                    >
                        매도
                    </button>
                    <button
                        type="button"
                        onClick={resetSimulation}
                        disabled={simLoading}
                        style={{ padding: '8px 12px', border: '1px solid #ccc', borderRadius: '6px', backgroundColor: '#fff' }}
                    >
                        초기화
                    </button>
                </div>
                {tradeMessage && <div style={{ color: '#555' }}>{tradeMessage}</div>}
                {portfolio && (
                    <div style={{ fontSize: '14px', color: '#333' }}>
                        현금: {fmt(portfolio.cash)}원 | 평가금액: {fmt(portfolio.marketValue)}원 | 총자산: {fmt(portfolio.totalValue)}원 | 실현손익: {fmt(portfolio.realizedPnl)}원 | 미실현손익: {fmt(portfolio.unrealizedPnl)}원
                    </div>
                )}
                {portfolio?.holdings?.length > 0 && (
                    <div className="sim-holdings-list">
                        <div className="sim-holdings-title">보유 종목</div>
                        {portfolio.holdings.map((h) => (
                            <div key={`${h.code}-${h.quantity}-${h.avgPrice}`} className="sim-holding-row">
                                <div className="sim-holding-main">
                                    <div className="sim-holding-code">
                                        {getStockNameByCode(h.code)} ({h.code})
                                    </div>
                                    <div className="sim-holding-meta">
                                        {h.quantity}주 보유 · 평단 {fmt(h.avgPrice)}원
                                    </div>
                                </div>
                                <div className="sim-holding-actions">
                                    <input
                                        type="number"
                                        min="1"
                                        max={Math.max(1, Number(h.quantity) || 1)}
                                        className="sim-holding-qty-input"
                                        value={holdingOrderQtys[h.code] ?? 1}
                                        disabled={simLoading}
                                        onChange={(e) => {
                                            const raw = e.target.value;
                                            setHoldingOrderQtys((prev) => ({
                                                ...prev,
                                                [h.code]: raw,
                                            }));
                                        }}
                                    />
                                    <button
                                        type="button"
                                        className="sim-holding-buy-btn"
                                        disabled={simLoading}
                                        onClick={() => {
                                            const qty = getHoldingOrderQty(h.code);
                                            setTradeCode(h.code);
                                            setTradeQty(qty);
                                            submitOrder('BUY', { code: h.code, quantity: qty });
                                        }}
                                    >
                                        매수
                                    </button>
                                    <button
                                        type="button"
                                        className="sim-holding-sell-btn"
                                        disabled={simLoading}
                                        onClick={() => {
                                            const qty = Math.min(getHoldingOrderQty(h.code), Math.max(1, Number(h.quantity) || 1));
                                            setTradeCode(h.code);
                                            setTradeQty(qty);
                                            submitOrder('SELL', { code: h.code, quantity: qty });
                                        }}
                                    >
                                        매도
                                    </button>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            <footer style={{ marginTop: '20px', fontSize: '13px', color: '#888', textAlign: 'center' }}>
                Backend: Spring Boot (8080) | Frontend: React (3000)
            </footer>
        </div>
    );
}

export default App;


