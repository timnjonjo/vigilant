import React from 'react'
import ReactDOM from 'react-dom/client'
import { AuthProvider } from 'react-oidc-context'
import { RouterProvider } from 'react-router-dom'
import { oidcConfig } from './auth/authConfig'
import { MockSessionProvider, RealSessionProvider } from './auth/session'
import { config } from './config'
import { router } from './routes'
import './index.css'

const app = <RouterProvider router={router} />

// Mock mode skips Keycloak entirely; real mode wraps the app in the OIDC provider.
const tree = config.useMock ? (
  <MockSessionProvider>{app}</MockSessionProvider>
) : (
  <AuthProvider {...oidcConfig}>
    <RealSessionProvider>{app}</RealSessionProvider>
  </AuthProvider>
)

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>{tree}</React.StrictMode>,
)
