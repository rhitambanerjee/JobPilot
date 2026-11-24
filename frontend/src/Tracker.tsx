import { useCallback, useEffect, useState } from "react";
import { api } from "./api";
import type { AppNotification, Application, AuthUser, Stage } from "./types";
import { errorMessage } from "./util";
import { btn, btnPrimary } from "./ui";
import Board from "./components/Board";
import ArchivePage from "./components/ArchivePage";
import AnalyticsPage from "./components/AnalyticsPage";
import ApplicationDrawer from "./components/ApplicationDrawer";
import NewApplicationForm from "./components/NewApplicationForm";
import RulesPage from "./components/RulesPage";
import TemplatesPage from "./components/TemplatesPage";
import RemindersPage from "./components/RemindersPage";
import NotificationsPage from "./components/NotificationsPage";

type Tab = "board" | "archive" | "insights" | "rules" | "templates" | "reminders" | "notifications";

const TABS: { id: Tab; label: string }[] = [
  { id: "board", label: "Board" },
  { id: "archive", label: "Archive" },
  { id: "insights", label: "Insights" },
  { id: "rules", label: "Rules" },
  { id: "templates", label: "Templates" },
  { id: "reminders", label: "Reminders" },
  { id: "notifications", label: "Notifications" },
];

/** Shown instead of an empty board when the applications request failed, so it is never mistaken for "no data". */
function LoadFailure({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div
      role="alert"
      className="mx-auto mt-12 flex max-w-lg flex-col items-center gap-3 rounded-2xl border border-red-100 bg-white px-8 py-12 text-center shadow-sm sm:mt-16"
    >
      <h2 className="text-lg font-semibold tracking-tight text-gray-900">Couldn't load your applications</h2>
      <p className="text-sm text-gray-500">{message}</p>
      <p className="text-xs text-gray-400">
        This is a connection problem, not an empty account. Check that the backend is running on port 8080.
      </p>
      <button className={btnPrimary} onClick={onRetry}>
        Try again
      </button>
    </div>
  );
}

