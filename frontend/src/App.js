import React, { useMemo, useEffect, useRef, useState } from 'react';
import { createChart } from 'lightweight-charts';
import axios from 'axios';

const PERIOD_OPTIONS = [
    { label: '6개월', value: 6 },
    { label: '1년', value: 12 },
    { label: '2년', value: 24 },
    { label: '3년', value: 36 },
    { label: '5년', value: 60 },
];

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

const QUICK_PICK_CODES = ['005930', '000660', '035420', '035720', '373220', '005380'];

function getStockNameByCode(code) {
    const stock = STOCK_OPTIONS.find((item) => item.code === code);
    return stock ? stock.name : code;
}

function StockChartCard({ code, months, requestDelayMs }) {
    const chartContainerRef = useRef(null);
    const chartRef = useRef(null);
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

        const timer = setTimeout(() => {
            axios
                .get(`http://localhost:8080/api/stock/chart/${code}`, { params: { months } })
                .then((res) => {
                    if (!Array.isArray(res.data) || res.data.length === 0) {
                        setError('데이터가 없습니다. 종목 코드를 확인해 주세요.');
                        return;
                    }
                    candlestickSeries.setData(res.data);
                    chart.timeScale().fitContent();
                })
                .catch((err) => {
                    const serverMessage = err?.response?.data?.message;
                    if (typeof serverMessage === 'string' && serverMessage.trim().length > 0) {
                        setError(serverMessage);
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
            window.removeEventListener('resize', handleResize);
            if (chartRef.current) {
                chartRef.current.remove();
                chartRef.current = null;
            }
        };
    }, [code, months, requestDelayMs]);

    return (
        <div
            style={{
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
            {loading && <div style={{ marginBottom: '8px', color: '#ef5350' }}>데이터 동기화 중...</div>}
            {error && <div style={{ marginBottom: '8px', color: '#d32f2f' }}>{error}</div>}
            <div ref={chartContainerRef} style={{ minHeight: '360px' }} />
        </div>
    );
}

function App() {
    const [selectedCodes, setSelectedCodes] = useState(['005930', '000660', '035420']);
    const [monthsInput, setMonthsInput] = useState(24);
    const [searchQuery, setSearchQuery] = useState('');
    const [applied, setApplied] = useState({
        codes: ['005930', '000660', '035420'],
        months: 24,
    });

    const selectedCodeSet = useMemo(() => new Set(selectedCodes), [selectedCodes]);
    const filteredStocks = useMemo(() => {
        const query = searchQuery.trim().toLowerCase();
        if (query.length === 0) return STOCK_OPTIONS;
        return STOCK_OPTIONS.filter((stock) => stock.name.toLowerCase().includes(query) || stock.code.includes(query));
    }, [searchQuery]);
    const quickPickStocks = useMemo(
        () => QUICK_PICK_CODES.map((code) => STOCK_OPTIONS.find((item) => item.code === code)).filter(Boolean),
        []
    );

    const applyFilter = () => {
        const safeMonths = Math.max(1, Math.min(Number(monthsInput) || 6, 120));
        setApplied({
            codes: selectedCodes.length > 0 ? selectedCodes : ['005930'],
            months: safeMonths,
        });
    };

    const addStock = (code) => {
        const nextCodes = selectedCodeSet.has(code) ? selectedCodes : [...selectedCodes, code];
        const normalized = nextCodes.length > 0 ? nextCodes : ['005930'];
        const safeMonths = Math.max(1, Math.min(Number(monthsInput) || 6, 120));
        setSelectedCodes(normalized);
        setApplied({
            codes: normalized,
            months: safeMonths,
        });
        setSearchQuery('');
    };

    const removeStock = (code) => {
        const nextCodes = selectedCodes.filter((c) => c !== code);
        const normalized = nextCodes.length > 0 ? nextCodes : ['005930'];
        const safeMonths = Math.max(1, Math.min(Number(monthsInput) || 6, 120));
        setSelectedCodes(normalized);
        setApplied({
            codes: normalized,
            months: safeMonths,
        });
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
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '10px', alignItems: 'center' }}>
                    <label style={{ fontWeight: 'bold' }}>종목 검색:</label>
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
                        const selected = selectedCodeSet.has(stock.code);
                        return (
                            <button
                                key={stock.code}
                                type="button"
                                onClick={() => (selected ? removeStock(stock.code) : addStock(stock.code))}
                                style={{
                                    width: '100%',
                                    textAlign: 'left',
                                    padding: '9px 12px',
                                    border: 'none',
                                    borderBottom: '1px solid #f0f0f0',
                                    backgroundColor: selected ? '#e3f2fd' : '#fff',
                                    color: selected ? '#0d47a1' : '#333',
                                    cursor: 'pointer',
                                    fontWeight: selected ? 700 : 500,
                                }}
                            >
                                {selected ? '✓ ' : '+ '}
                                {stock.name} ({stock.code})
                            </button>
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
                <strong style={{ color: '#333' }}>빠른 선택:</strong>
                {quickPickStocks.map((stock) => (
                    <button
                        key={stock.code}
                        type="button"
                        onClick={() => addStock(stock.code)}
                        style={{
                            padding: '8px 12px',
                            borderRadius: '999px',
                            border: selectedCodeSet.has(stock.code) ? '1px solid #1565c0' : '1px solid #d9d9d9',
                            backgroundColor: selectedCodeSet.has(stock.code) ? '#e3f2fd' : '#fff',
                            color: selectedCodeSet.has(stock.code) ? '#0d47a1' : '#444',
                            fontWeight: 600,
                            cursor: 'pointer',
                        }}
                    >
                        {stock.name}
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
                <strong style={{ color: '#333' }}>선택 종목:</strong>
                {selectedCodes.map((code) => (
                    <button
                        key={code}
                        type="button"
                        onClick={() => removeStock(code)}
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
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fit, minmax(380px, 1fr))',
                    gap: '14px',
                }}
            >
                {applied.codes.map((code, idx) => (
                    <StockChartCard
                        key={`${code}-${applied.months}`}
                        code={code}
                        months={applied.months}
                        requestDelayMs={idx * 500}
                    />
                ))}
            </div>

            <footer style={{ marginTop: '20px', fontSize: '13px', color: '#888', textAlign: 'center' }}>
                Backend: Spring Boot (8080) | Frontend: React (3000)
            </footer>
        </div>
    );
}

export default App;
