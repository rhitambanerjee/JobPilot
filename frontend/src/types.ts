export const STAGES = [
  "WISHLIST",
  "APPLIED",
  "SCREENING",
  "INTERVIEW",
  "OFFER",
  "ACCEPTED",
  "REJECTED",
  "WITHDRAWN",
  "ARCHIVED",
] as const;

export type Stage = (typeof STAGES)[number];

export interface Application {
  id: string;
  company: string;
  role: string;
  jobUrl: string | null;
  contactEmail: string | null;
  stage: Stage;
  allowedNext: Stage[];
  notes: string | null;
  lastActivityAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface NewApplication {
  company: string;
  role: string;
  jobUrl?: string;
  contactEmail?: string;
  notes?: string;
  stage?: "WISHLIST" | "APPLIED";
}

export interface AuditLog {
  id: string;
  action: string;
  actor: string;
  details: Record<string, unknown>;
  createdAt: string;
}

export interface Checklist {
  id: string;
  title: string;
  items: string[];
  createdAt: string;
}

export interface Reminder {
  id: string;
  applicationId: string;
  message: string;
  dueAt: string;
  done: boolean;
}

export type Operator = "eq" | "in" | "gt" | "gte" | "contains";

export interface Condition {
  field: string;
  op: Operator;
  value: unknown;
}

export interface ActionSpec {
  type: string;
  params: Record<string, unknown>;
}

export interface Rule {
  id: string;
  name: string;
  enabled: boolean;
  trigger: { eventType: string };
  conditions: Condition[];
  actions: ActionSpec[];
  createdAt: string;
}

export interface NewRule {
  name: string;
  trigger: { eventType: string };
  conditions: Condition[];
  actions: ActionSpec[];
  enabled: boolean;
}

export type EmailStatus = "NONE" | "PENDING" | "SENT" | "FAILED";

export interface AppNotification {
  id: string;
  applicationId: string | null;
  title: string;
  message: string;
  readAt: string | null;
  emailTo: string | null;
  emailStatus: EmailStatus;
  emailError: string | null;
  createdAt: string;
}

export interface NotificationSettings {
  email: string | null;
  emailEnabled: boolean;
}

export interface EmailTemplate {
  id: string;
  name: string;
  subject: string;
  body: string;
  builtIn: boolean;
  /** placeholders the user has to fill in (built-in ones are filled automatically) */
  placeholders: string[];
}

export interface TemplateInput {
  name: string;
  subject: string;
  body: string;
}

export interface OutgoingEmail {
  id: string;
  toAddress: string;
  subject: string;
  body: string;
  status: "PENDING" | "SENT" | "FAILED";
  lastError: string | null;
  createdAt: string;
  sentAt: string | null;
}

export interface AuthUser {
  id: string;
  email: string;
  displayName: string | null;
}
