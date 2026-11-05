import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { api } from '../lib/api'
import { useSubscription } from '../realtime/RealtimeContext'

const RELOAD_ON = new Set([
  'RESERVATION_CREATED',
  'CHECKED_IN',
  'CHECKED_OUT',
  'RESERVATION_EXPIRED',
  'AWAY_STARTED',
  'RETURNED',
  'AUTO_RELEASED',
  'SEAT_RELEASED_BY_ADMIN',
  'CREDIT_CHANGED',
])

/**
 * The signed-in student's open reservation (PENDING / ACTIVE) or null.
 * Reloads itself (and the user's credit) when a personal notification arrives.
 */
export function useActiveReservation() {
  const { user, refreshUser } = useAuth()
  const [reservation, setReservation] = useState(null)
  const [loading, setLoading] = useState(Boolean(user))
  const [error, setError] = useState(null)

  const reload = useCallback(async () => {
    if (!user) {
      setReservation(null)
      setLoading(false)
      return null
    }
    try {
      const r = await api.activeReservation()
      setReservation(r)
      setError(null)
      return r
    } catch (e) {
      setError(e)
      return null
    } finally {
      setLoading(false)
    }
  }, [user])

  useEffect(() => {
    reload()
  }, [reload])

  useSubscription(
    '/user/queue/notifications',
    (n) => {
      if (!n || !RELOAD_ON.has(n.type)) return
      reload()
      if (n.type === 'CREDIT_CHANGED' || n.type === 'RESERVATION_EXPIRED' || n.type === 'CHECKED_OUT') {
        refreshUser().catch(() => {})
      }
    },
    Boolean(user),
  )

  return { reservation, setReservation, loading, error, reload }
}
