import { useEffect, useState } from "react";
import { api } from "../api";
import type { Application, AuditLog, Checklist, OutgoingEmail, Reminder, Stage } from "../types";
import { errorMessage, formatDate, formatDateTime, stageLabel } from "../util";
import { btn, btnPrimary, field, lbl, pill, sectionTitle } from "../ui";
import { useDialog } from "../useDialog";

interface Props {
  application: Application;
  onClose: () => void;
  onMove: (id: string, stage: Stage) => void;
  onUpdated: (application: Application) => void;
  onError: (message: string) => void;
}

function describe(log: AuditLog): string {
  const d = log.details;
  if (log.action === "STAGE_CHANGED") return `Moved ${stageLabel(String(d.from))} → ${stageLabel(String(d.to))}`;
  if (log.action === "CREATED") return `Created as ${stageLabel(String(d.stage))}`;
  if (log.action === "DETAILS_UPDATED") return `Updated ${Object.keys(d).join(", ")}`;
  if (log.action === "CONTACT_UPDATED") return d.contactEmail ? `Contact email set to ${String(d.contactEmail)}` : "Contact email cleared";
  return log.action;
}

const EMAIL_STATUS = {
  PENDING: { text: "Queued", cls: "bg-gray-100 text-gray-600" },
  SENT: { text: "Sent", cls: "bg-gray-100 text-gray-600" },
  FAILED: { text: "Failed", cls: "bg-red-50 text-red-600" },
} as const;

