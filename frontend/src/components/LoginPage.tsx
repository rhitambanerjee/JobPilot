import { useState } from "react";
import { api, ApiError } from "../api";
import type { AuthUser } from "../types";
import { errorMessage } from "../util";
import { btn, btnPrimary, card, field, lbl } from "../ui";

type Mode = "login" | "register" | "forgot";

export default function LoginPage({ onAuthenticated }: { onAuthenticated: (user: AuthUser) => void }) {
  const [mode, setMode] = useState<Mode>("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [needsVerification, setNeedsVerification] = useState(false);
  const [busy, setBusy] = useState(false);

  function switchMode(next: Mode) {
    setMode(next);
    setError(null);
    setNotice(null);
    setNeedsVerification(false);
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setNotice(null);
    setNeedsVerification(false);
    try {
      if (mode === "login") {
        onAuthenticated(await api.login(email.trim(), password));
      } else if (mode === "register") {
        const result = await api.register(email.trim(), password, displayName.trim() || null);
        if (result.verificationRequired) {
          setMode("login");
          setPassword("");
          setNotice(`We sent a verification link to ${result.email}. Open it to activate your account, then log in.`);
        } else {
          onAuthenticated({ id: result.id, email: result.email, displayName: result.displayName });
        }
      } else {
        await api.forgotPassword(email.trim());
        setMode("login");
        setNotice("If an account exists for that email, we sent a password reset link. It expires in 1 hour.");
      }
    } catch (err) {
      setError(errorMessage(err));
      if (err instanceof ApiError && err.code === "EMAIL_NOT_VERIFIED") {
        setNeedsVerification(true);
      }
    } finally {
      setBusy(false);
    }
  }

  async function resend() {
    setBusy(true);
    try {
      await api.resendVerification(email.trim());
      setError(null);
      setNeedsVerification(false);
      setNotice("If your account exists and isn't verified yet, we sent a new link. The newest link is the one that works.");
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  const registering = mode === "register";
  const forgot = mode === "forgot";

  const subtitle = registering
    ? "Create an account to start tracking."
    : forgot
      ? "Enter your email and we'll send you a reset link."
      : "Log in to see your applications.";

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="mb-6 flex flex-col items-center gap-3 text-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-gray-900 text-xl font-bold text-white">
            J
          </div>
          <h1 className="text-xl font-semibold tracking-tight text-gray-900">Job Tracker</h1>
          <p className="text-sm text-gray-500">{subtitle}</p>
        </div>

        <form onSubmit={submit} className={card + " flex flex-col gap-4 p-6"}>
          {notice && (
            <p role="status" className="rounded-lg bg-gray-100 px-3 py-2 text-sm text-gray-700">
              {notice}
            </p>
          )}
          {error && (
            <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
              {error}
            </p>
          )}
          {needsVerification && (
            <button type="button" className={btn} onClick={() => void resend()} disabled={busy}>
              Resend verification email
            </button>
          )}

          {registering && (
            <label className={lbl}>
              Name (optional)
              <input
                className={field}
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                autoComplete="name"
                maxLength={100}
              />
            </label>
          )}
          <label className={lbl}>
            Email
            <input
              className={field}
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoFocus
              autoComplete="email"
            />
          </label>
          {!forgot && (
            <label className={lbl}>
              <span className="flex items-center justify-between">
                Password
                {mode === "login" && (
                  <button
                    type="button"
                    className="font-medium text-gray-500 underline underline-offset-2 hover:text-gray-900"
                    onClick={() => switchMode("forgot")}
                  >
                    Forgot password?
                  </button>
                )}
              </span>
              <input
                className={field}
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                minLength={registering ? 8 : undefined}
                maxLength={72}
                autoComplete={registering ? "new-password" : "current-password"}
              />
              {registering && <span className="font-normal text-gray-400">At least 8 characters.</span>}
            </label>
          )}

          <button className={btnPrimary} disabled={busy || !email.trim() || (!forgot && !password)}>
            {busy ? "Please wait…" : registering ? "Create account" : forgot ? "Send reset link" : "Log in"}
          </button>
        </form>

        <p className="mt-4 text-center text-sm text-gray-500">
          {mode === "login" ? "New here?" : registering ? "Already have an account?" : "Remembered it?"}{" "}
          <button
            type="button"
            className="font-medium text-gray-900 underline underline-offset-2"
            onClick={() => switchMode(mode === "login" ? "register" : "login")}
          >
            {mode === "login" ? "Create an account" : "Log in"}
          </button>
        </p>
      </div>
    </div>
  );
}
