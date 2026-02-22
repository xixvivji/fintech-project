import React, { useEffect, useMemo, useRef, useState } from 'react';
import { createChart } from 'lightweight-charts';
import axios from 'axios';

const PERIOD_OPTIONS = [
    { label: '6개월', value: 6 },
    { label: '1년', value: 12 },
    { label: '2년', value: 24 },
    { label: '3년', value: 36 },
    { label: '5년', value: 60 },
];

function parseStockCodes(input) {
    return Array.from(new Set(
        input
            .split(',')
            .map((code) => code.trim())
            .filter((code) => code.length > 0)
    ));
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
    const [stockInput, setStockInput] = useState('005930, 000660, 035420');
    const [monthsInput, setMonthsInput] = useState(24);
    const [applied, setApplied] = useState({
        codes: ['005930', '000660', '035420'],
        months: 24,
    });

    const parsedCodes = useMemo(() => parseStockCodes(stockInput), [stockInput]);

    const applyFilter = () => {
        const safeMonths = Math.max(1, Math.min(Number(monthsInput) || 6, 120));
        setApplied({
            codes: parsedCodes.length > 0 ? parsedCodes : ['005930'],
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
                    flexWrap: 'wrap',
                    gap: '10px',
                    alignItems: 'center',
                }}
            >
                <label style={{ fontWeight: 'bold' }}>종목 코드(쉼표 구분):</label>
                <input
                    type="text"
                    value={stockInput}
                    onChange={(e) => setStockInput(e.target.value)}
                    placeholder="예: 005930,000660,035420"
                    style={{
                        padding: '10px',
                        borderRadius: '4px',
                        border: '1px solid #ddd',
                        minWidth: '320px',
                        fontSize: '15px',
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
