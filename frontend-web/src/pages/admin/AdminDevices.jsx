import { useCallback, useEffect, useMemo, useState } from 'react'
import { useToast } from '../../components/Toasts'
import { useLiveSeats } from '../../hooks/useLiveSeats'
import { api, simulator } from '../../lib/api'
import { errorMessage } from '../../lib/errors'
import { formatDateTime } from '../../lib/format'
import { seatStatusMeta } from '../../lib/seatStatus'
import { useSubscription } from '../../realtime/RealtimeContext'

const SIM_ACTIONS = [
  ['sit', 'Ngồi', 'Giả lập sinh viên ngồi xuống (khoảng cách ~20-45 cm)'],
  ['stand', 'Rời đi', 'Giả lập sinh viên rời ghế'],
  ['disconnect', 'Mất kết nối', 'Ngắt TCP không gửi DISCONNECT, broker phát Last Will'],
  ['reconnect', 'Kết nối lại', 'Thiết bị kết nối lại, gửi ONLINE'],
  ['release', 'Thả về tự động', 'Trả ghế về hành vi ngẫu nhiên'],
]

function SimulatorPanel({ seatCode, onSeatCode, seatCodes }) {
  const { push } = useToast()
  const [status, setStatus] = useState(null)
  const [busy, setBusy] = useState(false)

  const refresh = useCallback(() => {
    simulator('/status', 'GET')
      .then(setStatus)
      .catch(() => setStatus(false))
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  const call = async (path, body, label) => {
    setBusy(true)
    try {
      await simulator(path, 'POST', body)
      push({ title: label, tone: 'good', timeout: 3000 })
      refresh()
    } catch (e) {
      push({ title: 'Simulator lỗi', message: errorMessage(e), tone: 'critical' })
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card p-4 space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="font-semibold">Điều khiển iot-simulator (demo)</h2>
        <span className={'chip ' + (status ? 'tone-good' : status === false ? 'tone-critical' : 'tone-neutral')}>
          <span className="dot" />
          {status ? `mode ${status.mode ?? '?'}` : status === false ? 'không kết nối được (cổng 8090)' : '…'}
        </span>
      </div>
      <p className="text-xs secondary">
        Ghế A-01 là cảm biến ESP32 thật nên không điều khiển được ở đây. Chạy backend với profile <code>demo</code> để
        rút ngắn thời gian chờ (walk-in 20 s, tạm vắng 10 s, giải phóng 90 s).
      </p>
      <div className="flex flex-wrap items-center gap-2">
        <select className="input w-auto" value={seatCode} onChange={(e) => onSeatCode(e.target.value)} aria-label="Ghế giả lập">
          {seatCodes.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
        {SIM_ACTIONS.map(([action, label, hint]) => (
          <button
            key={action}
            className="btn btn-sm"
            title={hint}
            disabled={busy || !seatCode}
            onClick={() => call(`/seats/${seatCode}/${action}`, undefined, `${seatCode}: ${label}`)}
          >
            {label}
          </button>
        ))}
      </div>
      <div className="flex flex-wrap items-center gap-2 pt-2 border-t border-[var(--grid)]">
        <span className="label">Kịch bản:</span>
        <button className="btn btn-sm" disabled={busy} onClick={() => call('/scenario/rush_hour', undefined, 'Giờ cao điểm')}>
          Giờ cao điểm
        </button>
        <button className="btn btn-sm" disabled={busy} onClick={() => call('/scenario/mass_leave', undefined, 'Mọi người rời đi')}>
          Mọi người rời đi
        </button>
        <button className="btn btn-sm" disabled={busy} onClick={() => call('/scenario/reset', undefined, 'Đặt lại')}>
          Đặt lại
        </button>
        <span className="label ml-2">Chế độ:</span>
        <button className="btn btn-sm" disabled={busy} onClick={() => call('/mode', { mode: 'ambient' }, 'Chế độ tự động')}>
          Tự động
        </button>
        <button className="btn btn-sm" disabled={busy} onClick={() => call('/mode', { mode: 'manual' }, 'Chế độ thủ công')}>
          Thủ công
        </button>
      </div>
    </section>
  )
}

export default function AdminDevices() {
  const { push } = useToast()
  const { seatsByCode } = useLiveSeats()
  const [devices, setDevices] = useState([])
  const [error, setError] = useState(null)
  const [filter, setFilter] = useState('all')
  const [simSeat, setSimSeat] = useState('A-02')

  useEffect(() => {
    api
      .devices()
      .then(setDevices)
      .catch((e) => setError(errorMessage(e)))
  }, [])

  useSubscription('/topic/devices', (d) =>
    setDevices((list) => list.map((x) => (x.deviceId === d.deviceId ? { ...x, ...d } : x))),
  )

  const release = async (seatCode) => {
    if (!window.confirm(`Giải phóng ghế ${seatCode}?`)) return
    try {
      await api.forceRelease(seatCode)
      push({ title: `Đã giải phóng ${seatCode}`, tone: 'good' })
    } catch (e) {
      push({ title: 'Không giải phóng được', message: errorMessage(e), tone: 'critical' })
    }
  }

  const simCodes = useMemo(() => devices.filter((d) => d.simulated).map((d) => d.seatCode), [devices])
  const shown = devices.filter((d) =>
    filter === 'offline' ? !d.online : filter === 'real' ? !d.simulated : true,
  )
  const onlineCount = devices.filter((d) => d.online).length

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-bold">Thiết bị IoT</h1>
        <p className="secondary text-sm">
          {onlineCount}/{devices.length} cảm biến đang hoạt động. Trạng thái cập nhật trực tiếp qua Last Will (MQTT)
        </p>
      </div>

      <SimulatorPanel seatCode={simSeat} onSeatCode={setSimSeat} seatCodes={simCodes} />

      <div className="flex gap-2" role="group" aria-label="Lọc thiết bị">
        {[
          ['all', 'Tất cả'],
          ['offline', 'Đang offline'],
          ['real', 'Cảm biến thật'],
        ].map(([key, label]) => (
          <button key={key} className="btn btn-sm" aria-pressed={filter === key} style={filter === key ? { background: 'var(--seq-100)' } : undefined} onClick={() => setFilter(key)}>
            {label}
          </button>
        ))}
      </div>

      {error && <p className="text-sm text-[var(--critical)]">{error}</p>}

      <section className="card overflow-x-auto">
        <table className="data">
          <thead>
            <tr>
              <th>Thiết bị</th>
              <th>Ghế</th>
              <th>Kết nối</th>
              <th>Trạng thái ghế</th>
              <th>Khoảng cách</th>
              <th>Heartbeat cuối</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {shown.map((d) => {
              const seat = seatsByCode[d.seatCode]
              const meta = seatStatusMeta(seat?.status)
              return (
                <tr key={d.deviceId}>
                  <td>
                    <span className="font-mono">{d.deviceId}</span>
                    {!d.simulated && <span className="chip ml-2">📡 ESP32</span>}
                  </td>
                  <td className="font-semibold">{d.seatCode}</td>
                  <td>
                    <span className={'chip ' + (d.online ? 'tone-good' : 'tone-critical')}>
                      <span className="dot" />
                      {d.online ? 'Online' : 'Offline'}
                    </span>
                  </td>
                  <td>
                    <span className="flex items-center gap-1.5">
                      <span className={'swatch ' + meta.className} />
                      {meta.icon} {meta.label}
                    </span>
                  </td>
                  <td>{seat?.lastDistanceCm != null ? `${seat.lastDistanceCm.toFixed(1)} cm` : '—'}</td>
                  <td>{formatDateTime(d.lastHeartbeat)}</td>
                  <td className="whitespace-nowrap">
                    {d.simulated && (
                      <button className="btn btn-sm mr-1" onClick={() => setSimSeat(d.seatCode)}>
                        Điều khiển
                      </button>
                    )}
                    {seat && seat.status !== 'AVAILABLE' && (
                      <button className="btn btn-sm btn-danger" onClick={() => release(d.seatCode)}>
                        Giải phóng
                      </button>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </section>
    </div>
  )
}
