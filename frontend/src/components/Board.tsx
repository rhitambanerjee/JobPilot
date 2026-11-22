import { useState } from "react";
import type { Application, Stage } from "../types";
import { STAGES } from "../types";
import { daysSince, stageLabel } from "../util";
import { btnPrimary } from "../ui";

interface Props {
  applications: Application[];
  hasArchived: boolean;
  onOpen: (id: string) => void;
  onMove: (id: string, stage: Stage) => void;
  onAdd: () => void;
}

// Archived applications live in the Archive tab, not on the board.
const BOARD_STAGES = STAGES.filter((s) => s !== "ARCHIVED");

function Welcome({ hasArchived, onAdd }: { hasArchived: boolean; onAdd: () => void }) {
  return (
    <div className="mx-auto mt-12 flex max-w-lg flex-col items-center gap-4 rounded-2xl border border-gray-200 bg-white px-8 py-12 text-center shadow-sm sm:mt-16">
      <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-gray-900 text-xl font-bold text-white">J</div>
      <h2 className="text-lg font-semibold tracking-tight text-gray-900">
        {hasArchived ? "Nothing active right now" : "Welcome to Job Tracker"}
      </h2>
      <p className="text-sm text-gray-500">
        {hasArchived
          ? "Add a new application, or look in the Archive tab for ones you've closed out."
          : "Track every application from wishlist to offer. Add your first one, then use Rules to automate checklists, reminders and follow-up emails."}
      </p>
      <button className={btnPrimary} onClick={onAdd}>
        {hasArchived ? "Add an application" : "Add your first application"}
      </button>
    </div>
  );
}

export default function Board({ applications, hasArchived, onOpen, onMove, onAdd }: Props) {
  const [dragOver, setDragOver] = useState<Stage | null>(null);
  const [dragging, setDragging] = useState(false);

  if (applications.length === 0) {
    return <Welcome hasArchived={hasArchived} onAdd={onAdd} />;
  }

  return (
    <div className="flex h-full items-stretch gap-4 overflow-x-auto px-4 py-6 sm:px-6">
      {BOARD_STAGES.map((stage) => {
        const items = applications.filter((a) => a.stage === stage);
        return (
          <section
            key={stage}
            aria-label={`${stageLabel(stage)}, ${items.length} application${items.length === 1 ? "" : "s"}`}
            className={
              "flex w-64 min-h-40 self-start shrink-0 flex-col gap-3 overflow-y-auto rounded-2xl border bg-gray-50 p-3 transition " +
              (dragOver === stage ? "border-gray-400 ring-2 ring-gray-300" : "border-gray-200")
            }
            onDragOver={(e) => {
              e.preventDefault();
              setDragOver(stage);
            }}
            onDragLeave={() => setDragOver(null)}
            onDrop={(e) => {
              e.preventDefault();
              setDragOver(null);
              setDragging(false);
              const id = e.dataTransfer.getData("text/plain");
              const dragged = applications.find((a) => a.id === id);
              // the server enforces the state machine; we just ask and show its answer
              if (dragged && dragged.stage !== stage) onMove(id, stage);
            }}
          >
            <div className="flex items-center justify-between px-1">
              <h3 className="text-sm font-semibold text-gray-700">{stageLabel(stage)}</h3>
              <span className="rounded-full bg-white px-2 py-0.5 text-xs font-medium text-gray-500 shadow-sm">
                {items.length}
              </span>
            </div>

            {items.map((a) => {
              const idle = daysSince(a.lastActivityAt);
              const stale = idle >= 14 && (a.stage === "APPLIED" || a.stage === "SCREENING");
              return (
                // role="button" + tabIndex keep the card draggable AND usable from the keyboard
                // (Enter or Space opens it; the detail panel has "Move to" buttons as the keyboard way to move it).
                <div
                  key={a.id}
                  role="button"
                  tabIndex={0}
                  draggable
                  onDragStart={(e) => {
                    e.dataTransfer.setData("text/plain", a.id);
                    setDragging(true);
                  }}
                  onDragEnd={() => {
                    setDragging(false);
                    setDragOver(null);
                  }}
                  onClick={() => onOpen(a.id)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      onOpen(a.id);
                    }
                  }}
                  className="cursor-grab rounded-xl border border-gray-200 bg-white p-3.5 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md focus:outline-none focus-visible:ring-2 focus-visible:ring-gray-400 active:cursor-grabbing"
                >
                  <p className="font-semibold leading-snug text-gray-900">{a.company}</p>
                  <p className="mt-0.5 text-sm text-gray-500">{a.role}</p>
                  <p className={"mt-3 text-xs " + (stale ? "font-medium text-amber-600" : "text-gray-400")}>
                    {idle === 0 ? "Active today" : `${idle}d since activity`}
                  </p>
                </div>
              );
            })}

            {items.length === 0 &&
              (dragging ? (
                <p className="rounded-xl border border-dashed border-gray-300 py-6 text-center text-xs text-gray-400">
                  Drop here
                </p>
              ) : (
                <p className="py-4 text-center text-xs text-gray-300">Empty</p>
              ))}
          </section>
        );
      })}
    </div>
  );
}
