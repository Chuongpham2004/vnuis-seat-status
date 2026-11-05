// Seat states use the reserved status palette and always ship with an icon + label,
// so colour never carries the meaning alone.

export const SEAT_STATUS = {
  AVAILABLE: { label: 'Còn trống', short: 'Trống', icon: '✓', className: 'status-available' },
  RESERVED: { label: 'Đã đặt', short: 'Đặt', icon: '◷', className: 'status-reserved' },
  OCCUPIED: { label: 'Có người', short: 'Có người', icon: '●', className: 'status-occupied' },
  AWAY: { label: 'Tạm vắng', short: 'Vắng', icon: '⏸', className: 'status-away' },
}

export const SEAT_STATUS_ORDER = ['AVAILABLE', 'RESERVED', 'OCCUPIED', 'AWAY']

export function seatStatusMeta(status) {
  return SEAT_STATUS[status] || { label: status || '—', short: status || '—', icon: '?', className: '' }
}

export const RESERVATION_STATUS = {
  PENDING: { label: 'Chờ check-in', tone: 'warning' },
  ACTIVE: { label: 'Đang sử dụng', tone: 'good' },
  COMPLETED: { label: 'Hoàn thành', tone: 'neutral' },
  EXPIRED_NO_SHOW: { label: 'Không đến (quá hạn)', tone: 'critical' },
  AUTO_RELEASED_AWAY: { label: 'Tự giải phóng (vắng lâu)', tone: 'serious' },
  CANCELLED: { label: 'Đã huỷ', tone: 'neutral' },
}

export const BOOKING_SOURCE = {
  APP_MAP: 'Bản đồ',
  AI_CHATBOT: 'Trợ lý AI',
  WALK_IN: 'Ngồi trực tiếp',
}

export const SEAT_TYPE = {
  SINGLE: 'Ghế đơn',
  GROUP_TABLE: 'Bàn nhóm',
}

/** Zone identity colours: categorical slots 1-3 (validated all-pairs, light surface). */
export const ZONE_COLORS = { A: 'var(--series-1)', B: 'var(--series-2)', C: 'var(--series-3)' }

export function zoneColor(code) {
  return ZONE_COLORS[code] || 'var(--ink-muted)'
}
