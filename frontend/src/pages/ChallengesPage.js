import React, { useEffect, useMemo, useState } from "react";
import axios from "axios";

function authHeaders(authToken) {
  return authToken ? { Authorization: `Bearer ${authToken}` } : {};
}

const DEFAULT_FORM = () => {
  const today = new Date();
  return {
    title: "",
    description: "",
    goalType: "RETURN_RATE",
    targetValue: "5",
    visibility: "PUBLIC",
    maxParticipants: "100",
    startDate: toLocalDate(today),
    endDate: toLocalDate(addDate(today, 30)),
    habitCode: "005930",
    habitDailyBuyQuantity: "1",
    habitRequiredDays: "20",
  };
};

export default function ChallengesPage({
  apiBaseUrl,
  authToken,
  isLoggedIn,
  currentUser,
  leagueState,
  navigateTo,
}) {
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const [rows, setRows] = useState([]);
  const [statusTab, setStatusTab] = useState("ALL");
  const [query, setQuery] = useState("");
  const [creating, setCreating] = useState(false);
  const [selectedTemplate, setSelectedTemplate] = useState("");
  const [createForm, setCreateForm] = useState(DEFAULT_FORM);

  useEffect(() => {
    const base = String(leagueState?.currentDate || "").trim();
    if (!base) return;
    setCreateForm((prev) => {
      if ((prev.title || "").trim() || (prev.description || "").trim()) return prev;
      return { ...prev, startDate: base, endDate: addDaysString(base, 30) };
    });
  }, [leagueState]);

  const load = async () => {
    if (!isLoggedIn || !authToken) return;
    setLoading(true);
    setMessage("");
    try {
      const res = await axios.get(`${apiBaseUrl}/api/challenges`, {
        headers: authHeaders(authToken),
      });
      setRows(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      setMessage(err?.response?.data?.message || err?.message || "챌린지 목록 조회에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoggedIn, authToken, apiBaseUrl]);

  const counts = useMemo(() => {
    const base = { ALL: rows.length, ONGOING: 0, UPCOMING: 0, ENDED: 0 };
    for (const r of rows) {
      const key = String(r?.status || "").toUpperCase();
      if (base[key] != null) base[key] += 1;
    }
    return base;
  }, [rows]);

  const filteredRows = useMemo(() => {
    const q = query.trim().toLowerCase();
    return rows.filter((r) => {
      if (statusTab !== "ALL" && String(r?.status || "").toUpperCase() !== statusTab) return false;
      if (!q) return true;
      return (
        String(r?.title || "").toLowerCase().includes(q) ||
        String(r?.description || "").toLowerCase().includes(q) ||
        String(r?.ownerUserName || "").toLowerCase().includes(q)
      );
    });
  }, [rows, statusTab, query]);

  const onCreate = async () => {
    setMessage("");
    try {
      const payload = {
        title: createForm.title,
        description: createForm.description,
        goalType: createForm.goalType,
        targetValue: Number(createForm.targetValue || 0),
        visibility: createForm.visibility,
        maxParticipants: Number(createForm.maxParticipants || 0),
        startDate: createForm.startDate,
        endDate: createForm.endDate,
        habitCode: createForm.goalType === "DAILY_BUY_QUANTITY" ? String(createForm.habitCode || "").trim() : null,
        habitDailyBuyQuantity:
          createForm.goalType === "DAILY_BUY_QUANTITY" ? Number(createForm.habitDailyBuyQuantity || 0) : null,
        habitRequiredDays:
          createForm.goalType === "DAILY_BUY_QUANTITY" ? Number(createForm.habitRequiredDays || 0) : null,
      };

      const res = await axios.post(`${apiBaseUrl}/api/challenges`, payload, {
        headers: authHeaders(authToken),
      });
      setCreating(false);
      setMessage("챌린지를 생성했습니다.");
      await load();
      if (res?.data?.id) navigateTo?.(`/challenges/${res.data.id}`);
    } catch (err) {
      setMessage(err?.response?.data?.message || err?.message || "챌린지 생성에 실패했습니다.");
    }
  };

  const applyTemplate = (key) => {
    setSelectedTemplate(key);
    const base = String(leagueState?.currentDate || createForm.startDate || "").trim();
    if (key === "BEGINNER") {
      setCreateForm((p) => ({
        ...p,
        title: "초보형 30일 +3% 챌린지",
        description: "무리한 단타보다 안정적인 수익률 달성을 목표로 하는 입문형 챌린지입니다.",
        goalType: "RETURN_RATE",
        targetValue: "3",
        visibility: "PUBLIC",
        maxParticipants: "200",
        startDate: base || p.startDate,
        endDate: base ? addDaysString(base, 30) : p.endDate,
      }));
      return;
    }

    if (key === "HABIT") {
      setCreateForm((p) => ({
        ...p,
        title: "습관형 20일 1주 매수 챌린지",
        description: "매일 같은 종목을 1주씩 매수하며 투자 습관을 만드는 챌린지입니다.",
        goalType: "DAILY_BUY_QUANTITY",
        targetValue: "0",
        habitCode: "005930",
        habitDailyBuyQuantity: "1",
        habitRequiredDays: "20",
        visibility: "PUBLIC",
        maxParticipants: "100",
        startDate: base || p.startDate,
        endDate: base ? addDaysString(base, 30) : p.endDate,
      }));
      return;
    }

    if (key === "AGGRESSIVE") {
      setCreateForm((p) => ({
        ...p,
        title: "공격형 21일 +8% 챌린지",
        description: "높은 목표 수익률에 도전하는 단기 집중형 챌린지입니다. 리스크 관리 규칙을 함께 공유하세요.",
        goalType: "RETURN_RATE",
        targetValue: "8",
        visibility: "PUBLIC",
        maxParticipants: "50",
        startDate: base || p.startDate,
        endDate: base ? addDaysString(base, 21) : p.endDate,
      }));
    }
  };

  if (!isLoggedIn) {
    return (
      <div className="app-card">
        <h3>챌린지</h3>
        <p>로그인하면 목표 달성형 투자 챌린지를 만들고 참여할 수 있습니다.</p>
      </div>
    );
  }

  return (
    <div style={{ display: "grid", gap: 16 }}>
      <div className="app-card" style={{ background: "linear-gradient(180deg, #f8fbff 0%, #ffffff 100%)" }}>
        <div
          className="app-toolbar-row"
          style={{ justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap" }}
        >
          <div>
            <h3 style={{ margin: 0 }}>목표 달성 챌린지</h3>
            <div style={{ fontSize: 12, color: "#64748b", marginTop: 4 }}>
              {currentUser?.name || "사용자"}님의 투자 습관/수익률 챌린지를 만들고 참여해보세요.
            </div>
            <div style={{ fontSize: 12, color: "#64748b", marginTop: 2 }}>
              상태 기준일: <strong>{leagueState?.currentDate || "리그 기준일 로딩 중"}</strong> (리그 날짜 기준으로
              예정/진행중/종료를 계산합니다)
            </div>
          </div>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <button type="button" onClick={() => setCreating((v) => !v)}>
              {creating ? "생성 폼 닫기" : "챌린지 만들기"}
            </button>
            <button type="button" className="home-link-btn" onClick={() => navigateTo?.("/challenges/history")}>
              종료 챌린지 히스토리
            </button>
            <button type="button" onClick={load} disabled={loading}>
              새로고침
            </button>
          </div>
        </div>

        {message && (
          <div className="sim-trade-message" style={{ marginTop: 8 }}>
            {message}
          </div>
        )}

        {creating && (
          <div style={{ display: "grid", gap: 10, marginTop: 14, paddingTop: 14, borderTop: "1px solid #e2e8f0" }}>
            <div style={{ display: "grid", gap: 6 }}>
              <div style={{ fontSize: 12, color: "#64748b" }}>템플릿 선택</div>
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                <TemplateBtn active={selectedTemplate === "BEGINNER"} onClick={() => applyTemplate("BEGINNER")}>
                  초보형
                </TemplateBtn>
                <TemplateBtn active={selectedTemplate === "HABIT"} onClick={() => applyTemplate("HABIT")}>
                  습관형
                </TemplateBtn>
                <TemplateBtn active={selectedTemplate === "AGGRESSIVE"} onClick={() => applyTemplate("AGGRESSIVE")}>
                  공격형
                </TemplateBtn>
                <button
                  type="button"
                  className="home-link-btn"
                  onClick={() => {
                    setSelectedTemplate("");
                    setCreateForm((p) => ({ ...DEFAULT_FORM(), startDate: p.startDate, endDate: p.endDate }));
                  }}
                >
                  초기화
                </button>
              </div>
            </div>

            <input
              placeholder="챌린지명 (예: 30일 +5% 달성)"
              value={createForm.title}
              onChange={(e) => setCreateForm((p) => ({ ...p, title: e.target.value }))}
            />

            <textarea
              rows={3}
              placeholder="챌린지 설명 (운영 규칙/목표/참여 조건)"
              value={createForm.description}
              onChange={(e) => setCreateForm((p) => ({ ...p, description: e.target.value }))}
            />

            <div style={{ display: "grid", gap: 8, gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))" }}>
              <select
                value={createForm.goalType}
                onChange={(e) =>
                  setCreateForm((p) => ({
                    ...p,
                    goalType: e.target.value,
                    targetValue: e.target.value === "RETURN_RATE" ? (p.targetValue || "5") : "0",
                  }))
                }
              >
                <option value="RETURN_RATE">수익률형</option>
                <option value="DAILY_BUY_QUANTITY">습관형 (1일 N주 매수)</option>
              </select>

              <select
                value={createForm.visibility}
                onChange={(e) => setCreateForm((p) => ({ ...p, visibility: e.target.value }))}
              >
                <option value="PUBLIC">공개</option>
                <option value="PRIVATE">비공개</option>
              </select>

              <input
                type="number"
                min="1"
                value={createForm.maxParticipants}
                onChange={(e) => setCreateForm((p) => ({ ...p, maxParticipants: e.target.value }))}
                placeholder="최대 참여자 수"
              />

              <input
                type="date"
                value={createForm.startDate}
                onChange={(e) => setCreateForm((p) => ({ ...p, startDate: e.target.value }))}
              />

              <input
                type="date"
                value={createForm.endDate}
                onChange={(e) => setCreateForm((p) => ({ ...p, endDate: e.target.value }))}
              />

              {createForm.goalType === "RETURN_RATE" ? (
                <input
                  type="number"
                  step="0.1"
                  value={createForm.targetValue}
                  onChange={(e) => setCreateForm((p) => ({ ...p, targetValue: e.target.value }))}
                  placeholder="목표 수익률(%)"
                />
              ) : (
                <>
                  <input
                    value={createForm.habitCode}
                    onChange={(e) => setCreateForm((p) => ({ ...p, habitCode: e.target.value }))}
                    placeholder="종목코드 (예: 005930)"
                  />
                  <input
                    type="number"
                    min="1"
                    value={createForm.habitDailyBuyQuantity}
                    onChange={(e) => setCreateForm((p) => ({ ...p, habitDailyBuyQuantity: e.target.value }))}
                    placeholder="하루 매수 수량"
                  />
                  <input
                    type="number"
                    min="1"
                    value={createForm.habitRequiredDays}
                    onChange={(e) => setCreateForm((p) => ({ ...p, habitRequiredDays: e.target.value }))}
                    placeholder="달성 필요 일수"
                  />
                </>
              )}
            </div>

            <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
              <button
                type="button"
                className="home-link-btn"
                onClick={() => {
                  const base = String(leagueState?.currentDate || createForm.startDate || "").trim();
                  if (!base) return;
                  setCreateForm((p) => ({ ...p, startDate: base, endDate: addDaysString(base, 7) }));
                }}
              >
                리그 기준일 +7일
              </button>
              <button
                type="button"
                className="home-link-btn"
                onClick={() => {
                  const base = String(leagueState?.currentDate || createForm.startDate || "").trim();
                  if (!base) return;
                  setCreateForm((p) => ({ ...p, startDate: base, endDate: addDaysString(base, 30) }));
                }}
              >
                리그 기준일 +30일
              </button>
              <div style={{ fontSize: 12, color: "#64748b" }}>{createGoalPreview(createForm)}</div>
              <button type="button" onClick={onCreate}>
                생성하고 상세 보기
              </button>
            </div>
          </div>
        )}
      </div>

      <div className="app-card">
        <div
          className="app-toolbar-row"
          style={{ justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap" }}
        >
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <TabBtn active={statusTab === "ALL"} onClick={() => setStatusTab("ALL")}>
              전체 {counts.ALL}
            </TabBtn>
            <TabBtn active={statusTab === "ONGOING"} onClick={() => setStatusTab("ONGOING")}>
              진행중 {counts.ONGOING}
            </TabBtn>
            <TabBtn active={statusTab === "UPCOMING"} onClick={() => setStatusTab("UPCOMING")}>
              예정 {counts.UPCOMING}
            </TabBtn>
            <TabBtn active={statusTab === "ENDED"} onClick={() => setStatusTab("ENDED")}>
              종료 {counts.ENDED}
            </TabBtn>
          </div>

          <input
            style={{ minWidth: 260 }}
            placeholder="챌린지 검색 (제목/설명/생성자)"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>

        <div style={{ display: "grid", gap: 12, marginTop: 12 }}>
          {loading && <div>불러오는 중...</div>}
          {!loading && filteredRows.length === 0 && <div>조건에 맞는 챌린지가 없습니다.</div>}

          {filteredRows.map((c) => {
            const ratio =
              Number(c?.maxParticipants || 0) > 0
                ? Math.min(100, (Number(c?.participantCount || 0) / Number(c.maxParticipants)) * 100)
                : 0;
            const statusColor = c.status === "ONGOING" ? "#16a34a" : c.status === "UPCOMING" ? "#2563eb" : "#64748b";

            return (
              <div
                key={`challenge-card-${c.id}`}
                style={{ border: "1px solid #e2e8f0", borderRadius: 14, padding: 14, display: "grid", gap: 10 }}
              >
                <div style={{ display: "flex", justifyContent: "space-between", gap: 10, alignItems: "start", flexWrap: "wrap" }}>
                  <div style={{ display: "grid", gap: 4, minWidth: 0 }}>
                    <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                      <strong>{c.title}</strong>
                      <span
                        style={{
                          fontSize: 12,
                          padding: "2px 8px",
                          borderRadius: 999,
                          background: "#f1f5f9",
                          color: statusColor,
                        }}
                      >
                        {statusLabel(c.status)}
                      </span>
                      {c.joined && (
                        <span
                          style={{
                            fontSize: 12,
                            padding: "2px 8px",
                            borderRadius: 999,
                            background: "#ecfdf5",
                            color: "#166534",
                          }}
                        >
                          참여중
                        </span>
                      )}
                    </div>

                    <div style={{ fontSize: 13, color: "#475569", whiteSpace: "pre-wrap" }}>{c.description}</div>
                    <div style={{ fontSize: 12, color: "#64748b" }}>
                      기간 {c.startDate} ~ {c.endDate} · 목표 {renderGoalSummary(c)} · 공개범위{" "}
                      {c.visibility === "PUBLIC" ? "공개" : "비공개"}
                    </div>
                  </div>

                  <button type="button" onClick={() => navigateTo?.(`/challenges/${c.id}`)}>
                    상세 보기
                  </button>
                </div>

                <div style={{ display: "grid", gap: 6 }}>
                  <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, color: "#64748b" }}>
                    <span>참여 현황</span>
                    <span>
                      {c.participantCount}/{c.maxParticipants}명
                    </span>
                  </div>
                  <ProgressBar pct={ratio} color="#0ea5e9" />
                </div>

                <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(140px,1fr))", gap: 8 }}>
                  <MiniStat label="챌린지 ID" value={`#${c.id}`} />
                  <MiniStat label="참여자" value={`${c.participantCount}명`} />
                  <MiniStat label="목표 유형" value={goalTypeLabel(c.goalType)} />
                  <MiniStat label="생성자" value={c.ownerUserName || "-"} />
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

function TabBtn({ active, onClick, children }) {
  return (
    <button type="button" onClick={onClick} className={active ? "app-nav-btn active" : "app-nav-btn"}>
      {children}
    </button>
  );
}

function TemplateBtn({ active, onClick, children }) {
  return (
    <button type="button" onClick={onClick} className={active ? "app-nav-btn active" : "app-nav-btn"}>
      {children}
    </button>
  );
}

function MiniStat({ label, value }) {
  return (
    <div style={{ border: "1px solid #e2e8f0", borderRadius: 8, padding: "8px 10px" }}>
      <div style={{ fontSize: 11, color: "#64748b" }}>{label}</div>
      <div style={{ fontWeight: 700, overflowWrap: "anywhere" }}>{value}</div>
    </div>
  );
}

function ProgressBar({ pct, color = "#0ea5e9" }) {
  const width = Math.max(0, Math.min(100, Number(pct || 0)));
  return (
    <div style={{ height: 8, borderRadius: 999, background: "#e2e8f0", overflow: "hidden" }}>
      <div style={{ width: `${width}%`, height: "100%", background: color }} />
    </div>
  );
}

function statusLabel(status) {
  switch (String(status || "").toUpperCase()) {
    case "ONGOING":
      return "진행중";
    case "UPCOMING":
      return "예정";
    case "ENDED":
      return "종료";
    default:
      return "기타";
  }
}

function goalTypeLabel(goalType) {
  const key = String(goalType || "").toUpperCase();
  if (key === "RETURN_RATE") return "수익률형";
  if (key === "DAILY_BUY_QUANTITY") return "습관형";
  return key || "-";
}

function renderGoalSummary(c) {
  const key = String(c?.goalType || "").toUpperCase();
  if (key === "DAILY_BUY_QUANTITY") {
    const code = String(c?.habitCode || "-");
    const qty = Number(c?.habitDailyBuyQuantity || 0);
    const days = Number(c?.habitRequiredDays || 0);
    return `${code} 하루 ${qty}주 매수 · ${days}일 달성`;
  }
  return `수익률 ${Number(c?.targetValue || 0).toFixed(1)}%`;
}

function createGoalPreview(form) {
  if (String(form.goalType || "").toUpperCase() === "DAILY_BUY_QUANTITY") {
    return `목표 미리보기: ${String(form.habitCode || "").trim() || "-"} 하루 ${
      Number(form.habitDailyBuyQuantity || 0) || 0
    }주 매수, ${Number(form.habitRequiredDays || 0) || 0}일 달성`;
  }
  return `목표 미리보기: 수익률 ${Number(form.targetValue || 0).toFixed(1)}%`;
}

function addDate(date, days) {
  const d = new Date(date);
  d.setDate(d.getDate() + Number(days || 0));
  return d;
}

function toLocalDate(date) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

function addDaysString(yyyyMmDd, days) {
  try {
    const d = new Date(`${yyyyMmDd}T00:00:00`);
    d.setDate(d.getDate() + Number(days || 0));
    return toLocalDate(d);
  } catch {
    return yyyyMmDd;
  }
}
