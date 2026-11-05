import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { api } from '../lib/api'
import { errorMessage } from '../lib/errors'
import { formatTime } from '../lib/format'

const SESSION_KEY = 'library.chatSession'
const EXAMPLES = [
  'Tìm cho mình một chỗ yên tĩnh có ổ cắm',
  'Khu B lúc 15h có đông không?',
  'Mình cần bàn nhóm cho 4 người',
  'Find me a quiet seat with a power outlet',
]

const TOOL_LABELS = {
  find_optimal_seats: 'Tìm ghế phù hợp',
  get_occupancy_forecast: 'Dự báo mật độ',
  reserve_seat: 'Đặt ghế',
}

function ToolCalls({ calls }) {
  if (!calls?.length) return null
  return (
    <details className="mt-2 text-xs">
      <summary className="cursor-pointer muted">Công cụ đã gọi ({calls.length})</summary>
      <ul className="mt-1 space-y-1">
        {calls.map((c, i) => (
          <li key={i} className="rounded bg-[var(--surface-2)] p-2 font-mono break-all">
            <span className={c.ok ? 'text-[var(--good-text)]' : 'text-[var(--critical)]'}>{c.ok ? '✓' : '✗'}</span>{' '}
            {TOOL_LABELS[c.name] || c.name}({JSON.stringify(c.arguments)})
            {!c.ok && c.error && <span className="block text-[var(--critical)]">{c.error.code}: {c.error.detail}</span>}
          </li>
        ))}
      </ul>
    </details>
  )
}

function Message({ m, onBook, busy }) {
  if (m.role === 'user') {
    return (
      <div className="flex justify-end">
        <p className="max-w-[80%] rounded-2xl rounded-br-sm bg-[var(--accent)] text-white px-4 py-2 whitespace-pre-wrap">
          {m.text}
        </p>
      </div>
    )
  }
  if (m.role === 'error') {
    return <p className="text-sm text-[var(--critical)]">{m.text}</p>
  }
  const r = m.data
  return (
    <div className="flex">
      <div className="max-w-[85%] rounded-2xl rounded-bl-sm card px-4 py-3">
        <p className="whitespace-pre-wrap">{r.reply}</p>
        {r.suggestedSeats?.length > 0 && !r.reservation && (
          <div className="flex flex-wrap gap-2 mt-3">
            {r.suggestedSeats.map((s) => (
              <button key={s.seatCode} className="btn btn-sm" disabled={busy} onClick={() => onBook(s.seatCode)}>
                Đặt {s.seatCode}
                <span className="muted font-normal">
                  {s.hasPowerOutlet ? ' ⚡' : ''}
                  {s.quietZone ? ' 🤫' : ''} · khu {s.zoneOccupancyPercent}%
                </span>
              </button>
            ))}
          </div>
        )}
        {r.reservation && (
          <div className="mt-3 rounded-lg bg-[var(--surface-2)] p-3 text-sm">
            ✅ Đã giữ ghế <strong>{r.reservation.seatCode}</strong>
            {r.reservation.expireAt && <> — check-in trước {formatTime(r.reservation.expireAt)}</>}.{' '}
            <Link to="/me" className="text-[var(--accent)] font-semibold">
              Xem chỗ của tôi
            </Link>
          </div>
        )}
        <ToolCalls calls={r.toolCalls} />
        <p className="text-[10px] muted mt-1">engine: {r.engine}</p>
      </div>
    </div>
  )
}

export default function AssistantPage() {
  const { refreshUser } = useAuth()
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const [sessionId, setSessionId] = useState(() => sessionStorage.getItem(SESSION_KEY))
  const endRef = useRef(null)

  useEffect(() => {
    endRef.current?.scrollIntoView?.({ behavior: 'smooth', block: 'end' })
  }, [messages])

  const send = async (text) => {
    const message = text.trim()
    if (!message || busy) return
    setInput('')
    setMessages((list) => [...list, { role: 'user', text: message }])
    setBusy(true)
    try {
      const res = await api.chat(message, sessionId || undefined)
      if (res.sessionId && res.sessionId !== sessionId) {
        setSessionId(res.sessionId)
        sessionStorage.setItem(SESSION_KEY, res.sessionId)
      }
      setMessages((list) => [...list, { role: 'assistant', data: res }])
      if (res.reservation) refreshUser().catch(() => {})
    } catch (e) {
      setMessages((list) => [...list, { role: 'error', text: errorMessage(e) }])
    } finally {
      setBusy(false)
    }
  }

  const reset = () => {
    sessionStorage.removeItem(SESSION_KEY)
    setSessionId(null)
    setMessages([])
  }

  return (
    <div className="max-w-3xl mx-auto flex flex-col gap-4" style={{ minHeight: 'calc(100vh - 8rem)' }}>
      <div className="flex items-end justify-between gap-2">
        <div>
          <h1 className="text-2xl font-bold">Trợ lý AI</h1>
          <p className="secondary text-sm">Hỏi bằng tiếng Việt hoặc tiếng Anh. Trợ lý có thể tìm ghế, xem dự báo và đặt chỗ giúp bạn.</p>
        </div>
        {messages.length > 0 && (
          <button className="btn btn-sm" onClick={reset}>
            Cuộc trò chuyện mới
          </button>
        )}
      </div>

      <div className="flex-1 space-y-3" aria-live="polite">
        {messages.length === 0 && (
          <div className="card p-4 space-y-2">
            <p className="label">Thử hỏi</p>
            <div className="flex flex-wrap gap-2">
              {EXAMPLES.map((ex) => (
                <button key={ex} className="btn btn-sm" onClick={() => send(ex)}>
                  {ex}
                </button>
              ))}
            </div>
          </div>
        )}
        {messages.map((m, i) => (
          <Message key={i} m={m} busy={busy} onBook={(code) => send(`Đặt ghế ${code}`)} />
        ))}
        {busy && <p className="text-sm muted">Trợ lý đang trả lời…</p>}
        <div ref={endRef} />
      </div>

      <form
        className="sticky bottom-4 card p-2 flex gap-2"
        onSubmit={(e) => {
          e.preventDefault()
          send(input)
        }}
      >
        <input
          className="input flex-1 border-none"
          placeholder="Nhập câu hỏi…"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          aria-label="Câu hỏi cho trợ lý"
          maxLength={1000}
        />
        <button className="btn btn-primary" disabled={busy || !input.trim()}>
          Gửi
        </button>
      </form>
    </div>
  )
}
