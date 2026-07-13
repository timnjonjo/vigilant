import type { ChangeEvent } from 'react'

/** Compact labelled native select — accessible, and it won't read as templated. */
export function Select<T extends string>({
  label,
  value,
  options,
  onChange,
}: {
  label: string
  value: T
  options: [T, string][]
  onChange: (value: T) => void
}) {
  return (
    <label className="inline-flex items-center gap-2 text-sm">
      <span className="text-xs uppercase tracking-wider text-faint">{label}</span>
      <select
        value={value}
        onChange={(e: ChangeEvent<HTMLSelectElement>) => onChange(e.target.value as T)}
        className="rounded-md border border-border bg-surface px-2 py-1.5 text-sm text-text outline-none hover:bg-surface-2 focus-visible:ring-2 focus-visible:ring-border-strong"
      >
        {options.map(([v, l]) => (
          <option key={v} value={v} className="bg-surface">
            {l}
          </option>
        ))}
      </select>
    </label>
  )
}