export default function ApplicationDrawer({ application, onClose, onMove, onUpdated, onError }: Props) {
  const [audit, setAudit] = useState<AuditLog[]>([]);
  const [checklists, setChecklists] = useState<Checklist[]>([]);
  const [reminders, setReminders] = useState<Reminder[]>([]);
  const [emails, setEmails] = useState<OutgoingEmail[]>([]);
  const [contact, setContact] = useState(application.contactEmail ?? "");
  const [savingContact, setSavingContact] = useState(false);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState({ company: "", role: "", jobUrl: "", notes: "" });
  const [savingEdit, setSavingEdit] = useState(false);
  // Escape first cancels an edit in progress; only a second press closes the drawer
  const dialogRef = useDialog<HTMLElement>(() => (editing ? setEditing(false) : onClose()));

  useEffect(() => {
    setContact(application.contactEmail ?? "");
  }, [application.id, application.contactEmail]);

  // Reload the side data whenever the application changes (a stage move or a finished rule job).
  useEffect(() => {
    let cancelled = false;
    Promise.all([
      api.audit(application.id),
      api.checklists(application.id),
      api.remindersFor(application.id),
      api.outgoingEmails(application.id),
    ])
      .then(([a, c, r, e]) => {
        if (cancelled) return;
        setAudit(a);
        setChecklists(c);
        setReminders(r);
        setEmails(e);
      })
      .catch((e) => onError(errorMessage(e)));
    return () => {
      cancelled = true;
    };
  }, [application.id, application.updatedAt, onError]);

  async function complete(id: string) {
    try {
      const updated = await api.completeReminder(id);
      setReminders((rs) => rs.map((r) => (r.id === id ? updated : r)));
    } catch (e) {
      onError(errorMessage(e));
    }
  }

  function startEdit() {
    setDraft({
      company: application.company,
      role: application.role,
      jobUrl: application.jobUrl ?? "",
      notes: application.notes ?? "",
    });
    setEditing(true);
  }

  async function saveEdit(e: React.FormEvent) {
    e.preventDefault();
    setSavingEdit(true);
    try {
      const updated = await api.updateApplication(application.id, {
        company: draft.company.trim(),
        role: draft.role.trim(),
        jobUrl: draft.jobUrl.trim() || null,
        notes: draft.notes.trim() || null,
      });
      onUpdated(updated);
      setEditing(false);
    } catch (err) {
      onError(errorMessage(err)); // e.g. "The job link must start with http:// or https://"
    } finally {
      setSavingEdit(false);
    }
  }

  async function saveContact(e: React.FormEvent) {
    e.preventDefault();
    setSavingContact(true);
    try {
      onUpdated(await api.updateContactEmail(application.id, contact.trim() || null));
    } catch (err) {
      onError(errorMessage(err));
    } finally {
      setSavingContact(false);
    }
  }

  return (
    <div className="fixed inset-0 z-30 flex justify-end bg-gray-900/30 backdrop-blur-[2px]" onClick={onClose}>
      <aside
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="drawer-title"
        onClick={(e) => e.stopPropagation()}
        className="flex h-full w-full max-w-md flex-col gap-6 overflow-y-auto bg-white p-6 shadow-xl"
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 id="drawer-title" className="text-xl font-semibold tracking-tight text-gray-900">
              {application.company}
            </h2>
            <p className="mt-0.5 text-sm text-gray-500">{application.role}</p>
            <div className="mt-3 flex items-center gap-3">
              <span className={pill}>{stageLabel(application.stage)}</span>
              {application.jobUrl && (
                <a
                  href={application.jobUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="text-sm font-medium text-gray-600 underline decoration-gray-300 underline-offset-2 hover:text-gray-900"
                >
                  Job posting ↗
                </a>
              )}
            </div>
          </div>
          <button className={btn} onClick={onClose} aria-label="Close">
            ✕
          </button>
        </div>

        {editing ? (
          <form onSubmit={saveEdit} className="flex flex-col gap-3 rounded-xl border border-gray-200 bg-gray-50 p-4">
            <label className={lbl}>
              Company
              <input
                className={field}
                value={draft.company}
                onChange={(e) => setDraft({ ...draft, company: e.target.value })}
                required
                maxLength={200}
              />
            </label>
            <label className={lbl}>
              Role
              <input
                className={field}
                value={draft.role}
                onChange={(e) => setDraft({ ...draft, role: e.target.value })}
                required
                maxLength={200}
              />
            </label>
            <label className={lbl}>
              Job link
              <input
                className={field}
                type="url"
                value={draft.jobUrl}
                onChange={(e) => setDraft({ ...draft, jobUrl: e.target.value })}
                placeholder="https://…"
              />
            </label>
            <label className={lbl}>
              Notes
              <textarea
                className={field}
                rows={4}
                value={draft.notes}
                onChange={(e) => setDraft({ ...draft, notes: e.target.value })}
                maxLength={5000}
              />
            </label>
            <div className="flex justify-end gap-2">
              <button type="button" className={btn} onClick={() => setEditing(false)}>
                Cancel
              </button>
              <button className={btnPrimary} disabled={savingEdit || !draft.company.trim() || !draft.role.trim()}>
                {savingEdit ? "Saving…" : "Save changes"}
              </button>
            </div>
          </form>
        ) : (
          <>
            {application.notes && (
              <p className="whitespace-pre-wrap rounded-xl bg-gray-50 p-3 text-sm text-gray-600">{application.notes}</p>
            )}
            <div>
              <button className={btn} onClick={startEdit}>
                Edit details
              </button>
            </div>
          </>
        )}

        <section className="flex flex-col gap-2.5">
          <h4 className={sectionTitle}>Move to</h4>
          {application.allowedNext.length === 0 ? (
            <p className="text-sm text-gray-400">No further moves from this stage.</p>
          ) : (
            <div className="flex flex-wrap gap-2">
              {application.allowedNext.map((s) => (
                <button key={s} className={btn} onClick={() => onMove(application.id, s)}>
                  {stageLabel(s)}
                </button>
              ))}
            </div>
          )}
        </section>

        <section className="flex flex-col gap-2.5">
          <h4 className={sectionTitle}>HR / careers email</h4>
          <form className="flex gap-2" onSubmit={saveContact}>
            <input
              className={field}
              type="email"
              placeholder="careers@company.com"
              value={contact}
              onChange={(e) => setContact(e.target.value)}
            />
            <button className={btn} disabled={savingContact || contact.trim() === (application.contactEmail ?? "")}>
              Save
            </button>
          </form>
          <p className="text-xs text-gray-400">Follow-up emails from your rules are sent to this address.</p>
        </section>

        <section className="flex flex-col gap-2.5">
          <h4 className={sectionTitle}>Emails</h4>
          {emails.length === 0 && (
            <p className="text-sm text-gray-400">None yet. A rule with the “Send email” action will queue them here.</p>
          )}
          {emails.map((m) => (
            <div key={m.id} className="rounded-xl border border-gray-200 bg-gray-50 p-3.5">
              <div className="flex items-start justify-between gap-3">
                <p className="text-sm font-medium text-gray-800">{m.subject}</p>
                <span
                  title={m.lastError ?? undefined}
                  className={"shrink-0 rounded-full px-2 py-0.5 text-xs font-medium " + EMAIL_STATUS[m.status].cls}
                >
                  {EMAIL_STATUS[m.status].text}
                </span>
              </div>
              <p className="mt-0.5 text-xs text-gray-400">
                To {m.toAddress} · {formatDateTime(m.sentAt ?? m.createdAt)}
              </p>
              <details className="mt-2">
                <summary className="cursor-pointer text-xs font-medium text-gray-500">Show message</summary>
                <p className="mt-2 whitespace-pre-wrap text-sm text-gray-600">{m.body}</p>
              </details>
            </div>
          ))}
        </section>

        <section className="flex flex-col gap-2.5">
          <h4 className={sectionTitle}>Checklists</h4>
          {checklists.length === 0 && (
            <p className="text-sm text-gray-400">None yet. A rule can create these automatically.</p>
          )}
          {checklists.map((c) => (
            <div key={c.id} className="rounded-xl border border-gray-200 bg-gray-50 p-3.5">
              <p className="text-sm font-semibold text-gray-800">{c.title}</p>
              <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-gray-600">
                {c.items.map((item, i) => (
                  <li key={i}>{item}</li>
                ))}
              </ul>
            </div>
          ))}
        </section>

        <section className="flex flex-col gap-2.5">
          <h4 className={sectionTitle}>Reminders</h4>
          {reminders.length === 0 && <p className="text-sm text-gray-400">None yet.</p>}
          {reminders.map((r) => (
            <div
              key={r.id}
              className="flex items-center justify-between gap-3 rounded-xl border border-gray-200 bg-gray-50 p-3.5"
            >
              <div>
                <p className={"text-sm " + (r.done ? "text-gray-400 line-through" : "font-medium text-gray-800")}>
                  {r.message}
                </p>
                <p className="mt-0.5 text-xs text-gray-400">Due {formatDate(r.dueAt)}</p>
              </div>
              {!r.done && (
                <button className={btn} onClick={() => complete(r.id)}>
                  Done
                </button>
              )}
            </div>
          ))}
        </section>

        <section className="flex flex-col gap-3">
          <h4 className={sectionTitle}>History</h4>
          <ul className="flex flex-col gap-3">
            {audit.map((log) => (
              <li key={log.id} className="border-l-2 border-gray-200 pl-3">
                <p className="text-sm text-gray-700">{describe(log)}</p>
                <p className="text-xs text-gray-400">
                  {formatDateTime(log.createdAt)} · {log.actor}
                </p>
              </li>
            ))}
          </ul>
        </section>
      </aside>
    </div>
  );
}
