import { Suspense } from 'react'
import { Outlet } from 'react-router-dom'
import { LoadingRows } from './LoadingState'
import { SidebarNav } from './SidebarNav'
import { TopBar } from './TopBar'

/** Persistent rail + top bar + routed content. Holds at tablet width and up. */
export function AppShell() {
  return (
    <div className="grid min-h-screen grid-cols-[3.5rem_1fr] md:grid-cols-[14rem_1fr]">
      <SidebarNav />
      <div className="flex min-h-screen min-w-0 flex-col">
        <TopBar />
        <main className="min-w-0 flex-1 px-4 py-5 md:px-6 md:py-6">
          <Suspense fallback={<LoadingRows />}>
            <Outlet />
          </Suspense>
        </main>
      </div>
    </div>
  )
}
