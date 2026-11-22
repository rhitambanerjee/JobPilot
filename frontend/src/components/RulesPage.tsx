import { useCallback, useEffect, useState } from "react";
import { api } from "../api";
import type { ActionSpec, Condition, EmailTemplate, NewRule, Operator, Rule } from "../types";
import { errorMessage } from "../util";
import { renderPreview } from "../placeholders";
import { btn, btnDanger, btnPrimary, card, field, lbl, pill, sectionTitle } from "../ui";

const EVENTS: Record<string, { label: string; fields: string[] }> = {
  "application.stage_changed": { label: "Stage changed", fields: ["toStage", "fromStage", "company", "role"] },
  "application.created": { label: "Application created", fields: ["stage", "company", "role"] },
  "application.stale": { label: "Application went stale", fields: ["daysIdle", "stage", "company", "role"] },
};

const OPERATORS: { value: Operator; label: string; hint: string }[] = [
  { value: "eq", label: "equals", hint: "INTERVIEW" },
  { value: "in", label: "is one of", hint: "OFFER, ACCEPTED" },
  { value: "gt", label: "greater than", hint: "14" },
  { value: "gte", label: "at least", hint: "7" },
  { value: "contains", label: "contains", hint: "backend" },
];

const ACTION_TYPES = [
  { value: "create_checklist", label: "Create checklist" },
  { value: "create_reminder", label: "Create reminder" },
  { value: "send_email", label: "Send email to HR / careers" },
  { value: "archive_application", label: "Archive application" },
];

interface CondRow {
  field: string;
  op: Operator;
  value: string;
}

interface ActionRow {
  type: string;
  title: string;
  items: string; // one per line
  message: string;
  daysFromNow: string;
  templateId: string;
  placeholders: Record<string, string>;
}

const emptyAction = (): ActionRow => ({
  type: "create_reminder",
  title: "",
  items: "",
  message: "",
  daysFromNow: "3",
  templateId: "",
  placeholders: {},
});

function toCondition(row: CondRow): Condition {
  let value: unknown = row.value.trim();
  if (row.op === "gt" || row.op === "gte") value = Number(row.value);
  if (row.op === "in") {
    value = row.value
      .split(",")
      .map((v) => v.trim())
      .filter(Boolean);
  }
  return { field: row.field.trim(), op: row.op, value };
}

function toAction(row: ActionRow, templates: EmailTemplate[]): ActionSpec {
  if (row.type === "create_checklist") {
    const items = row.items
      .split("\n")
      .map((i) => i.trim())
      .filter(Boolean);
    return {
      type: row.type,
      params: { ...(row.title.trim() && { title: row.title.trim() }), ...(items.length && { items }) },
    };
  }
  if (row.type === "create_reminder") {
    return {
      type: row.type,
      params: {
        ...(row.message.trim() && { message: row.message.trim() }),
        daysFromNow: Number(row.daysFromNow || 3),
      },
    };
  }
  if (row.type === "send_email") {
    const template = templates.find((t) => t.id === row.templateId);
    const placeholders = Object.fromEntries(
      (template?.placeholders ?? []).map((name) => [name, (row.placeholders[name] ?? "").trim()]),
    );
    return { type: row.type, params: { templateId: row.templateId, placeholders } };
  }
  return { type: row.type, params: {} };
}

/** What is still missing in an email action, so we can say so before saving. */
function emailProblem(row: ActionRow, templates: EmailTemplate[]): string | null {
  if (row.type !== "send_email") return null;
  const template = templates.find((t) => t.id === row.templateId);
  if (!template) return "Choose an email template";
  const missing = template.placeholders.filter((name) => !(row.placeholders[name] ?? "").trim());
  return missing.length ? `Fill in ${missing.join(", ")} for the email action` : null;
}

