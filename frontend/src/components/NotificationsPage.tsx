import { useEffect, useState } from "react";
import { api } from "../api";
import type { AppNotification } from "../types";
import { errorMessage, formatDateTime } from "../util";
import { btn, btnPrimary, card, field, lbl } from "../ui";

interface Props {
  notifications: AppNotification[];
  onOpenApplication: (id: string) => void;
  onChanged: () => void;
  onError: (message: string) => void;
}

interface Saved {
  email: string;
  enabled: boolean;
}

function EmailChip({ n }: { n: AppNotification }) {
  if (n.emailStatus === "NONE") return null;
  const styles = {
    PENDING: { text: "Email queued", cls: "bg-gray-100 text-gray-600" },
    SENT: { text: "Emailed", cls: "bg-gray-100 text-gray-600" },
    FAILED: { text: "Email failed", cls: "bg-red-50 text-red-600" },
  }[n.emailStatus];
  return (
    <span title={n.emailError ?? n.emailTo ?? undefined} className={"rounded-full px-2 py-0.5 text-xs font-medium " + styles.cls}>
      {styles.text}
    </span>
  );
}

export default function NotificationsPage({ notifications, onOpenApplication, onChanged, onError }: Props) {
  const [email, setEmail] = useState("");
  const [enabled, setEnabled] = useState(false);
  const [saved, setSaved] = useState<Saved | null>(null);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);

  useEffect(() => {
    api
      .getNotificationSettings()
      .then((s) => {
        setEmail(s.email ?? "");
        setEnabled(s.emailEnabled);
        setSaved({ email: s.email ?? "", enabled: s.emailEnabled });
      })
      .catch((e) => onError(errorMessage(e)));
  }, [onError]);

  const dirty = saved !== null && (email.trim() !== saved.email || enabled !== saved.enabled);
  const unread = notifications.filter((n) => !n.readAt).length;

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    try {
      const s = await api.saveNotificationSettings({ email: email.trim() || null, emailEnabled: enabled });
      setEmail(s.email ?? "");
      setEnabled(s.emailEnabled);
      setSaved({ email: s.email ?? "", enabled: s.emailEnabled });
    } catch (err) {
      onError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  async function sendTest() {
    setTesting(true);
    try {
      await api.sendTestNotification();
      onChanged();
    } catch (err) {
      onError(errorMessage(err));
    } finally {
      setTesting(false);
    }
  }

  async function open(n: AppNotification) {
    try {
      if (!n.readAt) {
        await api.markNotificationRead(n.id);
        onChanged();
      }
      if (n.applicationId) onOpenApplication(n.applicationId);
    } catch (err) {
      onError(errorMessage(err));
    }
  }

  async function markAllRead() {
    try {
      await api.markAllNotificationsRead();
      onChanged();
    } catch (err) {
      onError(errorMessage(err));
    }
  }

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6 px-6 py-8">
      <div>
        <h2 className="text-xl font-semibold tracking-tight text-gray-900">Notifications</h2>
        <p className="mt-1 text-sm text-gray-500">Every time one of your rules runs, it shows up here.</p>
      </div>

      <form onSubmit={save} className={card + " flex flex-col gap-4 p-5"}>
        <div>
          <h3 className="text-sm font-semibold text-gray-900">Email notifications</h3>
          <p className="mt-0.5 text-sm text-gray-500">Get the same notifications in your inbox. Off by default.</p>
        </div>

        <label className={lbl}>
          Email address
          <input
            className={field}
            type="email"
            placeholder="you@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required={enabled}
          />
        </label>

        <label className="flex items-start gap-2.5 text-sm text-gray-700">
          <input
            type="checkbox"
            className="mt-0.5 h-4 w-4 rounded border-gray-300 accent-gray-900"
            checked={enabled}
            onChange={(e) => setEnabled(e.target.checked)}
          />
          <span>I agree to receive an email whenever one of my rules runs.</span>
        </label>

        <div className="flex flex-wrap justify-end gap-2">
          <button
            type="button"
            className={btn}
            onClick={() => void sendTest()}
            disabled={testing || dirty || !saved?.enabled}
            title={dirty ? "Save your changes first" : undefined}
          >
            {testing ? "Sending…" : "Send test email"}
          </button>
          <button className={btnPrimary} disabled={saving || !dirty}>
            {saving ? "Saving…" : "Save"}
          </button>
        </div>
      </form>

      <section className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-semibold text-gray-900">
            Recent {unread > 0 && <span className="ml-1 font-normal text-gray-500">· {unread} unread</span>}
          </h3>
          <button className={btn} onClick={() => void markAllRead()} disabled={unread === 0}>
            Mark all as read
          </button>
        </div>

        {notifications.length === 0 && (
          <div className="rounded-2xl border border-dashed border-gray-300 bg-white py-14 text-center text-sm text-gray-400">
            No notifications yet. They appear when a rule runs.
          </div>
        )}

        {notifications.map((n) => (
          <button
            key={n.id}
            onClick={() => void open(n)}
            className={
              card +
              " flex items-start gap-3 p-4 text-left transition hover:shadow-md " +
              (n.readAt ? "opacity-70" : "")
            }
          >
            <span className={"mt-1.5 h-2 w-2 shrink-0 rounded-full " + (n.readAt ? "bg-transparent" : "bg-gray-900")} />
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <p className={"text-sm text-gray-900 " + (n.readAt ? "font-medium" : "font-semibold")}>{n.title}</p>
                <EmailChip n={n} />
              </div>
              <p className="mt-0.5 text-sm text-gray-500">{n.message}</p>
              <p className="mt-1.5 text-xs text-gray-400">{formatDateTime(n.createdAt)}</p>
            </div>
          </button>
        ))}
      </section>
    </div>
  );
}
