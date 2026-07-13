import { lazy } from 'react'
import { createBrowserRouter, Navigate } from 'react-router-dom'
import { Root } from './App'
import { CaseQueuePage } from './features/cases/CaseQueuePage'

// Split the heavy routes: Cytoscape (case detail) and Recharts (monitoring)
// load only when visited, keeping the queue's initial payload small.
const CaseDetailPage = lazy(() =>
  import('./features/cases/CaseDetailPage').then((m) => ({ default: m.CaseDetailPage })),
)
const MonitoringPage = lazy(() =>
  import('./features/monitoring/MonitoringPage').then((m) => ({ default: m.MonitoringPage })),
)
const CampaignsPage = lazy(() =>
  import('./features/campaigns/CampaignsPage').then((m) => ({ default: m.CampaignsPage })),
)

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Root />,
    children: [
      { index: true, element: <Navigate to="/queue" replace /> },
      { path: 'queue', element: <CaseQueuePage /> },
      { path: 'cases/:id', element: <CaseDetailPage /> },
      { path: 'monitoring', element: <MonitoringPage /> },
      { path: 'campaigns', element: <CampaignsPage /> },
    ],
  },
])
