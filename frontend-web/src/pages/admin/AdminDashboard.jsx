import { useEffect, useMemo, useState } from 'react'
import Heatmap from '../../components/charts/Heatmap'
import StatusLegend from '../../components/StatusLegend'
import ZoneBar from '../../components/ZoneBar'
import { useLiveSeats } from '../../hooks/useLiveSeats'
import { api } from '../../lib/api'
import { errorMessage } from '../../lib/errors'
import { formatPercent, formatTime } from '../../lib/format'
import { heatmapRows } from '../../lib/stats'
import { useSubscription } from '../../realtime/RealtimeContext'

function StatTile({ label, value, note }) {
  return (
    <div className="card p-4">
      <p className="label">{label}</p>
      <p className="text-3xl font-semibold mt-1">{value}</p>
      {note && <p className="text-xs secondary mt-1">{note}</p>}
    </div>
  )
}

export default function AdminDashboard() {
  const { zones, seats } = useLiveSeats()
  const [stats, setStats] = useState([])
  const [statsError, setStatsError] = useState(null)
  const [heatZone, setHeatZone] = useState('A')
  const [days, setDays] = useState(7)
  const [events, setEvents] = useState([])

  useEffect(() => {
    const to = new Date()
    const from = new Date(to.getTime() - days * 24 * 3600 * 1000)
    api
      .hourlyOccupancy(from.toISOString(), to.toISOString())
      .then((s) => {
        setStats(s)
        setStatsError(null)
      })
      .catch((e) => setStatsError(errorMessage(e)))
  }, [days])

  // Live activity feed: seat transitions + device ONLINE/OFFLINE.
  useSubscription('/topic/seats', (s) =>
    setEvents((list) => [{ at: new Date(), kind: 'seat', text: `${s.seatCode} → ${s.status}` }, ...list].slice(0, 30)),
  )
  useSubscription('/topic/devices', (d) =>
    setEvents((list) =>
      [{ at: new Date(), kind: d.online ? 'online' : 'offline', text: `${d.deviceId} (${d.seatCode}) ${d.online ? 'ONLINE' : 'OFFLINE'}` }, ...list].slice(0, 30),
    ),
  )

  const counts = useMemo(() => {
    const c = {}
    for (const s of seats) c[s.status] = (c[s.status] || 0) + 1
    return c
  }, [seats])
  const offline = seats.filter((s) => !s.deviceOnline).length
  const inUse = (counts.OCCUPIED || 0) + (counts.AWAY || 0)
  const rows = useMemo(() => heatmapRows(stats, heatZone), [stats, heatZone])

  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-bold">Tổng quan thư viện</h1>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <StatTile label="Chỗ trống" value={`${counts.AVAILABLE || 0}/${seats.length}`} />
        <StatTile label="Tỷ lệ đang sử dụng" value={formatPercent(seats.length ? inUse / seats.length : 0)} note={`${inUse} chỗ có người hoặc tạm vắng`} />
        <StatTile label="Đang chờ check-in" value={counts.RESERVED || 0} />
        <StatTile label="Cảm biến offline" value={offline} note={offline ? 'Ghế có cảm biến offline được coi là trống' : 'Tất cả cảm biến hoạt động'} />
      </div>

      <StatusLegend counts={counts} />

      <div className="grid md:grid-cols-3 gap-3">
        {zones.map((z) => (
          <div key={z.code} className="card p-4 space-y-2">
            <p className="font-semibold">{z.name}</p>
            <ZoneBar zone={z} height={12} />
            <p className="text-sm secondary">
              Trống {z.available} · Đặt {z.reserved} · Có người {z.occupied} · Vắng {z.away}
            </p>
          </div>
        ))}
      </div>

      <div className="grid lg:grid-cols-[1fr_320px] gap-4 items-start">
        <section className="card p-4 space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 className="font-semibold">Mật độ theo giờ (trung bình mỗi giờ)</h2>
            <div className="flex gap-2">
              <select className="input w-auto" value={days} onChange={(e) => setDays(Number(e.target.value))}>
                <option value={7}>7 ngày qua</option>
                <option value={14}>14 ngày qua</option>
                <option value={30}>30 ngày qua</option>
              </select>
              <select className="input w-auto" value={heatZone} onChange={(e) => setHeatZone(e.target.value)}>
                {zones.map((z) => (
                  <option key={z.code} value={z.code}>
                    Khu {z.code}
                  </option>
                ))}
              </select>
            </div>
          </div>
          {statsError ? (
            <p className="text-sm text-[var(--critical)]">{statsError}</p>
          ) : rows.length === 0 ? (
            <p className="text-sm secondary">
              Chưa có dữ liệu thống kê. backend-core ghi lại mật độ mỗi giờ khi hệ thống chạy (hoặc dùng dữ liệu tổng hợp ở
              trang Mô hình AI).
            </p>
          ) : (
            <Heatmap rows={rows} ariaLabel={`Mật độ khu ${heatZone} theo ngày và giờ`} />
          )}
        </section>

        <section className="card p-4">
          <h2 className="font-semibold mb-2">Hoạt động trực tiếp</h2>
          {events.length === 0 ? (
            <p className="text-sm secondary">Đang chờ sự kiện từ cảm biến…</p>
          ) : (
            <ul className="text-sm space-y-1 max-h-96 overflow-y-auto">
              {events.map((e, i) => (
                <li key={i} className="flex gap-2">
                  <span className="muted tabular-nums">{formatTime(e.at.toISOString())}</span>
                  <span className={e.kind === 'offline' ? 'text-[var(--critical)]' : ''}>{e.text}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  )
}
