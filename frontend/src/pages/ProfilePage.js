import React, { useEffect, useState } from "react";
import axios from "axios";

function headers(authToken) {
  return authToken ? { Authorization: `Bearer ${authToken}` } : {};
}

export default function ProfilePage({ apiBaseUrl, authToken, isLoggedIn }) {
  const [profile, setProfile] = useState(null);
  const [form, setForm] = useState({ displayName: "", bio: "" });
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");

  const loadProfile = async () => {
    if (!isLoggedIn || !authToken) return;
    setLoading(true);
    try {
      const res = await axios.get(`${apiBaseUrl}/api/profile/me`, { headers: headers(authToken) });
      setProfile(res.data || null);
      setForm({
        displayName: res.data?.displayName || "",
        bio: res.data?.bio || "",
      });
    } catch (err) {
      setMessage(err?.response?.data?.message || err?.message || "프로필 조회 실패");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadProfile();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoggedIn, authToken, apiBaseUrl]);

  if (!isLoggedIn) {
    return <div className="app-card"><h3>프로필</h3><p>로그인 후 이용할 수 있습니다.</p></div>;
  }

  const save = async () => {
    setSaving(true);
    setMessage("");
    try {
      const res = await axios.put(`${apiBaseUrl}/api/profile/me`, form, { headers: headers(authToken) });
      setProfile(res.data || null);
      setMessage("프로필을 저장했습니다.");
    } catch (err) {
      setMessage(err?.response?.data?.message || err?.message || "프로필 저장 실패");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="app-card" style={{ display: "grid", gap: 12 }}>
      <h3 style={{ margin: 0 }}>프로필</h3>
      {message && <div className="sim-trade-message">{message}</div>}
      {loading && <div>불러오는 중...</div>}
      {!loading && (
        <>
          <div style={{ color: "#64748b", fontSize: 13 }}>
            로그인 ID: <strong>{profile?.loginName || "-"}</strong>
          </div>
          <label style={{ display: "grid", gap: 6 }}>
            <span>표시 이름</span>
            <input value={form.displayName} onChange={(e) => setForm((p) => ({ ...p, displayName: e.target.value }))} maxLength={50} />
          </label>
          <label style={{ display: "grid", gap: 6 }}>
            <span>소개</span>
            <textarea rows={4} value={form.bio} onChange={(e) => setForm((p) => ({ ...p, bio: e.target.value }))} maxLength={300} />
          </label>
          <div style={{ display: "flex", gap: 8 }}>
            <button type="button" onClick={save} disabled={saving}>{saving ? "저장 중..." : "저장"}</button>
            <button type="button" onClick={loadProfile} disabled={loading}>다시 불러오기</button>
          </div>
        </>
      )}
    </div>
  );
}
