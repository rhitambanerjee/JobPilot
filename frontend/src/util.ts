export function daysSince(iso: string): number {
  return Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000);
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { dateStyle: "medium" });
}

export function stageLabel(stage: string): string {
  return stage.charAt(0) + stage.slice(1).toLowerCase();
}

export function errorMessage(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}