export default function Tracker({ user, onLogout }: { user: AuthUser; onLogout: () => void }) {
  const [tab, setTab] = useState<Tab>("board");
  const [applications, setApplications] = useState<Application[]>([]);
  const [notifications, setNotifications] = useState<AppNotification[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [showNew, setShowNew] = useState(false);

  const reload = useCallback(async () => {
    try {
      const [apps, notes] = await Promise.all([api.listApplications(), api.notifications()]);
      setApplications(apps);
      setNotifications(notes);
      setLoadError(null);
    } catch (e) {
      setLoadError(errorMessage(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  // Rules run asynchronously (outbox -> worker), so re-poll gently to pick up their effects.
  useEffect(() => {
    const timer = setInterval(() => void reload(), 15_000);
    return () => clearInterval(timer);
  }, [reload]);

  const retry = useCallback(() => {
    setLoading(true);
    void reload();
  }, [reload]);

  const move = useCallback(async (id: string, stage: Stage) => {
    try {
      const updated = await api.changeStage(id, stage);
      setApplications((apps) => apps.map((a) => (a.id === id ? updated : a)));
      setError(null);
    } catch (e) {
      setError(errorMessage(e)); // e.g. 409 "Cannot move from APPLIED to OFFER"
    }
  }, []);

  const active = applications.filter((a) => a.stage !== "ARCHIVED");
  const archived = applications.filter((a) => a.stage === "ARCHIVED");
  const unread = notifications.filter((n) => !n.readAt).length;
  const selected = applications.find((a) => a.id === selectedId) ?? null;

  const needsApplications = tab === "board" || tab === "archive";
  const loadFailed = loadError !== null && applications.length === 0;

  return (
    <div className="flex h-screen flex-col">
      <header className="sticky top-0 z-20 border-b border-gray-200 bg-white/90 backdrop-blur">
        {/* Small screens: logo + action on the first row, the tabs on their own scrollable row below. */}
        <div className="flex flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3 sm:px-6">
          <div className="order-1 flex items-center gap-2.5">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-gray-900 text-sm font-bold text-white">
              J
            </div>
            <h1 className="text-base font-semibold tracking-tight text-gray-900">Job Tracker</h1>
          </div>

          <div className="order-2 flex-1 lg:order-3" />

          <div className="order-3 flex items-center gap-3 lg:order-4">
            {tab === "board" && (
              <button className={btnPrimary} onClick={() => setShowNew(true)}>
                + New application
              </button>
            )}
            <span className="hidden max-w-48 truncate text-sm text-gray-500 md:inline" title={user.email}>
              {user.displayName ?? user.email}
            </span>
            <button className={btn} onClick={onLogout}>
              Log out
            </button>
          </div>

          <nav
            aria-label="Main sections"
            className="order-5 flex w-full gap-1 overflow-x-auto rounded-xl bg-gray-100 p-1 lg:order-2 lg:w-auto"
          >
            {TABS.map((t) => {
              const badge = t.id === "archive" ? archived.length : t.id === "notifications" ? unread : 0;
              return (
                <button
                  key={t.id}
                  onClick={() => setTab(t.id)}
                  aria-current={tab === t.id ? "page" : undefined}
                  className={
                    "flex shrink-0 items-center gap-1.5 whitespace-nowrap rounded-lg px-3.5 py-1.5 text-sm font-medium transition " +
                    (tab === t.id ? "bg-white text-gray-900 shadow-sm" : "text-gray-500 hover:text-gray-800")
                  }
                >
                  {t.label}
                  {badge > 0 && (
                    <span
                      className={
                        "rounded-full px-1.5 text-xs " +
                        (t.id === "notifications" ? "bg-gray-900 text-white" : "bg-gray-200 text-gray-600")
                      }
                    >
                      {badge}
                    </span>
                  )}
                </button>
              );
            })}
          </nav>
        </div>
      </header>

      {error && (
        <div
          role="alert"
          className="flex items-center justify-between gap-4 border-b border-red-100 bg-red-50 px-4 py-2.5 text-sm text-red-700 sm:px-6"
        >
          <span>{error}</span>
          <button className="font-medium underline" onClick={() => setError(null)}>
            Dismiss
          </button>
        </div>
      )}

      {loadError && applications.length > 0 && (
        <div
          role="status"
          className="flex items-center justify-between gap-4 border-b border-amber-100 bg-amber-50 px-4 py-2.5 text-sm text-amber-800 sm:px-6"
        >
          <span>Couldn't refresh. Showing the last data that loaded.</span>
          <button className="font-medium underline" onClick={() => void reload()}>
            Retry
          </button>
        </div>
      )}

      <main className="min-h-0 min-w-0 flex-1 overflow-y-auto">
        {needsApplications && loading && <p className="p-8 text-sm text-gray-500">Loading…</p>}
        {needsApplications && !loading && loadFailed && loadError && <LoadFailure message={loadError} onRetry={retry} />}

        {tab === "board" && !loading && !loadFailed && (
          <Board
            applications={active}
            hasArchived={archived.length > 0}
            onOpen={setSelectedId}
            onMove={move}
            onAdd={() => setShowNew(true)}
          />
        )}
        {tab === "archive" && !loading && !loadFailed && <ArchivePage applications={archived} onOpen={setSelectedId} />}
        {tab === "insights" && <AnalyticsPage />}
        {tab === "rules" && <RulesPage onError={setError} />}
        {tab === "templates" && <TemplatesPage onError={setError} />}
        {tab === "reminders" && (
          <RemindersPage applications={applications} onError={setError} onOpenRules={() => setTab("rules")} />
        )}
        {tab === "notifications" && (
          <NotificationsPage
            notifications={notifications}
            onOpenApplication={setSelectedId}
            onChanged={() => void reload()}
            onError={setError}
          />
        )}
      </main>

      {selected && (
        <ApplicationDrawer
          application={selected}
          onClose={() => setSelectedId(null)}
          onMove={move}
          onUpdated={(updated) => setApplications((apps) => apps.map((a) => (a.id === updated.id ? updated : a)))}
          onError={setError}
        />
      )}

      {showNew && (
        <NewApplicationForm
          onClose={() => setShowNew(false)}
          onCreated={(created) => {
            setApplications((apps) => [created, ...apps]);
            setShowNew(false);
          }}
          onError={setError}
        />
      )}
    </div>
  );
}
