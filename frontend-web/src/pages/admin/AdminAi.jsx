import { useCallback, useEffect, useState } from 'react'
import { useToast } from '../../components/Toasts'
import { api } from '../../lib/api'
import { errorMessage } from '../../lib/errors'
import { formatDateTime } from '../../lib/format'

const METRICS = [
  ['mae', 'MAE', 'Sai số tuyệt đối trung bình (tỷ lệ 0..1), càng thấp càng tốt', (v) => v?.toFixed(4)],
  ['rmse', 'RMSE', 'Căn bậc hai sai số bình phương, càng thấp càng tốt', (v) => v?.toFixed(4)],
  ['mape', 'MAPE', 'Sai số phần trăm (giờ mở cửa), càng thấp càng tốt', (v) => (v == null ? '—' : `${v.toFixed(2)}%`)],
  ['r2', 'R²', 'Hệ số xác định, càng gần 1 càng tốt', (v) => v?.toFixed(4)],
]

const FEATURE_VI = {
  hour_sin: 'Giờ trong ngày (sin)',
  hour_cos: 'Giờ trong ngày (cos)',
  dow_sin: 'Thứ trong tuần (sin)',
  dow_cos: 'Thứ trong tuần (cos)',
  is_weekend: 'Cuối tuần',
  is_exam_period: 'Mùa thi',
  zone_A: 'Khu A',
  zone_B: 'Khu B',
  zone_C: 'Khu C',
  lag_24: 'Cùng giờ hôm qua',
  lag_48: 'Cùng giờ 2 ngày trước',
  lag_168: 'Cùng giờ tuần trước',
  same_hour_mean_7d: 'TB cùng giờ 7 ngày',
  roll_mean_24_lag24: 'TB 24 giờ (trễ 24 giờ)',
  roll_max_24_lag24: 'Max 24 giờ (trễ 24 giờ)',
}

/** Horizontal bars (single series): value at the tip, 4px rounded data-end, <=24px thick. */
function ImportanceBars({ items }) {
  const max = Math.max(...items.map((i) => i.share), 0.0001)
  return (
    <ul className="space-y-1.5">
      {items.map((it) => (
        <li key={it.feature} className="grid grid-cols-[10rem_1fr] items-center gap-3 text-sm">
          <span className="secondary truncate" title={it.feature}>
            {FEATURE_VI[it.feature] || it.feature}
          </span>
          <span className="flex items-center gap-2">
            <span
              className="h-3.5 rounded-r-[4px]"
              style={{ width: `${(it.share / max) * 85}%`, background: 'var(--series-1)', minWidth: 2 }}
              title={`${(it.share * 100).toFixed(1)}%`}
            />
            <span className="text-xs tabular-nums secondary">{(it.share * 100).toFixed(1)}%</span>
          </span>
        </li>
      ))}
    </ul>
  )
}

