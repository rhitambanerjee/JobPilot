// {{placeholder}} helpers shared by the template editor and the rule form.
// Keep BUILT_IN_PLACEHOLDERS in sync with TemplateRenderer.BUILT_IN on the backend.

export const BUILT_IN_PLACEHOLDERS: { name: string; help: string }[] = [
  { name: "company", help: "Company name" },
  { name: "role", help: "Job title" },
  { name: "stage", help: "Current stage" },
  { name: "daysIdle", help: "Days since the last activity" },
  { name: "appliedDate", help: "Date you added the application" },
  { name: "jobUrl", help: "Link to the job posting" },
];

const BUILT_IN_NAMES = new Set(BUILT_IN_PLACEHOLDERS.map((p) => p.name));

const PLACEHOLDER = /\{\{\s*([A-Za-z][A-Za-z0-9_]*)\s*\}\}/g;

/** Example values so a preview reads like a real email. */
const SAMPLE_VALUES: Record<string, string> = {
  company: "Acme Corp",
  role: "Backend Engineer",
  stage: "Applied",
  daysIdle: "7",
  appliedDate: "1 November 2026",
  jobUrl: "https://acme.example/jobs/123",
};

/** Placeholder names in order of first appearance, without duplicates. */
export function placeholdersIn(...texts: string[]): string[] {
  const found = new Set<string>();
  for (const text of texts) {
    for (const match of text.matchAll(PLACEHOLDER)) found.add(match[1]);
  }
  return [...found];
}

/** The ones the user has to supply. */
export function customPlaceholdersIn(...texts: string[]): string[] {
  return placeholdersIn(...texts).filter((name) => !BUILT_IN_NAMES.has(name));
}

/** Values the user typed win, then sample data for built-ins, otherwise [name] so gaps are obvious. */
export function renderPreview(text: string, values: Record<string, string>): string {
  return text.replace(PLACEHOLDER, (_match, name: string) => {
    const typed = values[name]?.trim();
    return typed ? typed : (SAMPLE_VALUES[name] ?? `[${name}]`);
  });
}
