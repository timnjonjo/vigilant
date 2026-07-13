import { REASON_LABELS } from '../lib/labels'
import type { ReasonCode } from '../types/api'

/**
 * A reason code as evidence, not severity — deliberately monochrome. Colour in
 * this app only ever means risk level; the score/status carry that, not the code.
 */
export function ReasonCodeChip({ code }: { code: ReasonCode }) {
  return (
    <span className="inline-flex items-center gap-2 rounded border border-border bg-surface-2 px-2 py-1">
      <span className="font-mono text-xs text-faint">{code}</span>
      <span className="text-xs text-muted">{REASON_LABELS[code]}</span>
    </span>
  )
}
