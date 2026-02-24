import React, { useMemo, useState } from "react";
import { STOCK_OPTIONS } from "../constants/stocks";

function buildMockNews(code, baseDate) {
  const date = baseDate || "2020-01-01";
  return [
    {
      id: `${code}-n1`,
      type: "news",
      date,
      source: "Mock News",
      title: `${code} 실적 기대감에 거래량 증가`,
      summary: "리그 기준일 관점에서 UI와 데이터 흐름을 확인하기 위한 모의 뉴스 데이터입니다.",
    },
    {
      id: `${code}-n2`,
      type: "news",
      date,
      source: "Mock News",
      title: `${code} 업종 강세 영향`,
      summary: "동일 업종 흐름의 영향을 받는다는 가정으로 생성한 테스트 뉴스입니다.",
    },
    {
      id: `${code}-d1`,
      type: "disclosure",
      date,
      source: "Mock DART",
      title: `${code} 주요 공시(모의)`,
      summary: "공시 페이지 UI/탭 동작을 확인하기 위한 목업 데이터입니다.",
    },
  ];
}

export default function NewsPage({ chartEndDate }) {
  const [code, setCode] = useState("005930");
  const [tab, setTab] = useState("all");

  const items = useMemo(() => buildMockNews(code, chartEndDate), [code, chartEndDate]);
  const filtered = items.filter((x) => (tab === "all" ? true : x.type === tab));

  return (
    <div className="app-card" style={{ padding: 16 }}>
      <h3 style={{ marginTop: 0, marginBottom: 12 }}>뉴스 · 공시 (Mock)</h3>

      <div className="app-toolbar-row" style={{ marginBottom: 12 }}>
        <label>종목</label>
        <select value={code} onChange={(e) => setCode(e.target.value)}>
          {STOCK_OPTIONS.map((s) => (
            <option key={s.code} value={s.code}>
              {s.name} ({s.code})
            </option>
          ))}
        </select>
        <label>기준일</label>
        <input value={chartEndDate || ""} readOnly placeholder="기준일" />
        <button type="button" className={tab === "all" ? "app-nav-btn active" : "app-nav-btn"} onClick={() => setTab("all")}>전체</button>
        <button type="button" className={tab === "news" ? "app-nav-btn active" : "app-nav-btn"} onClick={() => setTab("news")}>뉴스</button>
        <button type="button" className={tab === "disclosure" ? "app-nav-btn active" : "app-nav-btn"} onClick={() => setTab("disclosure")}>공시</button>
      </div>

      <div style={{ color: "#64748b", fontSize: 12, marginBottom: 12 }}>
        현재는 UI/리그 기준일 필터 동작을 위한 목업 데이터입니다. 이후 과거 뉴스/공시 적재 파이프라인을 연결하면 실제 데이터로 교체할 수 있습니다.
      </div>

      <div style={{ display: "grid", gap: 10 }}>
        {filtered.map((item) => (
          <div key={item.id} className="app-card" style={{ padding: 12, border: "1px solid #e5e7eb" }}>
            <div className="app-toolbar-row" style={{ justifyContent: "space-between", marginBottom: 6 }}>
              <span className={item.type === "disclosure" ? "news-type-chip disclosure" : "news-type-chip news"}>
                {item.type === "disclosure" ? "공시" : "뉴스"}
              </span>
              <span style={{ color: "#64748b", fontSize: 12 }}>{item.date} · {item.source}</span>
            </div>
            <div style={{ fontWeight: 800, marginBottom: 4 }}>{item.title}</div>
            <div style={{ color: "#475569", fontSize: 13 }}>{item.summary}</div>
          </div>
        ))}
      </div>
    </div>
  );
}
