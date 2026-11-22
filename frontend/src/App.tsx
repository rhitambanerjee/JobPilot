import { useCallback, useEffect, useState } from "react";
import { api, setUnauthorizedHandler } from "./api";
import type { AuthUser } from "./types";
import LoginPage from "./components/LoginPage";
import Tracker from "./Tracker";

/**
 * Decides between the login page and the tracker. The tracker is keyed by user id, so logging out (or logging
 * in as someone else) throws away ALL of its state: nothing from one account can linger on screen for the next.
 */
export default function App() {
  const [user, setUser] = useState<AuthUser | null | "loading">("loading");

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

  if (user === "loading") {
    return <p className="p-8 text-sm text-gray-500">Loading…</p>;
  }
  if (user === null) {
    return <LoginPage onAuthenticated={setUser} />;
  }
  return <Tracker key={user.id} user={user} onLogout={() => void logout()} />;
}
