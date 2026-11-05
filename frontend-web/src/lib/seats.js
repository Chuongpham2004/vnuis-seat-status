// Pure helpers for the live seat map (kept free of React so they are easy to test).

/** Merge one /topic/seats message into the map, ignoring messages older than what we have. */
export function mergeSeat(seatsByCode, incoming) {
  if (!incoming?.seatCode) return seatsByCode
  const current = seatsByCode[incoming.seatCode]
  if (current?.updatedAt && incoming.updatedAt && new Date(incoming.updatedAt) < new Date(current.updatedAt)) {
    return seatsByCode
  }
  return { ...seatsByCode, [incoming.seatCode]: { ...current, ...incoming } }
}

export function indexSeats(list) {
  const out = {}
  for (const s of list || []) out[s.seatCode] = s
  return out
}

/**
 * Zone counters derived from the live seats, same rules as backend-core's ZoneView:
 * occupancyRate = (OCCUPIED + AWAY) / total; RESERVED is counted separately.
 */
export function summariseZones(zones, seatsByCode) {
  const seats = Object.values(seatsByCode)
  return (zones || []).map((zone) => {
    const inZone = seats.filter((s) => s.zoneCode === zone.code)
    const count = (status) => inZone.filter((s) => s.status === status).length
    const total = inZone.length || zone.totalSeats || 0
    const reserved = count('RESERVED')
    const occupied = count('OCCUPIED')
    const away = count('AWAY')
    return {
      ...zone,
      totalSeats: total,
      available: total - reserved - occupied - away,
      reserved,
      occupied,
      away,
      occupancyRate: total ? (occupied + away) / total : 0,
      offlineDevices: inZone.filter((s) => !s.deviceOnline).length,
    }
  })
}

/** Seats of one zone laid out on their (coordX, coordY) grid. */
export function seatGrid(seats) {
  if (!seats.length) return { columns: 0, rows: 0, cells: [] }
  const columns = Math.max(...seats.map((s) => s.coordX)) + 1
  const rows = Math.max(...seats.map((s) => s.coordY)) + 1
  const cells = [...seats].sort((a, b) => a.coordY - b.coordY || a.coordX - b.coordX)
  return { columns, rows, cells }
}

export function filterSeats(seats, { quiet, power, type, onlyAvailable } = {}) {
  return seats.filter(
    (s) =>
      (!quiet || s.quietZone) &&
      (!power || s.hasPowerOutlet) &&
      (!type || s.seatType === type) &&
      (!onlyAvailable || s.status === 'AVAILABLE'),
  )
}
