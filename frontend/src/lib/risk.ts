import type { Decision } from '../types/api'

/**
 * The single source of truth for risk/severity → colour. Every surface (score
 * bands, case status, alert severity, graph accents) maps through here so a HIGH
 * score, a REJECT status, and a "high" alert all read visually identical.
 */
export type RiskLevel = 'low' | 'medium' | 'high' | 'critical'

/** Score bands mirror the backend: <0.40 APPROVE, <0.75 HOLD, >=0.75 REJECT. */
export function riskLevelFromScore(score: number): RiskLevel {
  if (score >= 0.75) return 'high'
  if (score >= 0.4) return 'medium'
  return 'low'
}

export function riskLevelFromDecision(decision: Decision): RiskLevel {
  switch (decision) {
    case 'REJECT':
      return 'high'
    case 'HOLD':
      return 'medium'
    case 'APPROVE':
      return 'low'
  }
}

export type AlertSeverity = 'critical' | 'high' | 'medium' | 'low'

/** Classes are written out in full so Tailwind's scanner emits them. */
export const RISK_META: Record<
  RiskLevel,
  { label: string; text: string; bgSoft: string; border: string; dot: string; cssVar: string }
> = {
  low: {
    label: 'Low',
    text: 'text-risk-low',
    bgSoft: 'bg-risk-low/10',
    border: 'border-risk-low/40',
    dot: 'bg-risk-low',
    cssVar: 'var(--color-risk-low)',
  },
  medium: {
    label: 'Medium',
    text: 'text-risk-medium',
    bgSoft: 'bg-risk-medium/10',
    border: 'border-risk-medium/40',
    dot: 'bg-risk-medium',
    cssVar: 'var(--color-risk-medium)',
  },
  high: {
    label: 'High',
    text: 'text-risk-high',
    bgSoft: 'bg-risk-high/10',
    border: 'border-risk-high/40',
    dot: 'bg-risk-high',
    cssVar: 'var(--color-risk-high)',
  },
  critical: {
    label: 'Critical',
    text: 'text-risk-critical',
    bgSoft: 'bg-risk-critical/10',
    border: 'border-risk-critical/40',
    dot: 'bg-risk-critical',
    cssVar: 'var(--color-risk-critical)',
  },
}
