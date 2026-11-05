// Thin fetch wrapper for backend-core (/api/v1). Errors are RFC 7807 problems with a stable `code`.

const API_BASE = (import.meta.env.VITE_API_BASE || '') + '/api/v1'
const TOKEN_KEY = 'library.accessToken'

let unauthorizedHandler = null

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

/** Called when a request that carried a token comes back 401 (expired / revoked). */
export function onUnauthorized(handler) {
  unauthorizedHandler = handler
}

export class ApiError extends Error {
  constructor(status, code, detail, body) {
    super(detail || code || `HTTP ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.detail = detail
    this.body = body
  }
}

function buildQuery(params) {
  if (!params) return ''
  const qs = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') qs.append(key, String(value))
  }
  const s = qs.toString()
  return s ? `?${s}` : ''
}

export async function request(path, { method = 'GET', body, params, signal, auth = true } = {}) {
  const headers = { Accept: 'application/json' }
  const token = auth ? tokenStore.get() : null
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  let res
  try {
    res = await fetch(API_BASE + path + buildQuery(params), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    })
  } catch (e) {
    if (e.name === 'AbortError') throw e
    throw new ApiError(0, 'NETWORK_ERROR', 'Không kết nối được tới máy chủ')
  }

  if (res.status === 204) return null
  const text = await res.text()
  let data = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = text
    }
  }
  if (!res.ok) {
    if (res.status === 401 && token && unauthorizedHandler) unauthorizedHandler()
    const problem = data && typeof data === 'object' ? data : {}
    throw new ApiError(res.status, problem.code, problem.detail || problem.title, problem)
  }
  return data
}

export const api = {
  // auth
  login: (username, password) =>
    request('/auth/login', { method: 'POST', body: { username, password }, auth: false }),
  me: () => request('/auth/me'),

  // seats & zones (public)
  zones: () => request('/zones'),
  seats: (params) => request('/seats', { params }),
  seat: (code) => request(`/seats/${encodeURIComponent(code)}`),
  recommendations: (params) => request('/seats/recommendations', { params }),

  // reservations
  reserve: (seatCode, source = 'APP_MAP') =>
    request('/reservations', { method: 'POST', body: { seatCode, source } }),
  activeReservation: () => request('/reservations/me/active'),
  myReservations: (limit = 20) => request('/reservations/me', { params: { limit } }),
  checkIn: (id, qrCodeToken) =>
    request(`/reservations/${id}/check-in`, { method: 'POST', body: { qrCodeToken } }),
  checkOut: (id) => request(`/reservations/${id}/check-out`, { method: 'POST' }),
  cancel: (id) => request(`/reservations/${id}/cancel`, { method: 'POST' }),

  // AI (proxied to ai-service)
  forecast: (zoneCode, hours = 24) => request('/ai/forecast', { params: { zoneCode, hours } }),
  aiMetrics: () => request('/ai/metrics'),
  chat: (message, sessionId) => request('/ai/chat', { method: 'POST', body: { message, sessionId } }),
  aiSynthetic: (days = 180) => request('/ai/admin/synthetic', { method: 'POST', body: { days } }),
  aiTrain: () => request('/ai/admin/train', { method: 'POST', body: {} }),

  // admin
  devices: () => request('/admin/devices'),
  users: (q, limit = 50) => request('/admin/users', { params: { q, limit } }),
  adjustCredit: (id, delta, reason, clearBan) =>
    request(`/admin/users/${id}/credit`, { method: 'PATCH', body: { delta, reason, clearBan } }),
  forceRelease: (seatCode) =>
    request(`/admin/seats/${encodeURIComponent(seatCode)}/force-release`, { method: 'POST' }),
  seatQrCodes: (zone) => request('/admin/seats/qr', { params: { zone } }),
  hourlyOccupancy: (from, to) => request('/admin/occupancy/hourly', { params: { from, to } }),
}

// iot-simulator control API (proxied under /sim by Vite and nginx). Demo only.
const SIM_BASE = import.meta.env.VITE_SIMULATOR_BASE || '/sim'

export async function simulator(path, method = 'POST', body) {
  let res
  try {
    res = await fetch(SIM_BASE + path, {
      method,
      headers: body ? { 'Content-Type': 'application/json' } : undefined,
      body: body ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new ApiError(0, 'SIMULATOR_UNREACHABLE', 'Không kết nối được iot-simulator')
  }
  const data = await res.json().catch(() => null)
  if (!res.ok) {
    throw new ApiError(res.status, 'SIMULATOR_ERROR', (data && data.detail) || `HTTP ${res.status}`, data)
  }
  return data
}
