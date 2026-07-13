import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { FraudRatePoint } from '../../types/api'

const axisTick = { fill: 'var(--color-faint)', fontSize: 11, fontFamily: 'IBM Plex Mono, monospace' }

/** Flagged vs reviewed rate. Flagged is the risk line (red); reviewed is neutral. */
export function FraudRateChart({ data }: { data: FraudRatePoint[] }) {
  return (
    <div className="h-64 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ top: 8, right: 12, left: -14, bottom: 0 }}>
          <CartesianGrid stroke="var(--color-border)" strokeDasharray="2 4" vertical={false} />
          <XAxis
            dataKey="date"
            tick={axisTick}
            tickFormatter={(d: string) => d.slice(5)}
            tickLine={false}
            axisLine={{ stroke: 'var(--color-border)' }}
            minTickGap={28}
          />
          <YAxis tick={axisTick} tickLine={false} axisLine={false} width={34} unit="%" />
          <Tooltip
            contentStyle={{
              background: 'var(--color-surface)',
              border: '1px solid var(--color-border)',
              borderRadius: 8,
              fontSize: 12,
            }}
            labelStyle={{ color: 'var(--color-muted)' }}
            itemStyle={{ fontFamily: 'IBM Plex Mono, monospace' }}
          />
          <Line
            type="monotone"
            dataKey="flaggedRate"
            name="Flagged %"
            stroke="var(--color-risk-high)"
            strokeWidth={2}
            dot={false}
          />
          <Line
            type="monotone"
            dataKey="reviewedRate"
            name="Reviewed %"
            stroke="var(--color-muted)"
            strokeWidth={1.5}
            strokeDasharray="3 3"
            dot={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
