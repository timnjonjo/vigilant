import { TriangleAlert } from 'lucide-react'

/** Errors state what happened and stay in the interface's voice — never vague. */
export function ErrorNote({ message }: { message: string }) {
  return (
    <div className="flex items-start gap-2.5 px-4 py-6 text-sm text-risk-high">
      <TriangleAlert className="mt-0.5 h-4 w-4 shrink-0" strokeWidth={1.75} />
      <div>
        <p className="font-medium">Couldn’t load this.</p>
        <p className="font-mono text-xs text-muted">{message}</p>
      </div>
    </div>
  )
}
