const TZ = 'Asia/Ho_Chi_Minh'

const timeFmt = new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit', timeZone: TZ })
const dateTimeFmt = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  timeZone: TZ,
})
// Locale-independent parts for computing (not displaying) local hour and date.
const partsFmt = new Intl.DateTimeFormat('en-US', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  hourCycle: 'h23',
  timeZone: TZ,
})

function localParts(iso) {
  const out = {}
  for (const p of partsFmt.formatToParts(new Date(iso))) out[p.type] = p.value
  return out
}
const weekdayFmt = new Intl.DateTimeFormat('vi-VN', { weekday: 'short', day: '2-digit', month: '2-digit', timeZone: TZ })

export function formatTime(iso) {
  return iso ? timeFmt.format(new Date(iso)) : '—'
}

export function formatDateTime(iso) {
  return iso ? dateTimeFmt.format(new Date(iso)) : '—'
}

export function formatDay(iso) {
  return iso ? weekdayFmt.format(new Date(iso)) : '—'
}

/** Hour of day (0-23) in the library's time zone. */
export function hourOf(iso) {
  return Number(localParts(iso).hour) % 24
}

/** Calendar date key (YYYY-MM-DD) in the library's time zone. */
export function dayKey(iso) {
  const p = localParts(iso)
  return `${p.year}-${p.month}-${p.day}`
}

/** 125 -> "02:05"; 3725 -> "1:02:05". */
export function formatCountdown(totalSeconds) {
  if (totalSeconds == null || Number.isNaN(totalSeconds)) return '—'
  const s = Math.max(0, Math.floor(totalSeconds))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  const mm = String(m).padStart(2, '0')
  const ss = String(sec).padStart(2, '0')
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`
}

export function formatPercent(rate, digits = 0) {
  if (rate == null || Number.isNaN(rate)) return '—'
  return `${(rate * 100).toFixed(digits)}%`
}

export function formatDuration(fromIso, toIso) {
  if (!fromIso) return '—'
  const ms = (toIso ? new Date(toIso) : new Date()) - new Date(fromIso)
  const minutes = Math.max(0, Math.round(ms / 60000))
  if (minutes < 60) return `${minutes} phút`
  return `${Math.floor(minutes / 60)} giờ ${minutes % 60} phút`
}
