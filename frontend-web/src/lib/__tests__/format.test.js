import { dayKey, formatCountdown, formatPercent, hourOf } from '../format'
import { heatmapRows } from '../stats'

describe('format', () => {
  it('formats countdowns', () => {
    expect(formatCountdown(0)).toBe('00:00')
    expect(formatCountdown(125)).toBe('02:05')
    expect(formatCountdown(3725)).toBe('1:02:05')
    expect(formatCountdown(-5)).toBe('00:00')
    expect(formatCountdown(null)).toBe('—')
  })

  it('uses the library time zone (UTC+7) for hours and days', () => {
    expect(hourOf('2026-11-05T17:30:00Z')).toBe(0) // 00:30 the next day in Hanoi
    expect(dayKey('2026-11-05T17:30:00Z')).toBe('2026-11-06')
  })

  it('formats percentages', () => {
    expect(formatPercent(0.456)).toBe('46%')
    expect(formatPercent(null)).toBe('—')
  })
})

describe('heatmapRows', () => {
  it('builds one row per local day with hourly cells for the chosen zone', () => {
    const rows = heatmapRows(
      [
        { zoneCode: 'A', bucketHour: '2026-11-05T01:00:00Z', avgOccupancyRate: 0.3 }, // 08h local
        { zoneCode: 'A', bucketHour: '2026-11-05T18:00:00Z', avgOccupancyRate: 0.1 }, // 01h the next day
        { zoneCode: 'B', bucketHour: '2026-11-05T01:00:00Z', avgOccupancyRate: 0.9 },
      ],
      'A',
    )
    expect(rows.map((r) => r.key)).toEqual(['2026-11-05', '2026-11-06'])
    expect(rows[0].cells[8]).toBe(0.3)
    expect(rows[1].cells[1]).toBe(0.1)
    expect(rows[0].cells.filter((c) => c != null)).toHaveLength(1)
  })
})
