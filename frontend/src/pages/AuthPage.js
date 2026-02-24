import React from "react";

export default function AuthPage({
  authPage,
  authLoading,
  authMessage,
  loginName,
  setLoginName,
  loginPassword,
  setLoginPassword,
  signupName,
  setSignupName,
  signupPassword,
  setSignupPassword,
  onLogin,
  onSignup,
  navigateTo,
}) {
  return (
    <div style={{ padding: 20, minHeight: "100vh", background: "#f9fafb" }}>
      <header style={{ marginBottom: 20 }}>
        <h1 style={{ margin: 0 }}>Gen-Z Investment Platform</h1>
        <div style={{ color: "#64748b", marginTop: 6 }}>
          {authPage === "login" ? "Login" : "Sign Up"}
        </div>
      </header>

      <div style={{ maxWidth: 420, margin: "40px auto", padding: 20, background: "#fff", border: "1px solid #e5e7eb", borderRadius: 12 }}>
        <div style={{ display: "flex", gap: 8, marginBottom: 14 }}>
          <button
            type="button"
            onClick={() => navigateTo("/login")}
            style={{
              flex: 1,
              padding: 10,
              borderRadius: 8,
              border: authPage === "login" ? "none" : "1px solid #d1d5db",
              background: authPage === "login" ? "#2563eb" : "#fff",
              color: authPage === "login" ? "#fff" : "#111827",
              fontWeight: 700,
            }}
          >
            Login
          </button>
          <button
            type="button"
            onClick={() => navigateTo("/signup")}
            style={{
              flex: 1,
              padding: 10,
              borderRadius: 8,
              border: authPage === "signup" ? "none" : "1px solid #d1d5db",
              background: authPage === "signup" ? "#2563eb" : "#fff",
              color: authPage === "signup" ? "#fff" : "#111827",
              fontWeight: 700,
            }}
          >
            Sign Up
          </button>
        </div>

        {authPage === "login" ? (
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            <input value={loginName} onChange={(e) => setLoginName(e.target.value)} placeholder="Name" style={inputStyle} />
            <input
              type="password"
              value={loginPassword}
              onChange={(e) => setLoginPassword(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && onLogin()}
              placeholder="Password"
              style={inputStyle}
            />
            <button type="button" onClick={onLogin} disabled={authLoading} style={primaryBtn}>
              Login
            </button>
          </div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            <input value={signupName} onChange={(e) => setSignupName(e.target.value)} placeholder="Name" style={inputStyle} />
            <input
              type="password"
              value={signupPassword}
              onChange={(e) => setSignupPassword(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && onSignup()}
              placeholder="Password"
              style={inputStyle}
            />
            <button type="button" onClick={onSignup} disabled={authLoading} style={secondaryBtn}>
              Create Account
            </button>
          </div>
        )}

        {authMessage && <div style={{ marginTop: 12, color: "#b91c1c", fontWeight: 600 }}>{authMessage}</div>}
      </div>
    </div>
  );
}

const inputStyle = { padding: 10, borderRadius: 8, border: "1px solid #d1d5db" };
const primaryBtn = { padding: "10px 12px", borderRadius: 8, border: "none", background: "#2563eb", color: "#fff", fontWeight: 700 };
const secondaryBtn = { padding: "10px 12px", borderRadius: 8, border: "1px solid #2563eb", background: "#fff", color: "#2563eb", fontWeight: 700 };
