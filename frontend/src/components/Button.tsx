import { cva, type VariantProps } from 'class-variance-authority'
import type { ButtonHTMLAttributes } from 'react'
import { cn } from '../lib/cn'

const button = cva(
  'inline-flex items-center justify-center gap-2 rounded-md border text-sm font-medium transition-colors ' +
    'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-offset-bg ' +
    'disabled:pointer-events-none disabled:opacity-40',
  {
    variants: {
      variant: {
        default: 'border-border-strong bg-surface-2 text-text hover:bg-border/40 focus-visible:ring-border-strong',
        success: 'border-risk-low/40 bg-risk-low/15 text-risk-low hover:bg-risk-low/25 focus-visible:ring-risk-low',
        warn: 'border-risk-medium/40 bg-risk-medium/15 text-risk-medium hover:bg-risk-medium/25 focus-visible:ring-risk-medium',
        danger: 'border-risk-high/40 bg-risk-high/15 text-risk-high hover:bg-risk-high/25 focus-visible:ring-risk-high',
        critical: 'border-risk-critical/40 bg-risk-critical/15 text-risk-critical hover:bg-risk-critical/25 focus-visible:ring-risk-critical',
        ghost: 'border-transparent bg-transparent text-muted hover:bg-surface-2 hover:text-text focus-visible:ring-border-strong',
      },
      size: {
        sm: 'h-8 px-3',
        md: 'h-9 px-4',
      },
    },
    defaultVariants: { variant: 'default', size: 'md' },
  },
)

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & VariantProps<typeof button>

export function Button({ className, variant, size, ...props }: ButtonProps) {
  return <button className={cn(button({ variant, size }), className)} {...props} />
}
