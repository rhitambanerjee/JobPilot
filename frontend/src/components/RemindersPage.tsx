import { useCallback, useEffect, useState } from "react";
import { api } from "../api";
import type { Application, Reminder } from "../types";
import { errorMessage, formatDate } from "../util";
import { btn, card } from "../ui";

interface Props {
  applications: Application[];
  onError: (message: string) => void;
  onOpenRules: () => void;
}

export default function RemindersPage({ applications, onError, onOpenRules }: Props) {
  const [dueOnly, setDueOnly] = useState(false);
  const [reminders, setReminders] = useState<Reminder[]>([]);

  const load = useCallback(async () => {
    try {
      setReminders(await api.openReminders(dueOnly));
    } catch (e) {
      onError(errorMessage(e));
    }
  }, [dueOnly, onError]);

  useEffect(() => {
    void load();
  }, [load]);

  async function complete(id: string) {
    try {
      await api.completeReminder(id);
      setReminders((rs) => rs.filter((r) => r.id !== id));
    } catch (e) {
      onError(errorMessage(e));
    }
  }

  const nameOf = (id: string) => {
    const app = applications.find((a) => a.id === id);
    return app ? `${app.company} · ${app.role}` : "Unknown application";
  };

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-4 px-6 py-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-xl font-semibold tracking-tight text-gray-900">Reminders</h2>
          <p className="mt-1 text-sm text-gray-500">Open follow-ups created by you or by your rules.</p>
        </div>
        <div className="flex items-center gap-3">
          <label className="flex items-center gap-2 text-sm text-gray-600">
            <input
              type="checkbox"
              className="h-4 w-4 rounded border-gray-300 accent-gray-900"
              checked={dueOnly}
              onChange={(e) => setDueOnly(e.target.checked)}
            />
            Due now or overdue only
          </label>
          <button className={btn} onClick={() => void load()}>
            Refresh
          </button>
        </div>
      </div>

      {reminders.length === 0 && (
        <div className="rounded-2xl border border-dashed border-gray-300 bg-white px-6 py-12 text-center">
          {dueOnly ? (
            <p className="text-sm text-gray-400">Nothing is due or overdue right now.</p>
          ) : (
            <>
              <p className="text-sm font-medium text-gray-600">No open reminders</p>
              <p className="mx-auto mt-1 max-w-md text-sm text-gray-400">
                Reminders are created by rules that use the “Create reminder” action, for example “remind me to follow up
                3 days after I apply”.
              </p>
              <button className={btn + " mt-4"} onClick={onOpenRules}>
                Set up a rule
              </button>
            </>
          )}
        </div>
      )}

      {reminders.map((r) => {
        const overdue = new Date(r.dueAt).getTime() < Date.now();
        return (
          <div key={r.id} className={card + " flex items-center justify-between gap-4 p-4"}>
            <div>
              <p className="font-medium text-gray-900">{r.message}</p>
              <p className="mt-0.5 text-sm text-gray-500">{nameOf(r.applicationId)}</p>
            </div>
            <div className="flex items-center gap-3">
              <span
                className={
                  "rounded-full px-2.5 py-0.5 text-xs font-medium " +
                  (overdue ? "bg-amber-50 text-amber-700" : "bg-gray-100 text-gray-600")
                }
              >
                Due {formatDate(r.dueAt)}
              </span>
              <button className={btn} onClick={() => complete(r.id)}>
                Done
              </button>
            </div>
          </div>
        );
      })}
    </div>
  );
}
