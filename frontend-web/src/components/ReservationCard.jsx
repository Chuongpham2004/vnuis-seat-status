import { useCallback, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { useCountdown } from '../hooks/useCountdown'
import { api } from '../lib/api'
import { errorMessage } from '../lib/errors'
import { formatCountdown, formatDuration, formatTime } from '../lib/format'
import { BOOKING_SOURCE, RESERVATION_STATUS, seatStatusMeta } from '../lib/seatStatus'
import QrScanner, { qrScanSupported } from './QrScanner'
import { useToast } from './Toasts'

const lowerFirst = (s) => (s ? s[0].toLowerCase() + s.slice(1) : s)

/**
 * The student's open reservation: countdown to the no-show deadline, dual check-in
 * (QR on the table + sensor presence), check-out and cancel.
 */
export default function ReservationCard({ reservation, seat, onChange }) {
  const { refreshUser } = useAuth()
  const { push } = useToast()
  const [qr, setQr] = useState('')
  const [scanning, setScanning] = useState(false)
  const [busy, setBusy] = useState(null)
  const [error, setError] = useState(null)
  const secondsLeft = useCountdown(reservation.status === 'PENDING' ? reservation.expireAt : null)
  const meta = RESERVATION_STATUS[reservation.status] || { label: reservation.status, tone: 'neutral' }
  const seatMeta = seat ? seatStatusMeta(seat.status) : null

  const run = useCallback(
    async (action, fn, successTitle) => {
      setBusy(action)
      setError(null)
      try {
        const updated = await fn()
        push({ title: successTitle, tone: 'good' })
        onChange?.(updated)
        refreshUser().catch(() => {})
      } catch (e) {
        setError(errorMessage(e))
      } finally {
        setBusy(null)
      }
    },
    [onChange, push, refreshUser],
  )

  const checkIn = (token) => {
    const value = (token ?? qr).trim()
    if (!value) {
      setError('Hãy quét hoặc nhập mã QR dán trên bàn.')
      return
    }
    run('checkin', () => api.checkIn(reservation.id, value), `Check-in thành công ghế ${reservation.seatCode}`)
  }

  const onScan = (text) => {
    setScanning(false)
    setQr(text)
    checkIn(text)
  }

  const urgent = secondsLeft != null && secondsLeft <= 60

  return (
    <section
      className="card p-5 space-y-4 border-l-4"
      style={{ borderLeftColor: 'var(--brass)' }}
      aria-label="Lượt đặt chỗ hiện tại"
    >
      <div>
        <div className="flex items-center justify-between gap-2">
          <p className="text-sm secondary">Ghế của bạn ★</p>
          <span className={'chip tone-' + meta.tone}>
            <span className="dot" />
            {meta.label}
          </span>
        </div>
        <p className="text-4xl font-extrabold tracking-tight mt-1">{reservation.seatCode}</p>
        <p className="text-sm secondary mt-1">
          Đặt qua {lowerFirst(BOOKING_SOURCE[reservation.bookingSource] || reservation.bookingSource)} lúc{' '}
          {formatTime(reservation.reservedAt)}.
          {seatMeta && ` Ghế đang ở trạng thái ${seatMeta.label.toLowerCase()}`}
          {seat && !seat.deviceOnline ? ', cảm biến mất kết nối.' : seatMeta ? '.' : ''}
        </p>
      </div>

      {reservation.status === 'PENDING' && (
        <>
          <div
            className="rounded-[var(--radius-md)] p-4"
            style={{ background: urgent ? 'color-mix(in srgb, var(--critical) 12%, var(--surface-1))' : 'var(--surface-2)' }}
          >
            <p className="text-sm secondary">Còn lại để check-in</p>
            <p className="text-5xl font-extrabold tabular-nums tracking-tight" aria-live="polite">
              {formatCountdown(secondsLeft)}
            </p>
            <p className="text-xs secondary mt-1">
              Hết giờ trước khi check-in sẽ bị trừ 10 điểm uy tín. Check-in cần cả mã QR trên bàn và cảm biến phát hiện
              bạn đang ngồi.
            </p>
          </div>

          <div className="space-y-2">
            <label className="label" htmlFor="qr-token">
              Mã QR trên bàn
            </label>
            <div className="flex flex-wrap gap-2">
              <input
                id="qr-token"
                className="input flex-1 min-w-[12rem] font-mono"
                placeholder="Quét hoặc nhập mã in dưới QR"
                value={qr}
                onChange={(e) => setQr(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && checkIn()}
              />
              {qrScanSupported() && (
                <button className="btn" onClick={() => setScanning((v) => !v)} disabled={Boolean(busy)}>
                  📷 Quét QR
                </button>
              )}
              <button className="btn btn-primary" onClick={() => checkIn()} disabled={Boolean(busy)}>
                {busy === 'checkin' ? 'Đang check-in…' : 'Check-in'}
              </button>
            </div>
            {scanning && <QrScanner onResult={onScan} onClose={() => setScanning(false)} />}
          </div>
        </>
      )}

      {reservation.status === 'ACTIVE' && (
        <div className="rounded-lg p-4 bg-[var(--surface-2)] grid sm:grid-cols-2 gap-2 text-sm">
          <p>
            <span className="label block">Check-in lúc</span>
            {formatTime(reservation.checkInAt)}
          </p>
          <p>
            <span className="label block">Đã ngồi</span>
            {formatDuration(reservation.checkInAt)}
          </p>
          {seat?.status === 'AWAY' && (
            <p className="sm:col-span-2 text-[var(--ink-primary)]">
              ⏸ Cảm biến không thấy bạn ở ghế. Hãy quay lại trước khi hết thời gian tạm vắng, nếu không ghế sẽ tự được
              giải phóng.
            </p>
          )}
        </div>
      )}

      {error && (
        <p className="text-sm text-[var(--critical)]" role="alert">
          {error}
        </p>
      )}

      <div className="flex flex-wrap gap-2">
        {reservation.status === 'ACTIVE' && (
          <button
            className="btn btn-primary"
            disabled={Boolean(busy)}
            onClick={() => run('checkout', () => api.checkOut(reservation.id), 'Đã trả chỗ. Cảm ơn bạn!')}
          >
            {busy === 'checkout' ? 'Đang trả chỗ…' : 'Trả chỗ (check-out)'}
          </button>
        )}
        {reservation.status === 'PENDING' && (
          <button
            className="btn btn-danger"
            disabled={Boolean(busy)}
            onClick={() => run('cancel', () => api.cancel(reservation.id), 'Đã huỷ đặt chỗ')}
          >
            {busy === 'cancel' ? 'Đang huỷ…' : 'Huỷ đặt chỗ'}
          </button>
        )}
      </div>
    </section>
  )
}
