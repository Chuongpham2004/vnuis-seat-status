import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * Width of a container element, kept up to date with ResizeObserver.
 * Returns a callback ref, so it keeps working when the element is swapped.
 */
export function useWidth(initial = 640) {
  const [width, setWidth] = useState(initial)
  const observer = useRef(null)

  const ref = useCallback((el) => {
    observer.current?.disconnect()
    observer.current = null
    if (!el || typeof ResizeObserver === 'undefined') return
    const ro = new ResizeObserver((entries) => {
      const w = Math.floor(entries[0].contentRect.width)
      if (w > 0) setWidth(w)
    })
    ro.observe(el)
    observer.current = ro
  }, [])

  useEffect(() => () => observer.current?.disconnect(), [])
  return [ref, width]
}
