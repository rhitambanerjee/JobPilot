import { useState } from "react";
import { api } from "../api";
import type { AuthUser } from "../types";
import { errorMessage } from "../util";
import { btnPrimary, card, field, lbl } from "../ui";

export default function LoginPage({ onAuthenticated }: { onAuthenticated: (user: AuthUser) => void }) {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const user =
        mode === "login"
          ? await api.login(email.trim(), password)
          : await api.register(email.trim(), password, displayName.trim() || null);
      onAuthenticated(user);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  const registering = mode === "register";

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="mb-6 flex flex-col items-center gap-3 text-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-gray-900 text-xl font-bold text-white">
            J
          </div>
          <h1 className="text-xl font-semibold tracking-tight text-gray-900">Job Tracker</h1>
          <p className="text-sm text-gray-500">
            {registering ? "Create an account to start tracking." : "Log in to see your applications."}
          </p>
        </div>

        <form onSubmit={submit} className={card + " flex flex-col gap-4 p-6"}>
          {error && (
            <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
              {error}
            </p>
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
          <label className={lbl}>
            Password
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

          <button className={btnPrimary} disabled={busy || !email.trim() || !password}>
            {busy ? "Please wait…" : registering ? "Create account" : "Log in"}
          </button>
        </form>

        <p className="mt-4 text-center text-sm text-gray-500">
          {registering ? "Already have an account?" : "New here?"}{" "}
          <button
            type="button"
            className="font-medium text-gray-900 underline underline-offset-2"
            onClick={() => {
              setMode(registering ? "login" : "register");
              setError(null);
            }}
          >
            {registering ? "Log in" : "Create an account"}
          </button>
        </p>
      </div>
    </div>
  );
}
