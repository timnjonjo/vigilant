import cytoscape, { type ElementDefinition, type LayoutOptions } from 'cytoscape'
import dagre from 'cytoscape-dagre'
import { useEffect, useRef } from 'react'
import type { CaseGraph } from '../../types/api'

let dagreRegistered = false
function ensureDagre() {
  if (!dagreRegistered) {
    cytoscape.use(dagre)
    dagreRegistered = true
  }
}

// Token hexes duplicated here because the canvas can't read CSS variables.
const INK = { fill: '#1b2129', fillAlt: '#232a34', border: '#313a47', text: '#e6eaf0' }
const EDGE = { referred: '#8a94a6', device: '#e3b341', subnet: '#db61a2' }
const DATACENTER = '#f85149'

const STYLE: cytoscape.StylesheetJson = [
  {
    selector: 'node',
    style: {
      'background-color': INK.fill,
      'border-color': INK.border,
      'border-width': 1.5,
      label: 'data(label)',
      color: INK.text,
      'font-size': 9,
      'font-family': 'IBM Plex Mono, monospace',
      'text-valign': 'bottom',
      'text-margin-y': 5,
      width: 26,
      height: 26,
    },
  },
  { selector: 'node.referrer', style: { width: 42, height: 42, 'border-color': EDGE.referred, 'border-width': 2, 'font-size': 10 } },
  { selector: 'node.converted', style: { 'background-color': INK.fillAlt } },
  { selector: 'node.datacenter', style: { 'border-color': DATACENTER, 'border-width': 2.5 } },
  {
    selector: 'edge',
    style: { width: 1.5, 'curve-style': 'bezier', 'line-color': EDGE.referred, 'target-arrow-color': EDGE.referred },
  },
  { selector: 'edge.REFERRED', style: { 'target-arrow-shape': 'triangle', 'arrow-scale': 0.9 } },
  { selector: 'edge.SHARES_DEVICE', style: { 'line-style': 'dashed', 'line-color': EDGE.device, 'target-arrow-shape': 'none' } },
  { selector: 'edge.SHARES_IP_SUBNET', style: { 'line-style': 'dotted', 'line-color': EDGE.subnet, 'target-arrow-shape': 'none' } },
]

/**
 * Renders the flagged subgraph with edge types drawn distinctly — solid arrowed
 * REFERRED, dashed amber SHARES_DEVICE, dotted magenta SHARES_IP_SUBNET — so the
 * fan-out, the device cluster or the cycle is visible, not just scored.
 */
export function GraphExplorer({ graph }: { graph: CaseGraph }) {
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const container = ref.current
    if (!container) return
    ensureDagre()

    const elements: ElementDefinition[] = [
      ...graph.nodes.map((node) => ({
        data: { id: node.id, label: node.userId },
        classes: [
          node.role,
          node.ipType === 'DATACENTER' ? 'datacenter' : '',
          node.converted ? 'converted' : '',
        ]
          .filter(Boolean)
          .join(' '),
      })),
      ...graph.edges.map((edge) => ({
        data: { id: edge.id, source: edge.source, target: edge.target },
        classes: edge.type,
      })),
    ]

    const cy = cytoscape({
      container,
      elements,
      style: STYLE,
      layout: { name: 'dagre', rankDir: 'LR', nodeSep: 26, rankSep: 60, edgeSep: 12 } as unknown as LayoutOptions,
      minZoom: 0.4,
      maxZoom: 2.5,
      wheelSensitivity: 0.2,
    })
    cy.fit(undefined, 28)

    return () => cy.destroy()
  }, [graph])

  return <div ref={ref} className="h-[420px] w-full rounded-md bg-bg" />
}
