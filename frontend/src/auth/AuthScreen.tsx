import { Loader2, ShieldCheck } from 'lucide-react'
import type { ReactNode } from 'react'

/**
 * Branded full-screen interstitial for the auth handshake — the product's first
 * impression before Keycloak's hosted login takes over, and while it's coming
 * back. Monochrome chrome per the design system; saturated colour only ever
 * signals risk (used for the error variant).
 */
function AuthScreenFrame({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-8 bg-bg px-6">
      <div className="flex items-center gap-3">
        <ShieldCheck className="h-7 w-7 text-text" strokeWidth={1.75} />
        <span className="font-display text-2xl font-semibold tracking-tight text-text">Vigilant</span>
      </div>
      {children}
      <p className="font-mono text-xs tracking-wide text-faint">Referral-fraud intelligence</p>
    </div>
  )
}

/** Loading state: spinner + message while redirecting to / returning from Keycloak. */
export function AuthSplash({ message }: { message: string }) {
  return (
    <AuthScreenFrame>
      <div className="flex items-center gap-2.5 text-muted">
        <Loader2 className="h-4 w-4 animate-spin" strokeWidth={2} aria-hidden />
        <span className="font-display text-sm">{message}</span>
      </div>
    </AuthScreenFrame>
  )
}

/** Actionable failure state (Keycloak unreachable, or silent-renew failed). */
export function AuthError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <AuthScreenFrame>
      <div className="flex max-w-sm flex-col items-center gap-3 text-center">
        <p className="font-display text-sm text-risk-high">Couldn’t sign in</p>
        <p className="text-sm text-muted">{message}</p>
        <button
          onClick={onRetry}
          className="mt-1 rounded-md border border-border-strong bg-surface-2 px-4 py-2 text-sm text-text transition-colors hover:bg-border/40"
        >
          Try again
        </button>
      </div>
    </AuthScreenFrame>
  )
}