export default function AdminAi() {
  const { push } = useToast()
  const [metrics, setMetrics] = useState(null)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(null)
  const [days, setDays] = useState(180)

  const load = useCallback(() => {
    api
      .aiMetrics()
      .then((m) => {
        setMetrics(m)
        setError(null)
      })
      .catch((e) => {
        setMetrics(null)
        setError(e)
      })
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const run = async (kind) => {
    setBusy(kind)
    try {
      if (kind === 'synthetic') {
        const r = await api.aiSynthetic(Number(days))
        push({ title: 'Đã sinh dữ liệu tổng hợp', message: `${r.rows} dòng mới, tổng ${r.totalRows}`, tone: 'good' })
      } else {
        await api.aiTrain()
        push({ title: 'Huấn luyện xong', tone: 'good' })
        load()
      }
    } catch (e) {
      push({ title: 'Thao tác thất bại', message: errorMessage(e), tone: 'critical' })
    } finally {
      setBusy(null)
    }
  }

  const models = metrics ? Object.entries(metrics.models) : []

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-bold">Mô hình dự báo</h1>
        <p className="secondary text-sm">So sánh LightGBM với đường cơ sở trung bình trượt trên tập kiểm thử (dữ liệu chưa thấy).</p>
      </div>

      <section className="card p-4 flex flex-wrap items-end gap-3">
        <label className="space-y-1">
          <span className="label block">Số ngày dữ liệu tổng hợp</span>
          <input type="number" min={14} max={730} className="input w-28" value={days} onChange={(e) => setDays(e.target.value)} />
        </label>
        <button className="btn" disabled={Boolean(busy)} onClick={() => run('synthetic')}>
          {busy === 'synthetic' ? 'Đang sinh…' : 'Sinh dữ liệu lịch sử'}
        </button>
        <button className="btn btn-primary" disabled={Boolean(busy)} onClick={() => run('train')}>
          {busy === 'train' ? 'Đang huấn luyện…' : 'Huấn luyện lại'}
        </button>
        <p className="text-xs muted basis-full">Huấn luyện có thể mất vài chục giây. Dữ liệu tổng hợp được ghi vào bảng hourly_occupancy_stats.</p>
      </section>

      {error && (
        <div className="card p-4">
          <p className="font-semibold">Chưa có số liệu mô hình</p>
          <p className="text-sm secondary">{errorMessage(error)}</p>
        </div>
      )}

      {metrics && (
        <>
          <div className="grid sm:grid-cols-3 gap-3">
            <div className="card p-4">
              <p className="label">Giảm MAE so với cơ sở</p>
              <p className="text-3xl font-semibold mt-1">{metrics.improvementPct?.mae ?? '—'}%</p>
            </div>
            <div className="card p-4">
              <p className="label">Giảm RMSE so với cơ sở</p>
              <p className="text-3xl font-semibold mt-1">{metrics.improvementPct?.rmse ?? '—'}%</p>
            </div>
            <div className="card p-4">
              <p className="label">Huấn luyện lúc</p>
              <p className="text-lg font-semibold mt-1">{formatDateTime(metrics.trainedAt)}</p>
              <p className="text-xs secondary">
                {metrics.trainRows?.toLocaleString('vi-VN')} dòng train · {metrics.testRows?.toLocaleString('vi-VN')} dòng test
              </p>
            </div>
          </div>

          <section className="card overflow-x-auto">
            <h2 className="font-semibold p-4 pb-2">Chỉ số đánh giá (toàn bộ)</h2>
            <table className="data">
              <thead>
                <tr>
                  <th>Chỉ số</th>
                  {models.map(([key, m]) => (
                    <th key={key}>
                      {m.label || key}
                      {metrics.bestModel === key && ' ★'}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {METRICS.map(([key, label, hint, fmt]) => (
                  <tr key={key}>
                    <td title={hint}>
                      <span className="font-semibold">{label}</span> <span className="text-xs muted">{hint}</span>
                    </td>
                    {models.map(([mk, m]) => (
                      <td key={mk}>{fmt(m[key])}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
            {metrics.testPeriod && (
              <p className="text-xs muted p-4 pt-2">
                Tập kiểm thử: {formatDateTime(metrics.testPeriod.from)} → {formatDateTime(metrics.testPeriod.to)} ·{' '}
                {metrics.horizon}
              </p>
            )}
          </section>

          {metrics.perZone && (
            <section className="card overflow-x-auto">
              <h2 className="font-semibold p-4 pb-2">MAE theo khu</h2>
              <table className="data">
                <thead>
                  <tr>
                    <th>Khu</th>
                    {models.map(([key, m]) => (
                      <th key={key}>{m.label || key}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {Object.entries(metrics.perZone).map(([zone, byModel]) => (
                    <tr key={zone}>
                      <td className="font-semibold">Khu {zone}</td>
                      {models.map(([key]) => (
                        <td key={key}>{byModel[key]?.mae?.toFixed(4) ?? '—'}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>
          )}

          {metrics.featureImportance?.length > 0 && (
            <section className="card p-4 space-y-3">
              <h2 className="font-semibold">Mức độ quan trọng của đặc trưng (LightGBM, gain)</h2>
              <ImportanceBars items={metrics.featureImportance.slice(0, 10)} />
            </section>
          )}
        </>
      )}
    </div>
  )
}
