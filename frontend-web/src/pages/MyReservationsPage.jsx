import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import ReservationCard from '../components/ReservationCard'
import { useActiveReservation } from '../hooks/useActiveReservation'
import { api } from '../lib/api'
import { errorMessage } from '../lib/errors'
import { formatDateTime, formatDuration } from '../lib/format'
import { BOOKING_SOURCE, RESERVATION_STATUS } from '../lib/seatStatus'
import { useSubscription } from '../realtime/RealtimeContext'

function CreditMeter({ score }) {
  // Meter: the fill carries severity, the track is a lighter step of the same ramp.
  const tone = score >= 80 ? 'var(--good)' : score >= 60 ? 'var(--warning)' : 'var(--critical)'
  return (
    <div className="h-2.5 w-full rounded-full bg-[var(--surface-2)] overflow-hidden" role="img" aria-label={`${score}/100`}>
      <div className="h-full rounded-full" style={{ width: `${Math.max(0, Math.min(100, score))}%`, background: tone }} />
    </div>
  )
}

export default function MyReservationsPage() {
  const { user } = useAuth()
  const { reservation, setReservation, reload, loading } = useActiveReservation()
  const [liveSeat, setSeat] = useState(null)
  const [history, setHistory] = useState([])
  const [error, setError] = useState(null)

  const loadHistory = useCallback(() => {
    api
      .myReservations(30)
      .then(setHistory)
      .catch((e) => setError(errorMessage(e)))
  }, [])

  useEffect(() => {
    loadHistory()
  }, [loadHistory])

  // Live seat state (presence / AWAY) for the reserved seat.
  useEffect(() => {
    if (!reservation?.seatCode) return
    api.seat(reservation.seatCode).then(setSeat).catch(() => {})
  }, [reservation?.seatCode])

  // Only trust the cached seat while it belongs to the current reservation.
  const seat = liveSeat && liveSeat.seatCode === reservation?.seatCode ? liveSeat : null

  useSubscription(
    '/topic/seats',
    (s) => {
      if (s?.seatCode === reservation?.seatCode) setSeat(s)
    },
    Boolean(reservation),
  )
  useSubscription('/user/queue/notifications', () => loadHistory())

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">Chỗ của tôi</h1>

      <div className="grid md:grid-cols-[1fr_300px] gap-4 items-start">
        <div>
          {loading ? (
            <p className="muted">Đang tải…</p>
          ) : reservation ? (
            <ReservationCard
              reservation={reservation}
              seat={seat}
              onChange={(r) => {
                if (r && (r.status === 'PENDING' || r.status === 'ACTIVE')) setReservation(r)
                else reload()
                loadHistory()
              }}
            />
          ) : (
            <div className="card p-5 space-y-2">
              <p className="font-semibold">Bạn chưa giữ chỗ nào.</p>
              <p className="text-sm secondary">
                Chọn ghế trên{' '}
                <Link to="/" className="text-[var(--accent)] font-semibold">
                  bản đồ
                </Link>{' '}
                hoặc nhờ{' '}
                <Link to="/assistant" className="text-[var(--accent)] font-semibold">
                  trợ lý AI
                </Link>{' '}
                tìm giúp.
              </p>
            </div>
          )}
        </div>

        <section className="card p-5 space-y-3">
          <p className="label">Điểm uy tín</p>
          <p className="text-3xl font-bold">
            {user.creditScore}
            <span className="text-base secondary font-normal">/100</span>
          </p>
          <CreditMeter score={user.creditScore} />
          <ul className="text-xs secondary space-y-1">
            <li>+2 khi check-in đúng hạn và trả chỗ</li>
            <li>−10 khi đặt mà không đến</li>
            <li>Dưới 60 điểm: tạm khoá đặt chỗ 3 ngày</li>
          </ul>
          {!user.canBook && (
            <p className="text-sm text-[var(--critical)]">
              Đang bị khoá đặt chỗ{user.bannedUntil ? ` đến ${formatDateTime(user.bannedUntil)}` : ''}.
            </p>
          )}
        </section>
      </div>

      <section className="card overflow-x-auto">
        <h2 className="font-semibold p-4 pb-2">Lịch sử đặt chỗ</h2>
        {error && <p className="px-4 text-sm text-[var(--critical)]">{error}</p>}
        {history.length === 0 ? (
          <p className="px-4 pb-4 text-sm secondary">Chưa có lượt đặt nào.</p>
        ) : (
          <table className="data">
            <thead>
              <tr>
                <th>Ghế</th>
                <th>Nguồn</th>
                <th>Đặt lúc</th>
                <th>Thời gian ngồi</th>
                <th>Kết quả</th>
              </tr>
            </thead>
            <tbody>
              {history.map((r) => {
                const meta = RESERVATION_STATUS[r.status] || { label: r.status, tone: 'neutral' }
                return (
                  <tr key={r.id}>
                    <td className="font-semibold">{r.seatCode}</td>
                    <td>{BOOKING_SOURCE[r.bookingSource] || r.bookingSource}</td>
                    <td>{formatDateTime(r.reservedAt)}</td>
                    <td>{r.checkInAt ? formatDuration(r.checkInAt, r.checkOutAt) : '—'}</td>
                    <td>
                      <span className={'chip tone-' + meta.tone}>
                        <span className="dot" />
                        {meta.label}
                      </span>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </section>
    </div>
  )
}
