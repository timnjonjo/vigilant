import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// Unmount between tests (Testing Library's auto-cleanup only registers when
// vitest globals are enabled; we register it explicitly instead).
afterEach(() => cleanup())
