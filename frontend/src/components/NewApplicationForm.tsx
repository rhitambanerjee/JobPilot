import { useState } from "react";
import { api } from "../api";
import type { Application } from "../types";
import { errorMessage } from "../util";
import { btn, btnPrimary, field, lbl } from "../ui";
import { useDialog } from "../useDialog";

interface Props {
  onClose: () => void;
  onCreated: (a: Application) => void;
  onError: (message: string) => void;
}

export default function NewApplicationForm({ onClose, onCreated, onError }: Props) {
  const [company, setCompany] = useState("");
  const [role, setRole] = useState("");
  const [jobUrl, setJobUrl] = useState("");
  const [contactEmail, setContactEmail] = useState("");
  const [notes, setNotes] = useState("");
  const [stage, setStage] = useState<"APPLIED" | "WISHLIST">("APPLIED");
  const [saving, setSaving] = useState(false);
  const dialogRef = useDialog<HTMLFormElement>(onClose);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    try {
      const created = await api.createApplication({
        company: company.trim(),
        role: role.trim(),
        jobUrl: jobUrl.trim() || undefined,
        contactEmail: contactEmail.trim() || undefined,
        notes: notes.trim() || undefined,
        stage,
      });
      onCreated(created);
    } catch (err) {
      onError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div
      className="fixed inset-0 z-30 flex items-center justify-center bg-gray-900/30 p-4 backdrop-blur-[2px]"
      onClick={onClose}
    >
      <form
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="new-application-title"
        onClick={(e) => e.stopPropagation()}
        onSubmit={submit}
        className="flex w-full max-w-md flex-col gap-4 rounded-2xl bg-white p-6 shadow-xl"
      >
        <h2 id="new-application-title" className="text-lg font-semibold tracking-tight text-gray-900">
          New application
        </h2>

        <label className={lbl}>
          Company
          <input className={field} value={company} onChange={(e) => setCompany(e.target.value)} required autoFocus />
        </label>
        <label className={lbl}>
          Role
          <input className={field} value={role} onChange={(e) => setRole(e.target.value)} required />
        </label>
        <label className={lbl}>
          Job URL
          <input
            className={field}
            type="url"
            value={jobUrl}
            onChange={(e) => setJobUrl(e.target.value)}
            placeholder="https://…"
          />
        </label>
        <label className={lbl}>
          HR / careers email (optional)
          <input
            className={field}
            type="email"
            value={contactEmail}
            onChange={(e) => setContactEmail(e.target.value)}
            placeholder="careers@company.com"
          />
        </label>
        <label className={lbl}>
          Starting stage
          <select
            className={field}
            value={stage}
            onChange={(e) => setStage(e.target.value as "APPLIED" | "WISHLIST")}
          >
            <option value="APPLIED">Applied</option>
            <option value="WISHLIST">Wishlist</option>
          </select>
        </label>
        <label className={lbl}>
          Notes
          <textarea className={field} rows={3} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </label>

        <div className="flex justify-end gap-2 pt-1">
          <button type="button" className={btn} onClick={onClose}>
            Cancel
          </button>
          <button className={btnPrimary} disabled={saving || !company.trim() || !role.trim()}>
            {saving ? "Saving…" : "Create"}
          </button>
        </div>
      </form>
    </div>
  );
}
