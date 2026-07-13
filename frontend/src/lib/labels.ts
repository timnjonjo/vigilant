import type { EdgeType, ReasonCode } from '../types/api'

/** Human labels for reason codes — the analyst reads plain language, not enums. */
export const REASON_LABELS: Record<ReasonCode, string> = {
  VELOCITY_BURST: 'Velocity burst',
  DEVICE_COLLISION: 'Device collision',
  IP_SUBNET_COLLISION: 'IP subnet collision',
  CYCLE_DETECTED: 'Referral cycle',
  DATACENTER_OR_VPN_IP: 'Datacenter / VPN IP',
}

export const EDGE_LABELS: Record<EdgeType, string> = {
  REFERRED: 'Referred',
  SHARES_DEVICE: 'Shared device',
  SHARES_IP_SUBNET: 'Shared IP subnet',
}
