import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import Recommendations from '../components/Recommendations'
import ReservationCard from '../components/ReservationCard'
import SeatDetail from '../components/SeatDetail'
import SeatTile from '../components/SeatTile'
import StatusLegend from '../components/StatusLegend'
import ZoneBar from '../components/ZoneBar'
import { useActiveReservation } from '../hooks/useActiveReservation'
import { useLiveSeats } from '../hooks/useLiveSeats'
import { errorMessage } from '../lib/errors'
import { formatPercent } from '../lib/format'
import { filterSeats, seatGrid } from '../lib/seats'

function Toggle({ checked, onChange, children }) {
  return (
    <label className={'chip cursor-pointer select-none ' + (checked ? 'chip-on' : '')}>
      <input type="checkbox" className="sr-only" checked={checked} onChange={(e) => onChange(e.target.checked)} />
      {children}
    </label>
  )
}

/** Directory sign for one zone; pressing it focuses the plan on that room. */
function ZoneSign({ zone, active, onToggle }) {
  return (
    <button type="button" className="sign" aria-pressed={active} onClick={onToggle}>
      <span className="sign-letter" aria-hidden>
        {zone.code}
      </span>
      <span className="hidden lg:block font-semibold leading-snug">{zone.name.replace(/^Khu \w+ - /, '')}</span>
      <span className="sign-sub">
        <span className="text-base font-bold text-[var(--sign-ink)] tabular-nums">{zone.available}</span>
        <span className="lg:hidden"> trống</span>
        <span className="hidden lg:inline">
          {' '}
          chỗ trống trên {zone.totalSeats}, tầng {zone.floorNumber}
        </span>
      </span>
    </button>
  )
}

function Room({ zone, seats, filters, selectedCode, mySeatCode, onSelect, since }) {
  const grid = seatGrid(seats)
  const matching = new Set(filterSeats(seats, filters).map((s) => s.seatCode))
  return (
    <section className="room p-4 sm:p-5" aria-labelledby={`zone-${zone.code}`}>
      <span className="room-door" aria-hidden />
      <div className="flex items-center gap-3 mb-3">
        <span className="room-tag" aria-hidden>
          {zone.code}
        </span>
        <div className="min-w-0">
          <h2 id={`zone-${zone.code}`} className="font-semibold">
            {zone.name}
          </h2>
          <p className="text-xs secondary">
            {zone.available} chỗ trống, đang dùng {formatPercent(zone.occupancyRate)}
            {zone.offlineDevices > 0 && `, ${zone.offlineDevices} cảm biến mất kết nối`}
          </p>
        </div>
      </div>
      <ZoneBar zone={zone} height={6} />
      <div
        className="grid gap-2.5 mt-4"
        style={{ gridTemplateColumns: `repeat(${grid.columns || 1}, minmax(0, 84px))` }}
      >
        {grid.cells.map((seat) => (
          <div key={seat.seatCode} style={{ gridColumn: seat.coordX + 1, gridRow: seat.coordY + 1 }}>
            <SeatTile
              seat={seat}
              since={since}
              selected={seat.seatCode === selectedCode}
              mine={seat.seatCode === mySeatCode}
              dimmed={!matching.has(seat.seatCode)}
              onSelect={onSelect}
            />
          </div>
        ))}
      </div>
    </section>
  )
}

