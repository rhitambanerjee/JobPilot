import { useState } from "react";
import type { Application } from "../types";
import { formatDate } from "../util";
import { field } from "../ui";

interface Props {
  applications: Application[];
  onOpen: (id: string) => void;
}

export default function ArchivePage({ applications, onOpen }: Props) {
  const [query, setQuery] = useState("");

  const q = query.trim().toLowerCase();
  const shown = applications
    .filter((a) => !q || `${a.company} ${a.role}`.toLowerCase().includes(q))
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));

  return (
    <div className="mx-auto max-w-6xl px-6 py-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="text-xl font-semibold tracking-tight text-gray-900">Archive</h2>
          <p className="mt-1 text-sm text-gray-500">Applications you have closed out. Click one to see its history.</p>
        </div>
        <input
          className={field + " max-w-xs"}
          placeholder="Search company or role…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
      </div>

      {shown.length === 0 ? (
        <div className="mt-8 rounded-2xl border border-dashed border-gray-300 bg-white py-16 text-center text-sm text-gray-400">
          {applications.length === 0 ? "Nothing archived yet." : "No matches."}
        </div>
      ) : (
        <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {shown.map((a) => (
            <button
              key={a.id}
              onClick={() => onOpen(a.id)}
              className="rounded-xl border border-gray-200 bg-white p-4 text-left shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
            >
              <p className="font-semibold text-gray-900">{a.company}</p>
              <p className="mt-0.5 text-sm text-gray-500">{a.role}</p>
              <div className="mt-4 flex justify-between text-xs text-gray-400">
                <span>Added {formatDate(a.createdAt)}</span>
                <span>Archived {formatDate(a.updatedAt)}</span>
              </div>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
