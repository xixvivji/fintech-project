import React, { useEffect, useRef, useState } from 'react';
import { createChart } from 'lightweight-charts';
import axios from 'axios';

function App() {
    const chartContainerRef = useRef(null); // 초기값을 null로 설정
    const chartRef = useRef(null); // 생성된 차트 객체를 저장할 ref 추가
    const [stockCode, setStockCode] = useState('005930');
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        // 1. 차트 컨테이너가 없으면 실행 안 함
        if (!chartContainerRef.current) return;

        // 2. 초기화: 기존 차트 및 HTML 내용 완전히 제거
        chartContainerRef.current.innerHTML = '';

        // 3. 차트 객체 생성
        const chart = createChart(chartContainerRef.current, {
            width: chartContainerRef.current.clientWidth,
            height: 500,
            layout: {
                background: { color: '#ffffff' },
                textColor: '#333',
            },
            grid: {
                vertLines: { color: '#f0f0f0' },
                horzLines: { color: '#f0f0f0' },
            },
        });

        // 4. 시리즈 추가 (이 부분이 에러가 났던 지점)
        // chart 객체가 존재하는지 확인 후 안전하게 호출
        const candlestickSeries = chart.addCandlestickSeries({
            upColor: '#ef5350',
            downColor: '#26a69a',
            wickUpColor: '#ef5350',
            wickDownColor: '#26a69a',
        });

        // 전역 ref에 저장 (리사이징 등에서 쓰기 위함)
        chartRef.current = chart;

        // 5. 백엔드 데이터 호출
        setLoading(true);
        axios.get(`http://localhost:8080/api/stock/chart/${stockCode}`)
            .then(res => {
                if (res.data && Array.isArray(res.data) && res.data.length > 0) {
                    // 데이터가 'time' 기준으로 오름차순인지 백엔드 확인 필요 (차트는 과거->미래 순이어야 함)
                    candlestickSeries.setData(res.data);
                    chart.timeScale().fitContent();
                }
                setLoading(false);
            })
            .catch(err => {
                console.error("백엔드 연결 실패:", err);
                setLoading(false);
            });

        // 6. 리사이징 처리
        const handleResize = () => {
            if (chartRef.current && chartContainerRef.current) {
                chartRef.current.applyOptions({
                    width: chartContainerRef.current.clientWidth
                });
            }
        };
        window.addEventListener('resize', handleResize);

        // 7. 클린업 (중요!)
        return () => {
            window.removeEventListener('resize', handleResize);
            if (chartRef.current) {
                chartRef.current.remove();
                chartRef.current = null;
            }
        };
    }, [stockCode]); // stockCode가 바뀔 때마다 차트 재구성

    return (
        <div style={{ padding: '20px', fontFamily: 'sans-serif', backgroundColor: '#f9f9f9', minHeight: '100vh' }}>
            <header style={{ marginBottom: '20px' }}>
                <h1 style={{ color: '#333', margin: '0' }}>📈 Gen-Z 투자 플랫폼 <small style={{fontSize: '0.5em', color: '#999'}}>Local Test</small></h1>
            </header>

            <div style={{
                marginBottom: '20px',
                padding: '15px',
                backgroundColor: '#fff',
                borderRadius: '8px',
                boxShadow: '0 2px 4px rgba(0,0,0,0.05)'
            }}>
                <label style={{ marginRight: '10px', fontWeight: 'bold' }}>종목 코드:</label>
                <input
                    type="text"
                    value={stockCode}
                    onChange={(e) => setStockCode(e.target.value)}
                    placeholder="예: 005930"
                    style={{
                        padding: '10px',
                        borderRadius: '4px',
                        border: '1px solid #ddd',
                        width: '150px',
                        fontSize: '16px'
                    }}
                />
                {loading && <span style={{ marginLeft: '15px', color: '#ef5350', fontWeight: 'bold' }}>데이터 동기화 중...</span>}
            </div>

            <div
                ref={chartContainerRef}
                style={{
                    border: '1px solid #ddd',
                    borderRadius: '8px',
                    overflow: 'hidden',
                    backgroundColor: '#fff',
                    boxShadow: '0 4px 12px rgba(0,0,0,0.1)'
                }}
            />

            <footer style={{ marginTop: '20px', fontSize: '13px', color: '#888', textAlign: 'center' }}>
                본 환경은 <strong>SSAFY 프로젝트</strong> 개발용 로컬 서버입니다. <br/>
                Backend: Spring Boot (8080) | Frontend: React (3000)
            </footer>
        </div>
    );
}

export default App;