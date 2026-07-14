import { LogOut } from 'lucide-react'
import { useSession } from '../auth/session'
import { config } from '../config'

function initials(name: string): string {
  return name.replace(/[^a-zA-Z]/g, '').slice(0, 2).toUpperCase() || '··'
}

export function TopBar() {
  const session = useSession()
  return (
    <header className="flex items-center justify-between border-b border-border bg-bg/80 px-4 py-3 backdrop-blur md:px-6">
      {/* A user belongs to exactly one tenant, fixed by the token's tenant_id
          claim — read-only context, not a selector. */}
      <span className="inline-flex items-center gap-2 rounded-md border border-border bg-surface px-3 py-1.5 text-sm">
        <span className="text-muted">Tenant</span>
        <span className="font-mono text-text">{session.tenantId || '—'}</span>
      </span>

      <div className="flex items-center gap-3">
        <span className="hidden text-sm text-muted sm:inline">{session.username}</span>
        <span className="flex h-7 w-7 items-center justify-center rounded-full border border-border bg-surface-2 font-mono text-xs text-muted">
          {initials(session.username)}
        </span>
        {!config.useMock && (
          <button
            onClick={session.logout}
            title="Sign out"
            className="rounded-md p-1.5 text-muted outline-none hover:bg-surface-2 hover:text-text focus-visible:ring-2 focus-visible:ring-border-strong"
          >
            <LogOut className="h-4 w-4" strokeWidth={1.75} />
          </button>
        )}
      </div>
    </header>
  )
}
