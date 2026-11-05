import { useState } from 'react'
import { api } from '../lib/api'
import { errorMessage } from '../lib/errors'

const REASONS_VI = [
  [/^in preferred zone (\w+)/i, 'đúng khu $1 bạn chọn'],
  [/^has power outlet$/i, 'có ổ điện'],
  [/^quiet zone$/i, 'khu yên tĩnh'],
  [/^sensor detects something on the seat$/i, 'cảm biến đang thấy vật trên ghế'],
  [/^zone (\w+) is (\d+)% occupied$/i, 'khu $1 đang có người $2%'],
]

function translateReason(reason) {
  for (const [re, vi] of REASONS_VI) if (re.test(reason)) return reason.replace(re, vi)
  return reason
}

/** "Suggest a seat" using backend-core's ranking (/seats/recommendations). */
export default function Recommendations({ filters, zone, onPick }) {
  const [items, setItems] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  const load = async () => {
    setBusy(true)
    setError(null)
    try {
      const recs = await api.recommendations({
        zonePreference: zone || undefined,
        isQuietZone: filters.quiet || undefined,
        hasPowerOutlet: filters.power || undefined,
        seatType: filters.type || undefined,
        limit: 5,
      })
      setItems(recs)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card p-4 space-y-3">
      <div className="flex items-center justify-between gap-2">
        <h2 className="font-semibold">Gợi ý ghế phù hợp</h2>
        <button className="btn btn-sm" onClick={load} disabled={busy}>
          {busy ? 'Đang tìm…' : items ? 'Làm mới' : 'Gợi ý cho tôi'}
        </button>
      </div>
      <p className="text-xs muted">Dựa trên bộ lọc đang chọn, ưu tiên khu đang vắng.</p>
      {error && <p className="text-sm text-[var(--critical)]">{error}</p>}
      {items && items.length === 0 && <p className="text-sm secondary">Không còn ghế trống phù hợp.</p>}
      {items && items.length > 0 && (
        <ol className="space-y-2">
          {items.map((r, i) => (
            <li key={r.seat.seatCode}>
              <button
                className="w-full text-left rounded-lg border border-[var(--border)] p-2 hover:bg-[var(--surface-2)]"
                onClick={() => onPick(r.seat)}
              >
                <span className="font-semibold">
                  {i + 1}. {r.seat.seatCode}
                </span>
                <span className="text-xs secondary block">{r.reasons.map(translateReason).join(' · ')}</span>
              </button>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}
