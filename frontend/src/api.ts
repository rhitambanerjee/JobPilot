import type {
  AppNotification,
  Application,
  AuditLog,
  Checklist,
  EmailTemplate,
  NewApplication,
  NewRule,
  NotificationSettings,
  OutgoingEmail,
  Reminder,
  Rule,
  Stage,
  TemplateInput,
} from "./types";

export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
  ) {
    super(message);
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch("/api" + path, {
    headers: { "Content-Type": "application/json" },
    ...init,
  });
  if (!res.ok) {
    // the backend answers with RFC 7807 problem details: { detail, errors? }
    let message = `${res.status} ${res.statusText}`;
    try {
      const problem = await res.json();
      if (problem.detail) message = problem.detail;
      if (problem.errors) {
        message +=
          " — " +
          Object.entries(problem.errors)
            .map(([field, why]) => `${field}: ${why}`)
            .join(", ");
      }
    } catch {
      /* body wasn't JSON; keep the status line */
    }
    throw new ApiError(message, res.status);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

const post = <T>(path: string, body?: unknown) =>
  request<T>(path, { method: "POST", body: body === undefined ? undefined : JSON.stringify(body) });

export const api = {
  listApplications: () => request<Application[]>("/applications"),
  createApplication: (body: NewApplication) => post<Application>("/applications", body),
  changeStage: (id: string, stage: Stage) => post<Application>(`/applications/${id}/stage`, { stage }),

  audit: (applicationId: string) => request<AuditLog[]>(`/audit?entityId=${applicationId}`),
  checklists: (applicationId: string) => request<Checklist[]>(`/checklists?applicationId=${applicationId}`),
  remindersFor: (applicationId: string) => request<Reminder[]>(`/reminders?applicationId=${applicationId}`),

  openReminders: (dueOnly: boolean) => request<Reminder[]>(`/reminders?due=${dueOnly}`),
  completeReminder: (id: string) => post<Reminder>(`/reminders/${id}/complete`),

  updateContactEmail: (id: string, contactEmail: string | null) =>
    request<Application>(`/applications/${id}/contact-email`, {
      method: "PUT",
      body: JSON.stringify({ contactEmail }),
    }),
  outgoingEmails: (applicationId: string) =>
    request<OutgoingEmail[]>(`/outgoing-emails?applicationId=${applicationId}`),

  listTemplates: () => request<EmailTemplate[]>("/email-templates"),
  createTemplate: (body: TemplateInput) => post<EmailTemplate>("/email-templates", body),
  updateTemplate: (id: string, body: TemplateInput) =>
    request<EmailTemplate>(`/email-templates/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  deleteTemplate: (id: string) => request<void>(`/email-templates/${id}`, { method: "DELETE" }),

  notifications: () => request<AppNotification[]>("/notifications"),
  markNotificationRead: (id: string) => post<AppNotification>(`/notifications/${id}/read`),
  markAllNotificationsRead: () => request<void>("/notifications/read-all", { method: "POST" }),
  getNotificationSettings: () => request<NotificationSettings>("/notification-settings"),
  saveNotificationSettings: (body: NotificationSettings) =>
    request<NotificationSettings>("/notification-settings", { method: "PUT", body: JSON.stringify(body) }),
  sendTestNotification: () => post<AppNotification>("/notification-settings/test"),

  listRules: () => request<Rule[]>("/rules"),
  createRule: (body: NewRule) => post<Rule>("/rules", body),
  setRuleEnabled: (id: string, enabled: boolean) => post<Rule>(`/rules/${id}/${enabled ? "enable" : "disable"}`),
  deleteRule: (id: string) => request<void>(`/rules/${id}`, { method: "DELETE" }),
};
