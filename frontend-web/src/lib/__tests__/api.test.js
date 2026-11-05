import { ApiError, api, onUnauthorized, request, tokenStore } from '../api'
import { errorMessage } from '../errors'

function respond(status, body, contentType = 'application/json') {
  const payload = body == null ? null : JSON.stringify(body)
  return Promise.resolve(new Response(payload, { status, headers: { 'Content-Type': contentType } }))
}

describe('api client', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.stubGlobal('fetch', vi.fn())
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    onUnauthorized(null)
  })

  it('sends the bearer token and JSON body', async () => {
    tokenStore.set('jwt-123')
    fetch.mockReturnValue(respond(201, { id: 'r1', status: 'PENDING' }))
    const r = await api.reserve('A-05')
    expect(r.status).toBe('PENDING')
    const [url, init] = fetch.mock.calls[0]
    expect(url).toBe('/api/v1/reservations')
    expect(init.method).toBe('POST')
    expect(init.headers.Authorization).toBe('Bearer jwt-123')
    expect(JSON.parse(init.body)).toEqual({ seatCode: 'A-05', source: 'APP_MAP' })
  })

  it('drops empty query params', async () => {
    fetch.mockReturnValue(respond(200, []))
    await api.recommendations({ zonePreference: 'A', isQuietZone: undefined, limit: 5 })
    expect(fetch.mock.calls[0][0]).toBe('/api/v1/seats/recommendations?zonePreference=A&limit=5')
  })

  it('returns null for 204', async () => {
    fetch.mockReturnValue(respond(204, null))
    await expect(api.activeReservation()).resolves.toBeNull()
  })

  it('turns RFC 7807 problems into ApiError with the stable code', async () => {
    const problem = { status: 409, title: 'Conflict', code: 'SEAT_NOT_AVAILABLE', detail: 'Seat A-01 is RESERVED' }
    fetch.mockReturnValue(respond(409, problem, 'application/problem+json'))
    const err = await api.reserve('A-01').catch((e) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect(err.status).toBe(409)
    expect(err.code).toBe('SEAT_NOT_AVAILABLE')
    expect(errorMessage(err)).toBe('Ghế này không còn trống.')
  })

  it('calls the unauthorized handler only when a token was sent', async () => {
    const handler = vi.fn()
    onUnauthorized(handler)
    fetch.mockReturnValue(respond(401, { code: 'UNAUTHENTICATED' }))
    await request('/auth/me').catch(() => {})
    expect(handler).not.toHaveBeenCalled()

    tokenStore.set('expired')
    fetch.mockReturnValue(respond(401, { code: 'UNAUTHENTICATED' }))
    await request('/auth/me').catch(() => {})
    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('maps network failures to NETWORK_ERROR', async () => {
    fetch.mockRejectedValue(new TypeError('Failed to fetch'))
    const err = await api.zones().catch((e) => e)
    expect(err.code).toBe('NETWORK_ERROR')
  })
})
