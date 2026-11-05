import { QRCodeSVG } from 'qrcode.react'
import { useEffect, useState } from 'react'
import { api } from '../../lib/api'
import { errorMessage } from '../../lib/errors'

/** Printable QR stickers for the tables (one per seat). The QR encodes the seat's check-in token. */
export default function AdminQr() {
  const [zone, setZone] = useState('A')
  const [codes, setCodes] = useState([])
  const [error, setError] = useState(null)

  useEffect(() => {
    api
      .seatQrCodes(zone || undefined)
      .then((list) => {
        setCodes(list)
        setError(null)
      })
      .catch((e) => setError(errorMessage(e)))
  }, [zone])

  return (
    <div className="space-y-4">
      <div className="no-print flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold">Mã QR check-in</h1>
          <p className="secondary text-sm">In và dán lên từng bàn. Sinh viên quét mã này khi check-in.</p>
        </div>
        <div className="flex gap-2">
          <select className="input w-auto" value={zone} onChange={(e) => setZone(e.target.value)}>
            <option value="A">Khu A</option>
            <option value="B">Khu B</option>
            <option value="C">Khu C</option>
            <option value="">Tất cả</option>
          </select>
          <button className="btn btn-primary" onClick={() => window.print()}>
            In
          </button>
        </div>
      </div>
      {error && <p className="text-sm text-[var(--critical)]">{error}</p>}
      <div className="print-grid grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
        {codes.map((c) => (
          <figure key={c.seatCode} className="card p-3 flex flex-col items-center gap-2 bg-white">
            <QRCodeSVG value={c.qrCodeToken} size={128} level="M" marginSize={1} />
            <figcaption className="text-center">
              <span className="block text-lg font-bold">{c.seatCode}</span>
              <span className="block font-mono text-[10px] muted break-all">{c.qrCodeToken}</span>
            </figcaption>
          </figure>
        ))}
      </div>
    </div>
  )
}
