import { useMemo, useState } from 'react'
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
import { formatPercent, formatTime } from '../lib/format'
import { filterSeats, seatGrid } from '../lib/seats'

function Toggle({ checked, onChange, children }) {
  return (
    <label className="chip cursor-pointer select-none" style={checked ? { background: 'var(--seq-100)' } : undefined}>
      <input type="checkbox" className="sr-only" checked={checked} onChange={(e) => onChange(e.target.checked)} />
      {checked ? '✓ ' : ''}
      {children}
    </label>
  )
}

function ZoneSection({ zone, seats, filters, selectedCode, mySeatCode, onSelect }) {
  const grid = seatGrid(seats)
  const matching = new Set(filterSeats(seats, filters).map((s) => s.seatCode))
  return (
    <section className="card p-4 space-y-3" aria-labelledby={`zone-${zone.code}`}>
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <div>
          <h2 id={`zone-${zone.code}`} className="font-semibold">
            {zone.name}
          </h2>
          <p className="text-xs muted">
            Tầng {zone.floorNumber}
            {zone.quietZone ? ' · yên tĩnh' : ''} · {zone.totalSeats} chỗ
            {zone.offlineDevices > 0 ? ` · ${zone.offlineDevices} cảm biến offline` : ''}
          </p>
        </div>
        <p className="text-sm">
          <span className="font-bold text-lg">{zone.available}</span>
          <span className="secondary"> chỗ trống · đang dùng {formatPercent(zone.occupancyRate)}</span>
        </p>
      </div>
      <ZoneBar zone={zone} />
      <div className="grid gap-2" style={{ gridTemplateColumns: `repeat(${grid.columns || 1}, minmax(0, 1fr))` }}>
        {grid.cells.map((seat) => (
          <div key={seat.seatCode} style={{ gridColumn: seat.coordX + 1, gridRow: seat.coordY + 1 }}>
            <SeatTile
              seat={seat}
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
  const { zones, seats, seatsByCode, loading, error, lastEventAt } = useLiveSeats()
  const { reservation, setReservation, reload } = useActiveReservation()
  const [selectedCode, setSelectedCode] = useState(null)
  const [zoneFilter, setZoneFilter] = useState('')
  const [filters, setFilters] = useState({ quiet: false, power: false, type: '', onlyAvailable: false })

  const counts = useMemo(() => {
    const c = {}
    for (const s of seats) c[s.status] = (c[s.status] || 0) + 1
    return c
  }, [seats])

  const selected = selectedCode ? seatsByCode[selectedCode] : null
  const shownZones = zones.filter((z) => !zoneFilter || z.code === zoneFilter)
  const mySeatCode = reservation?.seatCode

  if (loading) return <p className="muted">Đang tải bản đồ ghế…</p>
  if (error && !seats.length) {
    return (
      <div className="card p-6">
        <p className="font-semibold">Không tải được dữ liệu ghế</p>
        <p className="secondary text-sm">{errorMessage(error)} Kiểm tra backend-core đang chạy ở cổng 8080.</p>
      </div>
    )
  }

  const totalAvailable = zones.reduce((n, z) => n + z.available, 0)

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold">Bản đồ chỗ ngồi</h1>
          <p className="secondary text-sm">
            {totalAvailable}/{seats.length} chỗ đang trống · cập nhật trực tiếp từ cảm biến
            {lastEventAt && ` · sự kiện gần nhất ${formatTime(lastEventAt.toISOString())}`}
          </p>
        </div>
        {!user && (
          <p className="text-sm secondary">
            <Link to="/login" className="text-[var(--accent)] font-semibold">
              Đăng nhập
            </Link>{' '}
            để đặt chỗ và dùng trợ lý AI.
          </p>
        )}
      </div>

      {/* Filters: one row above the content they scope */}
      <div className="flex flex-wrap items-center gap-2" role="group" aria-label="Bộ lọc">
        <select className="input w-auto" value={zoneFilter} onChange={(e) => setZoneFilter(e.target.value)}>
          <option value="">Tất cả khu</option>
          {zones.map((z) => (
            <option key={z.code} value={z.code}>
              {z.name}
            </option>
          ))}
        </select>
        <select
          className="input w-auto"
          value={filters.type}
          onChange={(e) => setFilters((f) => ({ ...f, type: e.target.value }))}
        >
          <option value="">Mọi loại ghế</option>
          <option value="SINGLE">Ghế đơn</option>
          <option value="GROUP_TABLE">Bàn nhóm</option>
        </select>
        <Toggle checked={filters.onlyAvailable} onChange={(v) => setFilters((f) => ({ ...f, onlyAvailable: v }))}>
          Chỉ ghế trống
        </Toggle>
        <Toggle checked={filters.power} onChange={(v) => setFilters((f) => ({ ...f, power: v }))}>
          ⚡ Có ổ điện
        </Toggle>
        <Toggle checked={filters.quiet} onChange={(v) => setFilters((f) => ({ ...f, quiet: v }))}>
          🤫 Yên tĩnh
        </Toggle>
      </div>

      <StatusLegend counts={counts} />

      <div className="grid lg:grid-cols-[1fr_320px] gap-4 items-start">
        <div className="space-y-4">
          {shownZones.map((zone) => (
            <ZoneSection
              key={zone.code}
              zone={zone}
              seats={seats.filter((s) => s.zoneCode === zone.code)}
              filters={filters}
              selectedCode={selectedCode}
              mySeatCode={mySeatCode}
              onSelect={(s) => setSelectedCode((c) => (c === s.seatCode ? null : s.seatCode))}
            />
          ))}
        </div>

        <aside className="space-y-4 lg:sticky lg:top-20">
          {reservation && !isAdmin && (
            <ReservationCard
              reservation={reservation}
              seat={seatsByCode[reservation.seatCode]}
              onChange={(r) => (r && (r.status === 'PENDING' || r.status === 'ACTIVE') ? setReservation(r) : reload())}
            />
          )}
          {selected ? (
            <SeatDetail
              seat={selected}
              activeReservation={reservation}
              onBooked={(r) => setReservation(r)}
              onClose={() => setSelectedCode(null)}
            />
          ) : (
            <div className="card p-4 text-sm secondary">Chọn một ghế trên bản đồ để xem chi tiết và đặt chỗ.</div>
          )}
          {!isAdmin && (
            <Recommendations filters={filters} zone={zoneFilter} onPick={(s) => setSelectedCode(s.seatCode)} />
          )}
        </aside>
      </div>
    </div>
  )
}
