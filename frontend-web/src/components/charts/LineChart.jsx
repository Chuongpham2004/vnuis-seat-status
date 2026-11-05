import { useState } from 'react'
import { useWidth } from './useWidth'

/**
 * Time-series line chart (0..1 rates shown as %).
 *  series: [{ key, label, color, points: [{ t: Date, v: number, lo?: number, hi?: number }] }]
 * Marks: 2px lines, optional 10% band (lo..hi), end dot with a 2px surface ring and a direct
 * end label. Hover: vertical crosshair snapping to the nearest x, one tooltip listing every series.
 * Keyboard: the plot is focusable and ←/→ move the crosshair.
 */
export default function LineChart({ series, height = 280, formatX, yMax = 1, showBand = true, ariaLabel }) {
  const [ref, width] = useWidth()
  const [hover, setHover] = useState(null) // index into xs
  const margin = { top: 12, right: 64, bottom: 28, left: 40 }
  const innerW = Math.max(50, width - margin.left - margin.right)
  const innerH = height - margin.top - margin.bottom

  const xs = series[0]?.points.map((p) => p.t) || []
  if (!xs.length) return <div ref={ref} className="muted text-sm py-8 text-center">Chưa có dữ liệu.</div>

  const t0 = xs[0].getTime()
  const t1 = xs[xs.length - 1].getTime()
  const x = (t) => margin.left + (t1 === t0 ? innerW / 2 : ((t.getTime() - t0) / (t1 - t0)) * innerW)
  const y = (v) => margin.top + innerH - (Math.max(0, Math.min(yMax, v)) / yMax) * innerH
  const yTicks = [0, 0.25, 0.5, 0.75, 1].map((f) => f * yMax)
  const xTickEvery = Math.max(1, Math.ceil(xs.length / Math.max(2, Math.floor(innerW / 70))))

  const path = (pts, acc) =>
    pts.map((p, i) => `${i ? 'L' : 'M'}${x(p.t).toFixed(1)},${y(acc(p)).toFixed(1)}`).join('')
  const band = (pts) =>
    path(pts, (p) => p.hi) +
    [...pts]
      .reverse()
      .map((p) => `L${x(p.t).toFixed(1)},${y(p.lo).toFixed(1)}`)
      .join('') +
    'Z'

  // End labels: nudge apart only to avoid exact overlap; legend + tooltip carry identity too.
  const ends = series
    .map((s) => ({ s, last: s.points[s.points.length - 1] }))
    .filter((e) => e.last)
    .map((e) => ({ ...e, ly: y(e.last.v) }))
    .sort((a, b) => a.ly - b.ly)
  for (let i = 1; i < ends.length; i++) {
    if (ends[i].ly - ends[i - 1].ly < 14) ends[i].ly = ends[i - 1].ly + 14
  }

  const nearest = (clientX, rect) => {
    const px = clientX - rect.left - margin.left
    const frac = Math.max(0, Math.min(1, px / innerW))
    return Math.round(frac * (xs.length - 1))
  }

  const hx = hover != null ? x(xs[hover]) : null
  const tooltipLeft = hx != null ? (hx > width - 200 ? hx - 180 : hx + 12) : 0

  return (
    <div ref={ref} className="relative w-full">
      <svg
        width={width}
        height={height}
        role="img"
        aria-label={ariaLabel}
        tabIndex={0}
        className="block outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)] rounded"
        onPointerMove={(e) => setHover(nearest(e.clientX, e.currentTarget.getBoundingClientRect()))}
        onPointerLeave={() => setHover(null)}
        onFocus={() => setHover((h) => h ?? xs.length - 1)}
        onBlur={() => setHover(null)}
        onKeyDown={(e) => {
          if (e.key === 'ArrowLeft') setHover((h) => Math.max(0, (h ?? 0) - 1))
          if (e.key === 'ArrowRight') setHover((h) => Math.min(xs.length - 1, (h ?? -1) + 1))
        }}
      >
        {yTicks.map((v) => (
          <g key={v}>
            <line
              x1={margin.left}
              x2={margin.left + innerW}
              y1={y(v)}
              y2={y(v)}
              stroke={v === 0 ? 'var(--axis)' : 'var(--grid)'}
              strokeWidth="1"
            />
            <text x={margin.left - 6} y={y(v)} dy="0.32em" textAnchor="end" fontSize="11" fill="var(--ink-muted)">
              {Math.round((v / 1) * 100)}%
            </text>
          </g>
        ))}
        {xs.map((t, i) =>
          i % xTickEvery === 0 ? (
            <text key={i} x={x(t)} y={height - 8} textAnchor="middle" fontSize="11" fill="var(--ink-muted)">
              {formatX(t)}
            </text>
          ) : null,
        )}

        {showBand &&
          series.map((s) =>
            s.points.every((p) => p.lo != null && p.hi != null) ? (
              <path key={s.key + '-band'} d={band(s.points)} fill={s.color} opacity="0.1" />
            ) : null,
          )}
        {series.map((s) => (
          <path
            key={s.key}
            d={path(s.points, (p) => p.v)}
            fill="none"
            stroke={s.color}
            strokeWidth="2"
            strokeLinejoin="round"
            strokeLinecap="round"
          />
        ))}
        {ends.map(({ s, last, ly }) => (
          <g key={s.key + '-end'}>
            <circle cx={x(last.t)} cy={y(last.v)} r="4" fill={s.color} stroke="var(--surface-1)" strokeWidth="2" />
            <text x={x(last.t) + 8} y={ly} dy="0.32em" fontSize="11" fill="var(--ink-secondary)">
              {s.label} {Math.round(last.v * 100)}%
            </text>
          </g>
        ))}

        {hx != null && (
          <g pointerEvents="none">
            <line x1={hx} x2={hx} y1={margin.top} y2={margin.top + innerH} stroke="var(--ink-muted)" strokeWidth="1" />
            {series.map((s) =>
              s.points[hover] ? (
                <circle
                  key={s.key}
                  cx={hx}
                  cy={y(s.points[hover].v)}
                  r="4"
                  fill={s.color}
                  stroke="var(--surface-1)"
                  strokeWidth="2"
                />
              ) : null,
            )}
          </g>
        )}
      </svg>

      {hover != null && (
        <div className="chart-tooltip" style={{ left: tooltipLeft, top: margin.top }}>
          <p className="muted mb-1">{formatX(xs[hover], true)}</p>
          {series.map((s) =>
            s.points[hover] ? (
              <p key={s.key} className="flex items-center gap-2">
                <span className="line-key" style={{ background: s.color }} />
                <span className="value">{Math.round(s.points[hover].v * 100)}%</span>
                <span className="secondary">{s.label}</span>
                {s.points[hover].lo != null && (
                  <span className="muted">
                    ({Math.round(s.points[hover].lo * 100)}–{Math.round(s.points[hover].hi * 100)}%)
                  </span>
                )}
              </p>
            ) : null,
          )}
        </div>
      )}
    </div>
  )
}
