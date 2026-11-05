import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { api } from '../lib/api'
import { errorMessage } from '../lib/errors'
import { formatDateTime } from '../lib/format'
import { SEAT_TYPE, seatStatusMeta } from '../lib/seatStatus'
import { useToast } from './Toasts'

/** Side panel for the selected seat: details, booking, admin force-release. */
export default function SeatDetail({ seat, activeReservation, onBooked, onClose }) {
  const { user, isAdmin, refreshUser } = useAuth()
  const { push } = useToast()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const meta = seatStatusMeta(seat.status)

  const book = async () => {
    setBusy(true)
    setError(null)
    try {
      const r = await api.reserve(seat.seatCode)
      push({ title: `Đã giữ ghế ${seat.seatCode}`, message: 'Hãy đến check-in trước khi hết giờ.', tone: 'good' })
      onBooked?.(r)
    } catch (e) {
      setError(errorMessage(e))
      if (e.code === 'USER_BANNED' || e.code === 'INSUFFICIENT_CREDIT') refreshUser().catch(() => {})
    } finally {
      setBusy(false)
    }
  }

  const forceRelease = async () => {
    if (!window.confirm(`Giải phóng ghế ${seat.seatCode}? Lượt đặt/sử dụng hiện tại sẽ bị kết thúc.`)) return
    setBusy(true)
    setError(null)
    try {
      await api.forceRelease(seat.seatCode)
      push({ title: `Đã giải phóng ghế ${seat.seatCode}`, tone: 'good' })
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  let action = null
  if (!user) {
    action = (
      <Link to="/login" className="btn btn-primary w-full">
        Đăng nhập để đặt ghế
      </Link>
    )
  } else if (isAdmin) {
    action =
      seat.status !== 'AVAILABLE' ? (
        <button className="btn btn-danger w-full" onClick={forceRelease} disabled={busy}>
          Giải phóng ghế (thủ thư)
        </button>
      ) : null
  } else if (activeReservation) {
    action =
      activeReservation.seatCode === seat.seatCode ? (
        <p className="text-sm secondary">Đây là ghế bạn đang giữ.</p>
      ) : (
        <p className="text-sm secondary">
          Bạn đang giữ ghế <strong>{activeReservation.seatCode}</strong>. Mỗi sinh viên chỉ giữ một ghế một lúc.
        </p>
      )
  } else if (!user.canBook) {
    action = (
      <p className="text-sm text-[var(--critical)]">
        Tài khoản chưa thể đặt chỗ (điểm uy tín {user.creditScore}
        {user.bannedUntil ? `, bị khoá đến ${formatDateTime(user.bannedUntil)}` : ''}).
      </p>
    )
  } else if (seat.status === 'AVAILABLE') {
    action = (
      <button className="btn btn-primary w-full" onClick={book} disabled={busy}>
        {busy ? 'Đang đặt…' : `Đặt ghế ${seat.seatCode}`}
      </button>
    )
  } else {
    action = <p className="text-sm secondary">Ghế đang không trống.</p>
  }

  return (
    <section className="p-5 space-y-5" aria-label={`Chi tiết ghế ${seat.seatCode}`}>
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm secondary">{seat.quietZone ? 'Khu yên tĩnh' : `Khu ${seat.zoneCode}`}</p>
          <p className="text-5xl font-extrabold tracking-tight leading-none mt-1">{seat.seatCode}</p>
        </div>
        <button className="btn btn-sm" onClick={onClose} aria-label="Đóng chi tiết ghế">
          Đóng
        </button>
      </div>
      <p className="flex items-center gap-2">
        <span className={'swatch ' + meta.className} />
        <span className="font-semibold">
          {meta.icon} {meta.label}
        </span>
        {!seat.deviceOnline && <span className="text-sm secondary">(cảm biến mất kết nối)</span>}
      </p>
      <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm border-y border-[var(--grid)] py-4">
        <dt className="secondary">Loại</dt>
        <dd>{SEAT_TYPE[seat.seatType] || seat.seatType}</dd>
        <dt className="secondary">Ổ điện</dt>
        <dd>{seat.hasPowerOutlet ? 'Có' : 'Không'}</dd>
        <dt className="secondary">Cảm biến</dt>
        <dd>
          {seat.simulatedDevice ? 'Giả lập' : 'ESP32 thật'}, {seat.deviceOnline ? 'đang hoạt động' : 'mất kết nối'}
        </dd>
        {seat.lastDistanceCm != null && (
          <>
            <dt className="secondary">Khoảng cách đo</dt>
            <dd className="tabular-nums">{seat.lastDistanceCm.toFixed(1)} cm</dd>
          </>
        )}
        <dt className="secondary">Cập nhật</dt>
        <dd>{formatDateTime(seat.updatedAt)}</dd>
      </dl>
      {error && (
        <p className="text-sm text-[var(--critical)]" role="alert">
          {error}
        </p>
      )}
      {action}
    </section>
  )
}
