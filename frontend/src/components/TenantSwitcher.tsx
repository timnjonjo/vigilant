import * as DropdownMenu from '@radix-ui/react-dropdown-menu'
import { Check, ChevronsUpDown } from 'lucide-react'
import { useTenant } from '../state/tenant'

/**
 * Cosmetic tenant switch. Multi-tenancy is an open decision (spec §10) — this is
 * intentionally shallow and trivially removable.
 */
export function TenantSwitcher() {
  const { tenantId, setTenantId, tenants } = useTenant()
  const current = tenants.find((t) => t.id === tenantId)

  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger className="inline-flex items-center gap-2 rounded-md border border-border bg-surface px-3 py-1.5 text-sm outline-none hover:bg-surface-2 focus-visible:ring-2 focus-visible:ring-border-strong">
        <span className="text-muted">Tenant</span>
        <span className="font-medium text-text">{current?.name ?? tenantId}</span>
        <ChevronsUpDown className="h-3.5 w-3.5 text-faint" />
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content
          align="start"
          sideOffset={6}
          className="z-50 min-w-[13rem] rounded-md border border-border bg-surface p-1 shadow-2xl"
        >
          {tenants.map((t) => (
            <DropdownMenu.Item
              key={t.id}
              onSelect={() => setTenantId(t.id)}
              className="flex cursor-pointer items-center justify-between gap-4 rounded px-2 py-1.5 text-sm outline-none data-[highlighted]:bg-surface-2"
            >
              <span className="flex flex-col">
                <span className="text-text">{t.name}</span>
                <span className="font-mono text-xs text-faint">{t.id}</span>
              </span>
              {t.id === tenantId && <Check className="h-4 w-4 text-text" />}
            </DropdownMenu.Item>
          ))}
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  )
}
