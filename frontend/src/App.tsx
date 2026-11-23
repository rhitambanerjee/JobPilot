import { useCallback, useEffect, useState } from "react";
import { api, setUnauthorizedHandler } from "./api";
import type { AuthUser } from "./types";
import LoginPage from "./components/LoginPage";
import { ResetPasswordPage, VerifyEmailPage } from "./components/AccountLinkPages";
import Tracker from "./Tracker";

type AccountLink = { kind: "verify" | "reset"; token: string };

/** Verification and reset emails link to /#verify=TOKEN or /#reset=TOKEN. The fragment never goes to any server. */
function readAccountLink(): AccountLink | null {
  const params = new URLSearchParams(window.location.hash.replace(/^#/, ""));
  const verify = params.get("verify");
  const reset = params.get("reset");
  if (verify) return { kind: "verify", token: verify };
  if (reset) return { kind: "reset", token: reset };
  return null;
}

/**
 * Decides between the login page and the tracker. The tracker is keyed by user id, so logging out (or logging
 * in as someone else) throws away ALL of its state: nothing from one account can linger on screen for the next.
 */
export default function App() {
  const [user, setUser] = useState<AuthUser | null | "loading">("loading");
  const [link, setLink] = useState<AccountLink | null>(readAccountLink);

  useEffect(() => {
    setUnauthorizedHandler(() => setUser(null)); // session expired while using the app
    api
      .me()
      .then(setUser)
      .catch(() => setUser(null));
    return () => setUnauthorizedHandler(null);
  }, []);

  const logout = useCallback(async () => {
    try {
      await api.logout();
    } finally {
      setUser(null);
    }
  }, []);

  const finishLink = () => {
    // drop the one-time token from the address bar so it can't be reused or shared by accident
    window.history.replaceState(null, "", window.location.pathname + window.location.search);
    setLink(null);
  };

  if (link?.kind === "verify") {
    return <VerifyEmailPage token={link.token} onDone={finishLink} />;
  }
  if (link?.kind === "reset") {
    return <ResetPasswordPage token={link.token} onDone={finishLink} />;
  }

  if (user === "loading") {
    return <p className="p-8 text-sm text-gray-500">Loading…</p>;
  }
  if (user === null) {
    return <LoginPage onAuthenticated={setUser} />;
  }
  return <Tracker key={user.id} user={user} onLogout={() => void logout()} />;
}