export default function MapPage() {
  const { user, isAdmin } = useAuth()
  const { zones, seats, seatsByCode, loading, error } = useLiveSeats()
  const { reservation, setReservation, reload } = useActiveReservation()
  const [selectedCode, setSelectedCode] = useState(null)
  const [zoneFilter, setZoneFilter] = useState('')
  const [filters, setFilters] = useState({ quiet: false, power: false, type: '', onlyAvailable: false })
  const [since] = useState(() => Date.now())

  const counts = useMemo(() => {
    const c = {}
    for (const s of seats) c[s.status] = (c[s.status] || 0) + 1
    return c
  }, [seats])

  useEffect(() => {
    if (!selectedCode) return undefined
    const onKey = (e) => e.key === 'Escape' && setSelectedCode(null)
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [selectedCode])

  const selected = selectedCode ? seatsByCode[selectedCode] : null
  const mySeatCode = reservation?.seatCode
  const floors = useMemo(() => {
    const byFloor = new Map()
    for (const z of zones) {
      if (zoneFilter && z.code !== zoneFilter) continue
      if (!byFloor.has(z.floorNumber)) byFloor.set(z.floorNumber, [])
      byFloor.get(z.floorNumber).push(z)
    }
    return [...byFloor.entries()].sort((a, b) => a[0] - b[0])
  }, [zones, zoneFilter])

  if (loading) return <p className="muted">Đang tải sơ đồ…</p>
  if (error && !seats.length) {
    return (
      <div className="card p-6 max-w-lg">
        <p className="font-semibold">Chưa tải được sơ đồ ghế</p>
        <p className="secondary text-sm mt-1">{errorMessage(error)} Kiểm tra backend-core đang chạy ở cổng 8080.</p>
      </div>
    )
  }

  const totalAvailable = zones.reduce((n, z) => n + z.available, 0)
  const pick = (s) => setSelectedCode((c) => (c === s.seatCode ? null : s.seatCode))

  return (
    <div className="grid grid-cols-[minmax(0,1fr)] lg:grid-cols-[300px_minmax(0,1fr)] gap-6 items-start">
      <aside className="space-y-5 lg:sticky lg:top-20">
        <div>
          <h1 className="page-title">Ngồi đâu bây giờ?</h1>
          <p className="secondary text-sm mt-1">
            {totalAvailable} trên {seats.length} chỗ đang trống. Cảm biến dưới mỗi ghế cập nhật sơ đồ theo thời gian thực.
          </p>
        </div>

        <nav className="grid grid-cols-3 lg:grid-cols-1 gap-2" aria-label="Các khu">
          {zones.map((z) => (
            <ZoneSign
              key={z.code}
              zone={z}
              active={zoneFilter === z.code}
              onToggle={() => setZoneFilter((f) => (f === z.code ? '' : z.code))}
            />
          ))}
        </nav>

        <div className="flex flex-wrap gap-2" role="group" aria-label="Bộ lọc">
          <Toggle checked={filters.onlyAvailable} onChange={(v) => setFilters((f) => ({ ...f, onlyAvailable: v }))}>
            Chỉ ghế trống
          </Toggle>
          <Toggle checked={filters.power} onChange={(v) => setFilters((f) => ({ ...f, power: v }))}>
            Có ổ điện
          </Toggle>
          <Toggle
            checked={filters.type === 'GROUP_TABLE'}
            onChange={(v) => setFilters((f) => ({ ...f, type: v ? 'GROUP_TABLE' : '' }))}
          >
            Bàn nhóm
          </Toggle>
        </div>

        {reservation && !isAdmin && (
          <ReservationCard
            reservation={reservation}
            seat={seatsByCode[reservation.seatCode]}
            onChange={(r) => (r && (r.status === 'PENDING' || r.status === 'ACTIVE') ? setReservation(r) : reload())}
          />
        )}
        {!isAdmin && user && !reservation && (
          <Recommendations filters={filters} zone={zoneFilter} onPick={(s) => setSelectedCode(s.seatCode)} />
        )}
        {!user && (
          <p className="text-sm secondary">
            <Link to="/login" className="font-semibold text-[var(--ink-primary)] underline underline-offset-4">
              Đăng nhập
            </Link>{' '}
            để giữ chỗ trước và nhờ trợ lý tìm ghế.
          </p>
        )}
      </aside>

      <section className="plan p-3 sm:p-6 space-y-6 min-w-0" aria-label="Sơ đồ mặt bằng">
        {floors.map(([floor, floorZones]) => (
          <div key={floor} className="space-y-3">
            <h2 className="text-sm font-semibold secondary">Tầng {floor}</h2>
            <div className="grid gap-6 xl:grid-cols-2">
              {floorZones.map((zone) => (
                <Room
                  key={zone.code}
                  zone={zone}
                  seats={seats.filter((s) => s.zoneCode === zone.code)}
                  filters={filters}
                  selectedCode={selectedCode}
                  mySeatCode={mySeatCode}
                  onSelect={pick}
                  since={since}
                />
              ))}
            </div>
          </div>
        ))}
        <div className="pt-2 border-t border-[var(--grid)]">
          <StatusLegend counts={counts} />
        </div>
      </section>

      {selected && (
        <>
          <div className="fixed inset-0 z-30 bg-[var(--ink-primary)]/20 lg:hidden" onClick={() => setSelectedCode(null)} />
          <div className="drawer">
            <SeatDetail
              seat={selected}
              activeReservation={reservation}
              onBooked={(r) => setReservation(r)}
              onClose={() => setSelectedCode(null)}
            />
          </div>
        </>
      )}
    </div>
  )
}
