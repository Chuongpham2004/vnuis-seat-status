import { useEffect, useState } from 'react'
import Legend from '../components/charts/Legend'
import LineChart from '../components/charts/LineChart'
import { api } from '../lib/api'
import { errorMessage } from '../lib/errors'
import { formatDay, formatTime, hourOf } from '../lib/format'
import { zoneColor } from '../lib/seatStatus'

const LEVEL = {
  LOW: ['Vắng', 'tone-good'],
  MEDIUM: ['Vừa phải', 'tone-warning'],
  HIGH: ['Đông', 'tone-critical'],
}

const ZONE_NAMES = { A: 'Khu A (yên tĩnh)', B: 'Khu B (chung)', C: 'Khu C (nhóm)' }

function toSeries(forecast) {
  return {
    key: forecast.zoneCode,
    label: `Khu ${forecast.zoneCode}`,
    color: zoneColor(forecast.zoneCode),
    points: forecast.points.map((p) => ({ t: new Date(p.time), v: p.predictedRate, lo: p.lower, hi: p.upper })),
  }
}

/** Best upcoming hour (lowest predicted occupancy) inside the library's opening hours. */
function quietestHour(points) {
  const open = points.filter((p) => p.hour >= 7 && p.hour < 22)
  const list = open.length ? open : points
  return list.reduce((best, p) => (best == null || p.predictedRate < best.predictedRate ? p : best), null)
}

export default function ForecastPage() {
  const [zone, setZone] = useState('')
  const [hours, setHours] = useState(24)
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [loadedKey, setLoadedKey] = useState(null)
  const requestKey = `${zone}|${hours}`
  const loading = loadedKey !== requestKey
  const [showTable, setShowTable] = useState(false)

  useEffect(() => {
    let cancelled = false
    api
      .forecast(zone || undefined, hours)
      .then((res) => {
        if (cancelled) return
        setData(res.zones ? res.zones : [res])
        setError(null)
      })
      .catch((e) => !cancelled && setError(e))
      .finally(() => !cancelled && setLoadedKey(`${zone}|${hours}`))
    return () => {
      cancelled = true
    }
  }, [zone, hours])

  const series = (data || []).map(toSeries)
  const formatX = (t, long) => (long ? `${formatDay(t.toISOString())} ${formatTime(t.toISOString())}` : `${hourOf(t.toISOString())}h`)

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-bold">Dự báo mức độ đông đúc</h1>
        <p className="secondary text-sm">
          Mô hình LightGBM dự báo tỷ lệ ghế có người theo giờ, dựa trên lịch sử, giờ trong ngày, cuối tuần và mùa thi.
        </p>
      </div>

      <div className="flex flex-wrap items-center gap-2" role="group" aria-label="Bộ lọc dự báo">
        <select className="input w-auto" value={hours} onChange={(e) => setHours(Number(e.target.value))}>
          <option value={12}>12 giờ tới</option>
          <option value={24}>24 giờ tới</option>
          <option value={48}>48 giờ tới</option>
          <option value={168}>7 ngày tới</option>
        </select>
        <select className="input w-auto" value={zone} onChange={(e) => setZone(e.target.value)}>
          <option value="">So sánh cả 3 khu</option>
          {Object.entries(ZONE_NAMES).map(([code, name]) => (
            <option key={code} value={code}>
              {name}
            </option>
          ))}
        </select>
        <button className="btn btn-sm" onClick={() => setShowTable((v) => !v)}>
          {showTable ? 'Ẩn bảng số liệu' : 'Xem bảng số liệu'}
        </button>
      </div>

      {error ? (
        <div className="card p-5">
          <p className="font-semibold">Không tải được dự báo</p>
          <p className="text-sm secondary">{errorMessage(error)}</p>
        </div>
      ) : (
        <>
          <div className="grid sm:grid-cols-3 gap-3">
            {(data || []).map((f) => {
              const best = quietestHour(f.points)
              const now = f.points[0]
              const [lvl, tone] = LEVEL[now?.level] || ['—', 'tone-neutral']
              return (
                <div key={f.zoneCode} className="card p-4" style={{ opacity: loading ? 0.6 : 1 }}>
                  <p className="flex items-center gap-2 label">
                    <span className="line-key" style={{ background: zoneColor(f.zoneCode), width: 16 }} />
                    {ZONE_NAMES[f.zoneCode] || `Khu ${f.zoneCode}`}
                  </p>
                  <p className="text-3xl font-bold mt-1">{now ? `${now.predictedPercent}%` : '—'}</p>
                  <p className="text-sm secondary flex items-center gap-2">
                    giờ tới
                    <span className={'chip ' + tone}>
                      <span className="dot" />
                      {lvl}
                    </span>
                  </p>
                  {best && (
                    <p className="text-xs secondary mt-2">
                      Vắng nhất: <strong>{formatX(new Date(best.time), true)}</strong> (~{best.predictedPercent}%)
                    </p>
                  )}
                </div>
              )
            })}
          </div>

          <section className="card p-4 space-y-3" style={{ opacity: loading ? 0.6 : 1 }}>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h2 className="font-semibold">Tỷ lệ chỗ có người dự kiến</h2>
              {series.length > 1 && <Legend items={series} />}
            </div>
            {series.length === 1 && (
              <p className="text-xs muted">Dải mờ: khoảng sai số ± RMSE của mô hình ({data[0].rmse}).</p>
            )}
            <LineChart
              series={series}
              showBand={series.length === 1}
              formatX={formatX}
              ariaLabel="Biểu đồ dự báo tỷ lệ chỗ có người theo giờ"
            />
            {data?.[0] && (
              <p className="text-xs muted">
                Mô hình: {data[0].model} · cache {data[0].cache?.hit ? 'hit' : 'miss'}
              </p>
            )}
          </section>

          {showTable && data && (
            <section className="card overflow-x-auto">
              <table className="data">
                <thead>
                  <tr>
                    <th>Thời điểm</th>
                    {data.map((f) => (
                      <th key={f.zoneCode}>Khu {f.zoneCode}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {data[0].points.map((p, i) => (
                    <tr key={p.time}>
                      <td>{formatX(new Date(p.time), true)}</td>
                      {data.map((f) => (
                        <td key={f.zoneCode}>
                          {f.points[i]?.predictedPercent}% ({Math.round((f.points[i]?.lower ?? 0) * 100)}–
                          {Math.round((f.points[i]?.upper ?? 0) * 100)}%)
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>
          )}
        </>
      )}
    </div>
  )
}
