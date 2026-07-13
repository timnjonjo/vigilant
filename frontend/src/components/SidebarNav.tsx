import { Activity, ListChecks, Megaphone, ShieldCheck } from 'lucide-react'
import { NavLink } from 'react-router-dom'
import { useSession } from '../auth/session'
import { cn } from '../lib/cn'

export function SidebarNav() {
  const session = useSession()
  const canSeeCases = session.hasRole('fraud_analyst') || session.hasRole('tenant_admin')
  const canSeeMonitoring = canSeeCases || session.hasRole('ops_viewer')
  const canManageCampaigns = session.hasRole('tenant_admin')

  const nav = [
    { to: '/queue', label: 'Case Queue', icon: ListChecks, show: canSeeCases },
    { to: '/monitoring', label: 'Monitoring', icon: Activity, show: canSeeMonitoring },
    { to: '/campaigns', label: 'Campaigns', icon: Megaphone, show: canManageCampaigns },
  ].filter((item) => item.show)

  return (
    <aside className="flex flex-col border-r border-border bg-surface">
      <div className="flex items-center gap-2 px-3 py-4 md:px-5">
        <ShieldCheck className="h-5 w-5 shrink-0 text-text" strokeWidth={1.75} />
        <span className="hidden font-display text-lg font-semibold tracking-tight md:inline">
          Vigilant
        </span>
      </div>

      <nav className="flex flex-col gap-0.5 px-2 py-2 md:px-3">
        {nav.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              cn(
                'flex items-center gap-2.5 rounded-md px-2.5 py-2 text-sm transition-colors md:px-3',
                isActive
                  ? 'bg-surface-2 text-text'
                  : 'text-muted hover:bg-surface-2 hover:text-text',
              )
            }
          >
            <Icon className="h-4 w-4 shrink-0" strokeWidth={1.75} />
            <span className="hidden md:inline">{label}</span>
          </NavLink>
        ))}
      </nav>

      <div className="mt-auto hidden px-5 py-4 font-mono text-[11px] leading-relaxed text-faint md:block">
        rules-based · v1
        <br />
        gates payout, never signup
      </div>
    </aside>
  )
}
