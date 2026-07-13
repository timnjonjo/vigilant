import type { ReactNode } from 'react'
import { cn } from '../lib/cn'

/** A titled surface container — the repeated structural unit of the console. */
export function Panel({
  title,
  actions,
  children,
  className,
  bodyClassName,
}: {
  title?: ReactNode
  actions?: ReactNode
  children: ReactNode
  className?: string
  bodyClassName?: string
}) {
  return (
    <section className={cn('rounded-lg border border-border bg-surface', className)}>
      {(title || actions) && (
        <header className="flex items-center justify-between border-b border-border px-4 py-3">
          {title && (
            <h2 className="text-xs font-semibold uppercase tracking-wider text-muted">{title}</h2>
          )}
          {actions}
        </header>
      )}
      <div className={cn('p-4', bodyClassName)}>{children}</div>
    </section>
  )
}
