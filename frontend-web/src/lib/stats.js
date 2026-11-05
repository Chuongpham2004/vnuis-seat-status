import { dayKey, formatDay, hourOf } from './format'

/** Hourly stats -> one heatmap row per day for the chosen zone. */
export function heatmapRows(stats, zone) {
  const byDay = new Map()
  for (const s of stats) {
    if (s.zoneCode !== zone) continue
    const key = dayKey(s.bucketHour)
    if (!byDay.has(key)) byDay.set(key, { key, label: formatDay(s.bucketHour), cells: Array(24).fill(null) })
    byDay.get(key).cells[hourOf(s.bucketHour)] = s.avgOccupancyRate
  }
  return [...byDay.values()].sort((a, b) => a.key.localeCompare(b.key))
}