function summarize(rule: Rule, templates: EmailTemplate[]): string {
  const when = EVENTS[rule.trigger.eventType]?.label ?? rule.trigger.eventType;
  const ifs = rule.conditions.map((c) => `${c.field} ${c.op} ${JSON.stringify(c.value)}`).join(" and ");
  const thens = rule.actions
    .map((a) => {
      if (a.type !== "send_email") return a.type.replace(/_/g, " ");
      const name = templates.find((t) => t.id === a.params.templateId)?.name;
      return name ? `send email "${name}"` : "send email";
    })
    .join(", ");
  return `When "${when}"${ifs ? ` and ${ifs}` : ""} → ${thens}`;
}

export default function RulesPage({ onError }: { onError: (message: string) => void }) {
  const [rules, setRules] = useState<Rule[]>([]);
  const [templates, setTemplates] = useState<EmailTemplate[]>([]);
  const [creating, setCreating] = useState(false);

  const load = useCallback(async () => {
    try {
      const [r, t] = await Promise.all([api.listRules(), api.listTemplates()]);
      setRules(r);
      setTemplates(t);
    } catch (e) {
      onError(errorMessage(e));
    }
  }, [onError]);

  useEffect(() => {
    void load();
  }, [load]);

  async function toggle(rule: Rule) {
    try {
      const updated = await api.setRuleEnabled(rule.id, !rule.enabled);
      setRules((rs) => rs.map((r) => (r.id === rule.id ? updated : r)));
    } catch (e) {
      onError(errorMessage(e));
    }
  }

  async function remove(rule: Rule) {
    if (!window.confirm(`Delete rule "${rule.name}"?`)) return;
    try {
      await api.deleteRule(rule.id);
      setRules((rs) => rs.filter((r) => r.id !== rule.id));
    } catch (e) {
      onError(errorMessage(e));
    }
  }

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-4 px-6 py-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-xl font-semibold tracking-tight text-gray-900">Rules</h2>
          <p className="mt-1 text-sm text-gray-500">
            When something happens, matching rules queue actions and a background worker runs them.
          </p>
        </div>
        <button className={btnPrimary} onClick={() => setCreating((v) => !v)}>
          {creating ? "Close form" : "+ New rule"}
        </button>
      </div>

      {creating && (
        <RuleForm
          templates={templates}
          onError={onError}
          onCreated={(rule) => {
            setRules((rs) => [rule, ...rs]);
            setCreating(false);
          }}
        />
      )}

      {rules.length === 0 && !creating && (
        <div className="rounded-2xl border border-dashed border-gray-300 bg-white px-6 py-12 text-center">
          <p className="text-sm font-medium text-gray-600">No rules yet</p>
          <p className="mx-auto mt-1 max-w-md text-sm text-gray-400">
            Checklists, reminders and archiving need no setup. To email HR automatically you also need an application
            with a contact email saved on it. The built-in email templates are ready to use.
          </p>
          <button className={btnPrimary + " mt-4"} onClick={() => setCreating(true)}>
            Create your first rule
          </button>
        </div>
      )}

      {rules.map((rule) => (
        <div
          key={rule.id}
          className={card + " flex items-center justify-between gap-4 p-4 " + (rule.enabled ? "" : "opacity-60")}
        >
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <p className="font-semibold text-gray-900">{rule.name}</p>
              <span className={pill}>{rule.enabled ? "Active" : "Paused"}</span>
            </div>
            <p className="mt-1 text-sm text-gray-500">{summarize(rule, templates)}</p>
          </div>
          <div className="flex shrink-0 gap-2">
            <button className={btn} onClick={() => toggle(rule)}>
              {rule.enabled ? "Pause" : "Enable"}
            </button>
            <button className={btnDanger} onClick={() => remove(rule)}>
              Delete
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}

function RuleForm({
  templates,
  onCreated,
  onError,
}: {
  templates: EmailTemplate[];
  onCreated: (r: Rule) => void;
  onError: (m: string) => void;
}) {
  const [name, setName] = useState("");
  const [eventType, setEventType] = useState("application.stage_changed");
  const [conditions, setConditions] = useState<CondRow[]>([]);
  const [actions, setActions] = useState<ActionRow[]>([emptyAction()]);
  const [saving, setSaving] = useState(false);

  const fields = EVENTS[eventType].fields;

  const patchCond = (i: number, patch: Partial<CondRow>) =>
    setConditions((rows) => rows.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));
  const patchAction = (i: number, patch: Partial<ActionRow>) =>
    setActions((rows) => rows.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));

  /** The common case: Applied for a week with no news -> email HR a follow-up. */
  function useFollowUpExample() {
    setName("Follow up after 7 days");
    setEventType("application.stale");
    setConditions([
      { field: "stage", op: "eq", value: "APPLIED" },
      { field: "daysIdle", op: "gte", value: "7" },
    ]);
    setActions([{ ...emptyAction(), type: "send_email", templateId: templates[0]?.id ?? "" }]);
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    for (const row of actions) {
      const problem = emailProblem(row, templates);
      if (problem) {
        onError(problem);
        return;
      }
    }
    setSaving(true);
    const body: NewRule = {
      name: name.trim(),
      trigger: { eventType },
      conditions: conditions.map(toCondition),
      actions: actions.map((a) => toAction(a, templates)),
      enabled: true,
    };
    try {
      onCreated(await api.createRule(body));
    } catch (err) {
      onError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className={card + " flex flex-col gap-5 p-5"} onSubmit={submit}>
      {templates.length > 0 && (
        <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-gray-50 px-4 py-3">
          <p className="text-sm text-gray-600">Want to email HR when an application sits in Applied for a week?</p>
          <button type="button" className={btn} onClick={useFollowUpExample}>
            Use the 7-day follow-up example
          </button>
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-2">
        <label className={lbl}>
          Rule name
          <input className={field} value={name} onChange={(e) => setName(e.target.value)} required placeholder="Interview prep" />
        </label>
        <label className={lbl}>
          When
          <select
            className={field}
            value={eventType}
            onChange={(e) => {
              setEventType(e.target.value);
              setConditions([]); // fields differ per event, so old conditions would be meaningless
            }}
          >
            {Object.entries(EVENTS).map(([value, { label }]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
      </div>

      <section className="flex flex-col gap-3 rounded-xl bg-gray-50 p-4">
        <h4 className={sectionTitle}>Only if (all must match)</h4>
        {conditions.map((c, i) => (
          <div className="flex flex-wrap items-center gap-2" key={i}>
            <select className={field + " sm:w-40"} value={c.field} onChange={(e) => patchCond(i, { field: e.target.value })}>
              {fields.map((f) => (
                <option key={f}>{f}</option>
              ))}
            </select>
            <select
              className={field + " sm:w-36"}
              value={c.op}
              onChange={(e) => patchCond(i, { op: e.target.value as Operator })}
            >
              {OPERATORS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
            <input
              className={field + " min-w-0 flex-1"}
              value={c.value}
              required
              placeholder={OPERATORS.find((o) => o.value === c.op)?.hint}
              onChange={(e) => patchCond(i, { value: e.target.value })}
            />
            <button type="button" className={btn} onClick={() => setConditions((rows) => rows.filter((_, idx) => idx !== i))}>
              ✕
            </button>
          </div>
        ))}
        <div>
          <button
            type="button"
            className={btn}
            onClick={() => setConditions((rows) => [...rows, { field: fields[0], op: "eq", value: "" }])}
          >
            + Add condition
          </button>
        </div>
      </section>

      <section className="flex flex-col gap-3 rounded-xl bg-gray-50 p-4">
        <h4 className={sectionTitle}>Then</h4>
        {actions.map((a, i) => (
          <div className="flex flex-col gap-3 rounded-xl border border-gray-200 bg-white p-4" key={i}>
            <div className="flex items-center gap-2">
              <select
                className={field + " sm:w-64"}
                value={a.type}
                onChange={(e) =>
                  patchAction(i, {
                    type: e.target.value,
                    ...(e.target.value === "send_email" && !a.templateId ? { templateId: templates[0]?.id ?? "" } : {}),
                  })
                }
              >
                {ACTION_TYPES.map((t) => (
                  <option key={t.value} value={t.value}>
                    {t.label}
                  </option>
                ))}
              </select>
              <div className="flex-1" />
              {actions.length > 1 && (
                <button type="button" className={btn} onClick={() => setActions((rows) => rows.filter((_, idx) => idx !== i))}>
                  ✕
                </button>
              )}
            </div>

            {a.type === "create_checklist" && (
              <>
                <label className={lbl}>
                  Title
                  <input className={field} value={a.title} onChange={(e) => patchAction(i, { title: e.target.value })} placeholder="Interview prep" />
                </label>
                <label className={lbl}>
                  Items (one per line, optional)
                  <textarea className={field} rows={3} value={a.items} onChange={(e) => patchAction(i, { items: e.target.value })} />
                </label>
              </>
            )}

            {a.type === "create_reminder" && (
              <div className="grid gap-3 sm:grid-cols-[1fr_9rem]">
                <label className={lbl}>
                  Message
                  <input className={field} value={a.message} onChange={(e) => patchAction(i, { message: e.target.value })} placeholder="Follow up" />
                </label>
                <label className={lbl}>
                  Days from now
                  <input
                    className={field}
                    type="number"
                    min={0}
                    value={a.daysFromNow}
                    onChange={(e) => patchAction(i, { daysFromNow: e.target.value })}
                  />
                </label>
              </div>
            )}

            {a.type === "send_email" && (
              <SendEmailFields row={a} templates={templates} onChange={(patch) => patchAction(i, patch)} />
            )}

            {a.type === "archive_application" && (
              <p className="text-sm text-gray-400">Moves the application to the Archive tab.</p>
            )}
          </div>
        ))}
        <div>
          <button type="button" className={btn} onClick={() => setActions((rows) => [...rows, emptyAction()])}>
            + Add action
          </button>
        </div>
      </section>

      <div className="flex justify-end">
        <button className={btnPrimary} disabled={saving || !name.trim()}>
          {saving ? "Saving…" : "Create rule"}
        </button>
      </div>
    </form>
  );
}

function SendEmailFields({
  row,
  templates,
  onChange,
}: {
  row: ActionRow;
  templates: EmailTemplate[];
  onChange: (patch: Partial<ActionRow>) => void;
}) {
  const template = templates.find((t) => t.id === row.templateId);

  return (
    <div className="flex flex-col gap-3">
      <label className={lbl}>
        Template
        <select className={field} value={row.templateId} onChange={(e) => onChange({ templateId: e.target.value })}>
          {templates.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
              {t.builtIn ? "" : " (mine)"}
            </option>
          ))}
        </select>
      </label>

      {template && template.placeholders.length > 0 && (
        <div className="grid gap-3 sm:grid-cols-2">
          {template.placeholders.map((name) => (
            <label key={name} className={lbl}>
              {name}
              <input
                className={field}
                value={row.placeholders[name] ?? ""}
                onChange={(e) => onChange({ placeholders: { ...row.placeholders, [name]: e.target.value } })}
                required
              />
            </label>
          ))}
        </div>
      )}

      {template && (
        <div className="rounded-xl bg-gray-50 p-3">
          <p className="text-xs font-semibold uppercase tracking-wide text-gray-400">Preview (sample data)</p>
          <p className="mt-1.5 text-sm font-medium text-gray-800">{renderPreview(template.subject, row.placeholders)}</p>
          <p className="mt-1 whitespace-pre-wrap text-sm text-gray-600">{renderPreview(template.body, row.placeholders)}</p>
        </div>
      )}

      <p className="text-xs text-gray-400">
        Sent to the HR / careers email saved on each application. Add it on the application's detail panel. If it's
        missing, you'll get a notification and the email goes out once you add it.
      </p>
    </div>
  );
}
