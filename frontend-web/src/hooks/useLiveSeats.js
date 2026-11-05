import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api } from '../lib/api'
import { indexSeats, mergeSeat, summariseZones } from '../lib/seats'
import { useRealtimeStatus, useSubscription } from '../realtime/RealtimeContext'

/** Seats + zones from REST, kept live by /topic/seats. Reloads after every reconnect. */
export function useLiveSeats() {
  const [zones, setZones] = useState([])
  const [seatsByCode, setSeatsByCode] = useState({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [lastEventAt, setLastEventAt] = useState(null)
  const status = useRealtimeStatus()
  const wasConnected = useRef(false)

  const load = useCallback(async () => {
    try {
      const [z, s] = await Promise.all([api.zones(), api.seats()])
      setZones(z)
      // Keep any newer realtime update that raced with the REST call.
      setSeatsByCode((prev) => ({ ...indexSeats(s), ...pickNewer(prev, s) }))
      setError(null)
    } catch (e) {
      setError(e)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  // Events are lost while the socket is down: re-sync on every reconnect.
  useEffect(() => {
    if (status === 'connected') {
      if (wasConnected.current) load()
      wasConnected.current = true
    }
  }, [status, load])

  useSubscription('/topic/seats', (seat) => {
    setSeatsByCode((prev) => mergeSeat(prev, seat))
    setLastEventAt(new Date())
  })

  const zoneSummaries = useMemo(() => summariseZones(zones, seatsByCode), [zones, seatsByCode])
  const seats = useMemo(
    () => Object.values(seatsByCode).sort((a, b) => a.seatCode.localeCompare(b.seatCode)),
    [seatsByCode],
  )

  return { zones: zoneSummaries, seats, seatsByCode, loading, error, reload: load, lastEventAt }
}

function pickNewer(prev, fresh) {
  const freshByCode = indexSeats(fresh)
  const out = {}
  for (const [code, seat] of Object.entries(prev)) {
    const f = freshByCode[code]
    if (f && seat.updatedAt && f.updatedAt && new Date(seat.updatedAt) > new Date(f.updatedAt)) out[code] = seat
  }
  return out
}
