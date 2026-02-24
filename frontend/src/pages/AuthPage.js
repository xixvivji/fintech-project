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
    <div className="auth-page">
      <header className="auth-page-header">
        <h1 className="auth-page-title">Gen-Z Investment Platform</h1>
        <div className="auth-page-subtitle">{authPage === "login" ? "Login" : "Sign Up"}</div>
      </header>

      <div className="app-card auth-card">
        <div className="auth-tab-row">
          <button
            type="button"
            onClick={() => navigateTo("/login")}
            className={authPage === "login" ? "auth-tab-btn active" : "auth-tab-btn"}
          >
            Login
          </button>
          <button
            type="button"
            onClick={() => navigateTo("/signup")}
            className={authPage === "signup" ? "auth-tab-btn active" : "auth-tab-btn"}
          >
            Sign Up
          </button>
        </div>

        {authPage === "login" ? (
          <div className="auth-form-stack">
            <input value={loginName} onChange={(e) => setLoginName(e.target.value)} placeholder="Name" />
            <input
              type="password"
              value={loginPassword}
              onChange={(e) => setLoginPassword(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && onLogin()}
              placeholder="Password"
            />
            <button type="button" onClick={onLogin} disabled={authLoading} className="auth-submit-btn primary">
              Login
            </button>
          </div>
        ) : (
          <div className="auth-form-stack">
            <input value={signupName} onChange={(e) => setSignupName(e.target.value)} placeholder="Name" />
            <input
              type="password"
              value={signupPassword}
              onChange={(e) => setSignupPassword(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && onSignup()}
              placeholder="Password"
            />
            <button type="button" onClick={onSignup} disabled={authLoading} className="auth-submit-btn secondary">
              Create Account
            </button>
          </div>
        )}

        {authMessage && <div className="auth-message">{authMessage}</div>}
      </div>
    </div>
  );
}
