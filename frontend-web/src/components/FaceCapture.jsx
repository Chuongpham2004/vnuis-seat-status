import { useEffect, useRef, useState } from 'react'

// eslint-disable-next-line react-refresh/only-export-components
export const STEP_TEXT = {
  CENTER: { title: 'Nhìn thẳng vào camera', arrow: '●' },
  LEFT: { title: 'Từ từ quay đầu sang TRÁI', arrow: '←' },
  RIGHT: { title: 'Từ từ quay đầu sang PHẢI', arrow: '→' },
}

const STEP_MS = 2400 // time the student gets per step
const SHOTS_AT = [1000, 1600, 2200] // frames captured inside each step (3 x 3 steps = 9 <= 12)

/**
 * Captures base64 JPEG frames for a head-turn challenge.
 * The preview is mirrored like a selfie, but frames are taken from the raw video, which is
 * what face-service expects ("LEFT" = the student's own left).
 */
// eslint-disable-next-line react-refresh/only-export-components
export function grabFrame(video, maxWidth = 480, quality = 0.82) {
  const scale = Math.min(1, maxWidth / video.videoWidth)
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(video.videoWidth * scale)
  canvas.height = Math.round(video.videoHeight * scale)
  canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height)
  return canvas.toDataURL('image/jpeg', quality).split(',')[1]
}

export default function FaceCapture({ steps, onDone, onCancel }) {
  const videoRef = useRef(null)
  const streamRef = useRef(null)
  const timers = useRef([])
  const [ready, setReady] = useState(false)
  const [error, setError] = useState(null)
  const [current, setCurrent] = useState(-1) // index into steps while recording
  const [progress, setProgress] = useState(0)
  const onDoneRef = useRef(onDone)
  useEffect(() => {
    onDoneRef.current = onDone
  })

  useEffect(() => {
    let cancelled = false
    async function open() {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: 'user', width: { ideal: 640 }, height: { ideal: 480 } },
          audio: false,
        })
        if (cancelled) {
          stream.getTracks().forEach((t) => t.stop())
          return
        }
        streamRef.current = stream
        const video = videoRef.current
        video.srcObject = stream
        await video.play()
        if (!cancelled) setReady(true)
      } catch {
        if (!cancelled) setError('Không mở được camera trước. Hãy cho phép quyền camera cho trang này.')
      }
    }
    open()
    const pending = timers.current
    return () => {
      cancelled = true
      pending.forEach(clearTimeout)
      streamRef.current?.getTracks().forEach((t) => t.stop())
    }
  }, [])

  const record = () => {
    const frames = []
    const later = (ms, fn) => timers.current.push(setTimeout(fn, ms))
    steps.forEach((step, i) => {
      const t0 = i * STEP_MS
      later(t0, () => {
        setCurrent(i)
        setProgress(0)
      })
      SHOTS_AT.forEach((at, k) =>
        later(t0 + at, () => {
          const video = videoRef.current
          if (video?.videoWidth) frames.push({ step, image: grabFrame(video) })
          setProgress((k + 1) / SHOTS_AT.length)
        }),
      )
    })
    later(steps.length * STEP_MS, () => {
      streamRef.current?.getTracks().forEach((t) => t.stop())
      setCurrent(steps.length)
      onDoneRef.current(frames)
    })
  }

  const step = current >= 0 && current < steps.length ? steps[current] : null
  const text = step ? STEP_TEXT[step] : null

  return (
    <div className="space-y-3">
      {error ? (
        <p className="text-sm text-[var(--critical)]" role="alert">
          {error}
        </p>
      ) : (
        <div className="relative mx-auto w-full max-w-xs aspect-[3/4] overflow-hidden rounded-[var(--radius-lg)] bg-black">
          <video
            ref={videoRef}
            className="absolute inset-0 h-full w-full object-cover -scale-x-100"
            muted
            playsInline
            aria-label="Hình ảnh camera"
          />
          {/* oval guide */}
          <div
            className={
              'absolute left-1/2 top-[45%] h-[62%] w-[68%] -translate-x-1/2 -translate-y-1/2 rounded-[50%] border-4 ' +
              (step ? 'border-[var(--good)]' : 'border-white/70')
            }
            aria-hidden="true"
          />
          {text && (
            <div className="absolute inset-x-0 bottom-0 bg-black/60 p-3 text-center text-white">
              <p className="text-3xl leading-none" aria-hidden="true">
                {text.arrow}
              </p>
              <p className="mt-1 font-semibold" aria-live="assertive">
                Bước {current + 1}/{steps.length}: {text.title}
              </p>
              <div className="mt-2 h-1.5 rounded bg-white/25">
                <div className="h-full rounded bg-[var(--good)] transition-all" style={{ width: `${progress * 100}%` }} />
              </div>
            </div>
          )}
        </div>
      )}

      {current < 0 && !error && (
        <div className="space-y-2 text-sm">
          <p className="secondary">
            Đặt khuôn mặt vào khung oval, đủ ánh sáng, chỉ một người trước camera. Bạn sẽ làm lần lượt:{' '}
            <strong>{steps.map((s) => STEP_TEXT[s].title.toLowerCase()).join(' → ')}</strong>.
          </p>
          <button className="btn btn-primary w-full" onClick={record} disabled={!ready}>
            {ready ? 'Bắt đầu' : 'Đang mở camera…'}
          </button>
        </div>
      )}
      {current >= 0 && current < steps.length && <p className="text-center text-sm muted">Giữ nguyên tư thế khi thanh tiến trình chạy.</p>}
      {onCancel && current < 0 && (
        <button className="btn btn-sm w-full" onClick={onCancel}>
          Huỷ
        </button>
      )}
    </div>
  )
}
