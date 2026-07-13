import { config } from '../config'
import type { VigilantApi } from './contract'
import { mockApi } from './mock/mockApi'
import { httpApi } from './http/httpApi'

/**
 * The single swap point. `VITE_USE_MOCK=false` flips the whole app onto the real
 * backend transport; nothing else changes. Every screen imports `api` from here.
 */
export const api: VigilantApi = config.useMock ? mockApi : httpApi

export type { VigilantApi } from './contract'
