import type {
  AppNotification,
  AuthUser,
  Application,
  ApplicationDetailsInput,
  AuditLog,
  Checklist,
  EmailTemplate,
  FunnelReport,
  NewApplication,
  NewRule,
  NotificationSettings,
  OutgoingEmail,
  RegisterResult,
  Reminder,
  Rule,
  Stage,
  TemplateInput,
} from "./types";

export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
    /** machine-readable reason from the server, e.g. "EMAIL_NOT_VERIFIED" */
    public code?: string,
  ) {
    super(message);
  }
}

/** Called when the server says the session is gone, so the app can show the login page. */
let onUnauthorized: (() => void) | null = null;
export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler;
}

/** The server sets an XSRF-TOKEN cookie; write requests must echo it back in the X-XSRF-TOKEN header. */
function csrfToken(): string | null {
  const entry = document.cookie.split("; ").find((c) => c.startsWith("XSRF-TOKEN="));
  return entry ? decodeURIComponent(entry.slice("XSRF-TOKEN=".length)) : null;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const method = (init?.method ?? "GET").toUpperCase();
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (method !== "GET" && method !== "HEAD") {
    const token = csrfToken();
    if (token) headers["X-XSRF-TOKEN"] = token;
  }
  const res = await fetch("/api" + path, { ...init, headers, credentials: "same-origin" });
  if (res.status === 401 && !path.startsWith("/auth/")) {
    onUnauthorized?.();
  }
  if (!res.ok) {
    // the backend answers with RFC 7807 problem details: { detail, errors? }
    let message = `${res.status} ${res.statusText}`;
    let code: string | undefined;
    try {
      const problem = await res.json();
      code = problem.code;
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
    throw new ApiError(message, res.status, code);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

const post = <T>(path: string, body?: unknown) =>
  request<T>(path, { method: "POST", body: body === undefined ? undefined : JSON.stringify(body) });

export const api = {
  me: () => request<AuthUser>("/auth/me"),
  login: (email: string, password: string) => post<AuthUser>("/auth/login", { email, password }),
  register: (email: string, password: string, displayName: string | null) =>
    post<RegisterResult>("/auth/register", { email, password, displayName }),
  verifyEmail: (token: string) => post<void>("/auth/verify-email", { token }),
  resendVerification: (email: string) => post<void>("/auth/resend-verification", { email }),
  forgotPassword: (email: string) => post<void>("/auth/forgot-password", { email }),
  resetPassword: (token: string, newPassword: string) => post<void>("/auth/reset-password", { token, newPassword }),
  logout: () => request<void>("/auth/logout", { method: "POST" }),

  listApplications: () => request<Application[]>("/applications"),
  createApplication: (body: NewApplication) => post<Application>("/applications", body),
  updateApplication: (id: string, body: ApplicationDetailsInput) =>
    request<Application>(`/applications/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  funnel: (weeks = 12) => request<FunnelReport>(`/analytics/funnel?weeks=${weeks}`),
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
