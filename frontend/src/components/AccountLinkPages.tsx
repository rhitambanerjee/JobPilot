import { useEffect, useRef, useState } from "react";
import { api } from "../api";
import { errorMessage } from "../util";
import { btnPrimary, card, field, lbl } from "../ui";

function Shell({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="mb-6 flex flex-col items-center gap-3 text-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-gray-900 text-xl font-bold text-white">
            J
          </div>
          <h1 className="text-xl font-semibold tracking-tight text-gray-900">{title}</h1>
        </div>
        <div className={card + " flex flex-col gap-4 p-6"}>{children}</div>
      </div>
    </div>
  );
}

/** Opened from the link in the verification email (/#verify=TOKEN). */
export function VerifyEmailPage({ token, onDone }: { token: string; onDone: () => void }) {
  const [state, setState] = useState<"working" | "ok" | { error: string }>("working");
  const started = useRef(false); // React StrictMode runs effects twice in dev; the link only works once

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    api
      .verifyEmail(token)
      .then(() => setState("ok"))
      .catch((e) => setState({ error: errorMessage(e) }));
  }, [token]);

  return (
    <Shell title="Verifying your email">
      {state === "working" && <p className="text-sm text-gray-500">One moment…</p>}
      {state === "ok" && (
        <p role="status" className="text-sm text-gray-700">
          Your email is verified. You can log in now.
        </p>
      )}
      {typeof state === "object" && (
        <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
          {state.error}
        </p>
      )}
      {state !== "working" && (
        <button className={btnPrimary} onClick={onDone}>
          {state === "ok" ? "Continue to log in" : "Back to log in"}
        </button>
      )}
    </Shell>
  );
}

/** Opened from the link in the password-reset email (/#reset=TOKEN). */
export function ResetPasswordPage({ token, onDone }: { token: string; onDone: () => void }) {
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (password !== confirm) {
      setError("The two passwords don't match.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api.resetPassword(token, password);
      setDone(true);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  if (done) {
    return (
      <Shell title="Password updated">
        <p role="status" className="text-sm text-gray-700">
          Your password has been changed and you've been logged out everywhere else. Log in with the new one.
        </p>
        <button className={btnPrimary} onClick={onDone}>
          Continue to log in
        </button>
      </Shell>
    );
  }

  return (
    <Shell title="Choose a new password">
      <form onSubmit={submit} className="flex flex-col gap-4">
        {error && (
          <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
            {error}
          </p>
        )}
        <label className={lbl}>
          New password
          <input
            className={field}
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={8}
            maxLength={72}
            autoComplete="new-password"
            autoFocus
          />
          <span className="font-normal text-gray-400">At least 8 characters.</span>
        </label>
        <label className={lbl}>
          Confirm new password
          <input
            className={field}
            type="password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            required
            autoComplete="new-password"
          />
        </label>
        <button className={btnPrimary} disabled={busy || !password || !confirm}>
          {busy ? "Saving…" : "Set new password"}
        </button>
        <button type="button" className="text-sm text-gray-500 underline underline-offset-2" onClick={onDone}>
          Cancel
        </button>
      </form>
    </Shell>
  );
}
