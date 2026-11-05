import { useEffect, useState } from 'react'

/** Seconds left until `targetIso`, ticking once per second; null when there is no target. */
export function useCountdown(targetIso) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!targetIso) return undefined
    const t = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(t)
  }, [targetIso])
  if (!targetIso) return null
  return Math.max(0, Math.round((new Date(targetIso).getTime() - now) / 1000))
}
