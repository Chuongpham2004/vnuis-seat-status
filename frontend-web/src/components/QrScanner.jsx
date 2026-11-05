import { useEffect, useRef, useState } from 'react'

/**
 * Camera QR scanner using the browser's BarcodeDetector (Chrome / Edge / Android).
 * Calls onResult(text) once, then stops. Where the API is missing the parent
 * falls back to typing the code printed under the QR sticker.
 */
// eslint-disable-next-line react-refresh/only-export-components
export const qrScanSupported = () => typeof window !== 'undefined' && 'BarcodeDetector' in window

export default function QrScanner({ onResult, onClose }) {
  const videoRef = useRef(null)
  const [error, setError] = useState(null)
  const onResultRef = useRef(onResult)
  useEffect(() => {
    onResultRef.current = onResult
  })

  useEffect(() => {
    let stream
    let raf
    let stopped = false
    const detector = new window.BarcodeDetector({ formats: ['qr_code'] })

    async function start() {
      try {
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
        const video = videoRef.current
        if (!video || stopped) return
        video.srcObject = stream
        await video.play()
        const tick = async () => {
          if (stopped) return
          try {
            const codes = await detector.detect(video)
            if (codes.length && codes[0].rawValue) {
              stopped = true
              onResultRef.current(codes[0].rawValue.trim())
              return
            }
          } catch {
            // frame not ready yet
          }
          raf = requestAnimationFrame(tick)
        }
        tick()
      } catch {
        setError('Không mở được camera. Hãy cho phép quyền camera hoặc nhập mã thủ công.')
      }
    }
    start()
    return () => {
      stopped = true
      cancelAnimationFrame(raf)
      stream?.getTracks().forEach((t) => t.stop())
    }
  }, [])

  return (
    <div className="space-y-2">
      {error ? (
        <p className="text-sm text-[var(--critical)]">{error}</p>
      ) : (
        <video ref={videoRef} className="w-full max-w-sm rounded-lg bg-black aspect-square object-cover" muted playsInline />
      )}
      <button className="btn btn-sm" onClick={onClose}>
        Đóng camera
      </button>
    </div>
  )
}
