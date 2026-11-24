import { useCallback, useEffect, useState } from "react";
import { api } from "../api";
import type { FunnelReport } from "../types";
import { errorMessage, stageLabel } from "../util";
import { btn, btnPrimary, card, sectionTitle } from "../ui";

const BAR_SHADES = ["bg-gray-900", "bg-gray-700", "bg-gray-600", "bg-gray-500", "bg-gray-400"];

const pct = (v: number | null) => (v === null ? "–" : `${v}%`);
const days = (v: number | null) => (v === null ? "–" : v === 1 ? "1 day" : `${v} days`);

function Stat({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className={card + " flex flex-col gap-1 p-4"}>
      <p className="text-xs font-medium text-gray-500">{label}</p>
      <p className="text-2xl font-semibold tracking-tight text-gray-900">{value}</p>
      {hint && <p className="text-xs text-gray-400">{hint}</p>}
    </div>
  );
}

function weekLabel(isoDate: string) {
  return new Date(isoDate + "T00:00:00").toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

export default function AnalyticsPage() {
  const [report, setReport] = useState<FunnelReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [failure, setFailure] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setReport(await api.funnel(12));
      setFailure(null);
    } catch (e) {
      setFailure(errorMessage(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading && !report) {
    return <p className="p-8 text-sm text-gray-500">Loading…</p>;
  }

  if (failure && !report) {
    return (
      <div
        role="alert"
        className="mx-auto mt-12 flex max-w-lg flex-col items-center gap-3 rounded-2xl border border-red-100 bg-white px-8 py-12 text-center shadow-sm"
      >
        <h2 className="text-lg font-semibold tracking-tight text-gray-900">Couldn't load your insights</h2>
        <p className="text-sm text-gray-500">{failure}</p>
        <button className={btnPrimary} onClick={() => void load()}>
          Try again
        </button>
      </div>
    );
  }

  if (!report) return null;

  const [, , interview, offer] = report.funnel;
  const rejectedTotal = report.rejectedAfter.reduce((sum, r) => sum + r.count, 0);
  const maxWeek = Math.max(1, ...report.weekly.map((w) => w.count));
  const maxRejected = Math.max(1, ...report.rejectedAfter.map((r) => r.count));

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6 px-4 py-8 sm:px-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-xl font-semibold tracking-tight text-gray-900">Insights</h2>
          <p className="mt-1 text-sm text-gray-500">How your applications move through the pipeline.</p>
        </div>
        <button className={btn} onClick={() => void load()} disabled={loading}>
          {loading ? "Refreshing…" : "Refresh"}
        </button>
      </div>

      {failure && (
        <p role="status" className="rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800">
          Couldn't refresh ({failure}). Showing the last numbers that loaded.
        </p>
      )}

      {report.applied === 0 ? (
        <div className="rounded-2xl border border-dashed border-gray-300 bg-white px-6 py-14 text-center">
          <p className="text-sm font-medium text-gray-600">Nothing to chart yet</p>
          <p className="mx-auto mt-1 max-w-md text-sm text-gray-400">
            Add applications and move them through the stages on the Board. Your funnel, response rate and timings
            appear here once you have applied somewhere.
            {report.wishlist > 0 && ` (${report.wishlist} on your wishlist so far.)`}
          </p>
        </div>
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Stat
              label="Applications sent"
              value={String(report.applied)}
              hint={report.wishlist > 0 ? `${report.wishlist} more on your wishlist` : undefined}
            />
            <Stat
              label="Response rate"
              value={pct(report.responseRatePercent)}
              hint={`${report.responded} of ${report.applied} heard back`}
            />
            <Stat label="Typical wait for a reply" value={days(report.medianDaysToFirstResponse)} hint="median" />
            <Stat label="Interviews · Offers" value={`${interview.count} · ${offer.count}`} />
          </div>

          <section className={card + " flex flex-col gap-4 p-5"} aria-label="Application funnel">
            <h3 className={sectionTitle}>Funnel</h3>
            <ul className="flex flex-col gap-4">
              {report.funnel.map((step, i) => (
                <li key={step.stage} className="flex flex-col gap-1.5">
                  <div className="flex flex-wrap items-baseline justify-between gap-x-4">
                    <span className="text-sm font-medium text-gray-800">{stageLabel(step.stage)}</span>
                    <span className="text-sm text-gray-500">
                      <b className="font-semibold text-gray-900">{step.count}</b>
                      {step.percentOfApplied !== null && ` · ${step.percentOfApplied}% of applied`}
                    </span>
                  </div>
                  <div className="h-3 overflow-hidden rounded-full bg-gray-100">
                    <div
                      className={"h-full rounded-full " + BAR_SHADES[i]}
                      style={{ width: `${report.applied === 0 ? 0 : (step.count / report.applied) * 100}%` }}
                    />
                  </div>
                  {i > 0 && (
                    <p className="text-xs text-gray-400">
                      {step.conversionFromPrevious !== null
                        ? `${step.conversionFromPrevious}% of those who reached ${stageLabel(report.funnel[i - 1].stage)}`
                        : `Nobody has reached ${stageLabel(report.funnel[i - 1].stage)} yet`}
                      {step.medianDaysFromApplied !== null && ` · typically ${days(step.medianDaysFromApplied)} after applying`}
                    </p>
                  )}
                </li>
              ))}
            </ul>
          </section>

          <div className="grid gap-6 md:grid-cols-2">
            <section className={card + " flex flex-col gap-3 p-5"} aria-label="Where rejections happened">
              <h3 className={sectionTitle}>Where rejections happened</h3>
              {rejectedTotal === 0 ? (
                <p className="text-sm text-gray-400">No rejections recorded. Good news, or nothing has come back yet.</p>
              ) : (
                <ul className="flex flex-col gap-3">
                  {report.rejectedAfter.map((r) => (
                    <li key={r.stage} className="flex items-center gap-3 text-sm">
                      <span className="w-24 shrink-0 text-gray-600">After {stageLabel(r.stage)}</span>
                      <div className="h-2.5 flex-1 overflow-hidden rounded-full bg-gray-100">
                        <div className="h-full rounded-full bg-gray-500" style={{ width: `${(r.count / maxRejected) * 100}%` }} />
                      </div>
                      <span className="w-6 text-right font-medium text-gray-800">{r.count}</span>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className={card + " flex flex-col gap-3 p-5"} aria-label="Where everything is right now">
              <h3 className={sectionTitle}>Right now</h3>
              <ul className="flex flex-wrap gap-2">
                {report.currentByStage
                  .filter((c) => c.count > 0)
                  .map((c) => (
                    <li key={c.stage} className="rounded-full bg-gray-100 px-3 py-1 text-sm text-gray-700">
                      {stageLabel(c.stage)} <b className="font-semibold text-gray-900">{c.count}</b>
                    </li>
                  ))}
              </ul>
            </section>
          </div>

          <section className={card + " flex flex-col gap-4 p-5"} aria-label="Applications sent per week">
            <h3 className={sectionTitle}>Applications sent per week</h3>
            <ul className="flex h-40 items-end gap-2">
              {report.weekly.map((w) => (
                <li
                  key={w.weekStart}
                  className="flex h-full min-w-0 flex-1 flex-col items-center justify-end gap-1"
                  title={`Week of ${weekLabel(w.weekStart)}: ${w.count}`}
                >
                  <span className="text-xs text-gray-500">{w.count > 0 ? w.count : ""}</span>
                  <div
                    className="w-full rounded-t bg-gray-800"
                    style={{ height: w.count > 0 ? Math.max(4, Math.round((w.count / maxWeek) * 96)) : 0 }}
                  />
                  <span className="truncate text-[10px] text-gray-400">{weekLabel(w.weekStart)}</span>
                </li>
              ))}
            </ul>
          </section>

          <details className="text-sm text-gray-500">
            <summary className="cursor-pointer font-medium text-gray-600">How these numbers are worked out</summary>
            <ul className="mt-2 list-disc space-y-1 pl-5">
              <li>An application "reached" a stage if it was ever in it, even if it moved on or was archived later.</li>
              <li>Reaching a later stage also counts for earlier ones, so skipping Screening still counts as passing it.</li>
              <li>"Heard back" means it reached Screening or later, or was rejected after you applied.</li>
              <li>Times are medians, so one very slow reply can't skew them. Weeks start on Monday (UTC).</li>
              <li>Wishlist items aren't counted until you apply.</li>
            </ul>
          </details>
        </>
      )}
    </div>
  );
}
