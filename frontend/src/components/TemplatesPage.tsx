import { useCallback, useEffect, useState } from "react";
import { api } from "../api";
import type { EmailTemplate } from "../types";
import { errorMessage } from "../util";
import { BUILT_IN_PLACEHOLDERS, customPlaceholdersIn, renderPreview } from "../placeholders";
import { btn, btnDanger, btnPrimary, card, field, lbl, pill, sectionTitle } from "../ui";

interface Draft {
  id: string | null; // null = a new template
  name: string;
  subject: string;
  body: string;
}

export default function TemplatesPage({ onError }: { onError: (message: string) => void }) {
  const [templates, setTemplates] = useState<EmailTemplate[]>([]);
  const [draft, setDraft] = useState<Draft | null>(null);

  const load = useCallback(async () => {
    try {
      setTemplates(await api.listTemplates());
    } catch (e) {
      onError(errorMessage(e));
    }
  }, [onError]);

  useEffect(() => {
    void load();
  }, [load]);

  async function remove(t: EmailTemplate) {
    if (!window.confirm(`Delete template "${t.name}"?`)) return;
    try {
      await api.deleteTemplate(t.id);
      await load();
    } catch (e) {
      onError(errorMessage(e)); // e.g. "This template is used by rule(s): ..."
    }
  }

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-4 px-6 py-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-xl font-semibold tracking-tight text-gray-900">Email templates</h2>
          <p className="mt-1 text-sm text-gray-500">
            Reusable emails for your rules. Placeholders like <code className="rounded bg-gray-100 px-1">{"{{company}}"}</code>{" "}
            are filled in automatically; any other placeholder is a value you set when you create the rule.
          </p>
        </div>
        <button className={btnPrimary} onClick={() => setDraft({ id: null, name: "", subject: "", body: "" })}>
          + New template
        </button>
      </div>

      {draft && (
        <TemplateForm
          draft={draft}
          onCancel={() => setDraft(null)}
          onSaved={() => {
            setDraft(null);
            void load();
          }}
          onError={onError}
        />
      )}

      {templates.map((t) => (
        <div key={t.id} className={card + " flex flex-col gap-3 p-4"}>
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <p className="font-semibold text-gray-900">{t.name}</p>
                {t.builtIn && <span className={pill}>Built-in</span>}
              </div>
              <p className="mt-0.5 text-sm text-gray-500">Subject: {t.subject}</p>
            </div>
            <div className="flex shrink-0 gap-2">
              <button
                className={btn}
                onClick={() => setDraft({ id: null, name: t.builtIn ? `${t.name} (copy)` : `${t.name} copy`, subject: t.subject, body: t.body })}
              >
                {t.builtIn ? "Use as starting point" : "Duplicate"}
              </button>
              {!t.builtIn && (
                <>
                  <button className={btn} onClick={() => setDraft({ id: t.id, name: t.name, subject: t.subject, body: t.body })}>
                    Edit
                  </button>
                  <button className={btnDanger} onClick={() => void remove(t)}>
                    Delete
                  </button>
                </>
              )}
            </div>
          </div>

          {t.placeholders.length > 0 && (
            <p className="text-xs text-gray-500">
              You fill in:{" "}
              {t.placeholders.map((p) => (
                <span key={p} className={pill + " mr-1"}>
                  {p}
                </span>
              ))}
            </p>
          )}

          <details>
            <summary className="cursor-pointer text-xs font-medium text-gray-500">Preview</summary>
            <p className="mt-2 text-sm font-medium text-gray-800">{renderPreview(t.subject, {})}</p>
            <p className="mt-1 whitespace-pre-wrap rounded-xl bg-gray-50 p-3 text-sm text-gray-600">
              {renderPreview(t.body, {})}
            </p>
          </details>
        </div>
      ))}
    </div>
  );
}

function TemplateForm({
  draft,
  onSaved,
  onCancel,
  onError,
}: {
  draft: Draft;
  onSaved: () => void;
  onCancel: () => void;
  onError: (message: string) => void;
}) {
  const [name, setName] = useState(draft.name);
  const [subject, setSubject] = useState(draft.subject);
  const [body, setBody] = useState(draft.body);
  const [saving, setSaving] = useState(false);

  const custom = customPlaceholdersIn(subject, body);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    const input = { name: name.trim(), subject: subject.trim(), body };
    try {
      if (draft.id) await api.updateTemplate(draft.id, input);
      else await api.createTemplate(input);
      onSaved();
    } catch (err) {
      onError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={submit} className={card + " flex flex-col gap-4 p-5"}>
      <h3 className="text-sm font-semibold text-gray-900">{draft.id ? "Edit template" : "New template"}</h3>

      <label className={lbl}>
        Template name
        <input className={field} value={name} onChange={(e) => setName(e.target.value)} required placeholder="My follow-up" />
      </label>
      <label className={lbl}>
        Subject
        <input className={field} value={subject} onChange={(e) => setSubject(e.target.value)} required />
      </label>
      <label className={lbl}>
        Body
        <textarea className={field + " font-mono"} rows={10} value={body} onChange={(e) => setBody(e.target.value)} required />
      </label>

      <div className="flex flex-col gap-2">
        <h4 className={sectionTitle}>Filled in automatically (click to insert)</h4>
        <div className="flex flex-wrap gap-1.5">
          {BUILT_IN_PLACEHOLDERS.map((p) => (
            <button
              key={p.name}
              type="button"
              title={p.help}
              className={btn + " !px-2 !py-0.5 text-xs"}
              onClick={() => setBody((b) => `${b}{{${p.name}}}`)}
            >
              {`{{${p.name}}}`}
            </button>
          ))}
        </div>
        <p className="text-xs text-gray-400">
          Anything else you type as {"{{yourName}}"} becomes a value you fill in when you use the template in a rule.
          {custom.length > 0 && <> This template asks for: <b>{custom.join(", ")}</b>.</>}
        </p>
      </div>

      <div className="rounded-xl bg-gray-50 p-4">
        <h4 className={sectionTitle}>Preview with sample data</h4>
        <p className="mt-2 text-sm font-medium text-gray-800">{renderPreview(subject, {})}</p>
        <p className="mt-1 whitespace-pre-wrap text-sm text-gray-600">{renderPreview(body, {})}</p>
      </div>

      <div className="flex justify-end gap-2">
        <button type="button" className={btn} onClick={onCancel}>
          Cancel
        </button>
        <button className={btnPrimary} disabled={saving || !name.trim() || !subject.trim() || !body.trim()}>
          {saving ? "Saving…" : draft.id ? "Save changes" : "Save template"}
        </button>
      </div>
    </form>
  );
}
