import { filterSeats, mergeSeat, seatGrid, summariseZones } from '../seats'

const seat = (code, extra = {}) => ({
  seatCode: code,
  zoneCode: code[0],
  status: 'AVAILABLE',
  coordX: 0,
  coordY: 0,
  deviceOnline: true,
  quietZone: false,
  hasPowerOutlet: false,
  seatType: 'SINGLE',
  ...extra,
})

describe('mergeSeat', () => {
  it('applies a newer update', () => {
    const map = { 'A-01': seat('A-01', { updatedAt: '2026-11-05T08:00:00Z' }) }
    const next = mergeSeat(map, seat('A-01', { status: 'OCCUPIED', updatedAt: '2026-11-05T08:00:05Z' }))
    expect(next['A-01'].status).toBe('OCCUPIED')
  })

  it('ignores an update older than the current state', () => {
    const map = { 'A-01': seat('A-01', { status: 'OCCUPIED', updatedAt: '2026-11-05T08:00:05Z' }) }
    const next = mergeSeat(map, seat('A-01', { status: 'AVAILABLE', updatedAt: '2026-11-05T08:00:00Z' }))
    expect(next).toBe(map)
  })

  it('adds an unknown seat and ignores payloads without a code', () => {
    expect(mergeSeat({}, seat('B-02'))['B-02']).toBeTruthy()
    const map = {}
    expect(mergeSeat(map, {})).toBe(map)
  })
})

describe('summariseZones', () => {
  it('counts states like backend-core (rate = occupied + away)', () => {
    const seats = {
      'A-01': seat('A-01', { status: 'OCCUPIED' }),
      'A-02': seat('A-02', { status: 'AWAY', deviceOnline: false }),
      'A-03': seat('A-03', { status: 'RESERVED' }),
      'A-04': seat('A-04'),
      'B-01': seat('B-01', { status: 'OCCUPIED' }),
    }
    const [a] = summariseZones([{ code: 'A', name: 'Khu A', totalSeats: 20 }], seats)
    expect(a).toMatchObject({ totalSeats: 4, available: 1, reserved: 1, occupied: 1, away: 1, offlineDevices: 1 })
    expect(a.occupancyRate).toBeCloseTo(0.5)
  })
})

describe('seatGrid', () => {
  it('sizes the grid from coordinates and sorts row by row', () => {
    const g = seatGrid([
      seat('A-07', { coordX: 1, coordY: 1 }),
      seat('A-01', { coordX: 0, coordY: 0 }),
      seat('A-05', { coordX: 4, coordY: 0 }),
    ])
    expect(g.columns).toBe(5)
    expect(g.rows).toBe(2)
    expect(g.cells.map((c) => c.seatCode)).toEqual(['A-01', 'A-05', 'A-07'])
  })

  it('handles an empty zone', () => {
    expect(seatGrid([])).toEqual({ columns: 0, rows: 0, cells: [] })
  })
})

describe('filterSeats', () => {
  it('combines the filters', () => {
    const list = [
      seat('A-01', { quietZone: true, hasPowerOutlet: true }),
      seat('A-02', { quietZone: true, status: 'OCCUPIED', hasPowerOutlet: true }),
      seat('C-01', { seatType: 'GROUP_TABLE' }),
    ]
    expect(filterSeats(list, { quiet: true, power: true, onlyAvailable: true }).map((s) => s.seatCode)).toEqual([
      'A-01',
    ])
    expect(filterSeats(list, { type: 'GROUP_TABLE' }).map((s) => s.seatCode)).toEqual(['C-01'])
    expect(filterSeats(list, {})).toHaveLength(3)
  })
})
