import { useAuth } from '../auth/AuthContext'
import { useSubscription } from '../realtime/RealtimeContext'
import { useToast } from './Toasts'

// Vietnamese titles for /user/queue/notifications types (the backend message is shown underneath).
const TITLES = {
  RESERVATION_CREATED: ['Đặt chỗ thành công', 'good'],
  CHECKED_IN: ['Đã check-in', 'good'],
  CHECKED_OUT: ['Đã trả chỗ', 'info'],
  RESERVATION_EXPIRED: ['Lượt đặt đã hết hạn', 'critical'],
  AWAY_STARTED: ['Bạn đang tạm vắng', 'warning'],
  RETURNED: ['Chào mừng bạn quay lại', 'good'],
  AUTO_RELEASED: ['Ghế đã được giải phóng', 'critical'],
  CREDIT_CHANGED: ['Điểm uy tín thay đổi', 'info'],
  SEAT_RELEASED_BY_ADMIN: ['Thủ thư đã giải phóng ghế', 'warning'],
}

/** Turns personal STOMP notifications into toasts. Renders nothing. */
export default function NotificationListener() {
  const { user } = useAuth()
  const { push } = useToast()
  useSubscription(
    '/user/queue/notifications',
    (n) => {
      if (!n) return
      const [title, tone] = TITLES[n.type] || [n.type, 'info']
      push({ title, message: n.message, tone, timeout: tone === 'critical' ? 12000 : 7000 })
    },
    Boolean(user),
  )
  return null
}
