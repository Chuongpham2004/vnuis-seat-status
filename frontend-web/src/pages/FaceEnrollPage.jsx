import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import FaceCheck from '../components/FaceCheck'
import { useToast } from '../components/Toasts'
import { api } from '../lib/api'
import { errorMessage } from '../lib/errors'
import { formatDateTime } from '../lib/format'

/** Enrol (first booking) or manage the student's face template. */
export default function FaceEnrollPage() {
  const [params] = useSearchParams()
  const seat = params.get('seat')
  const navigate = useNavigate()
  const { refreshUser } = useAuth()
  const { push } = useToast()
  const [status, setStatus] = useState(null)
  const [consent, setConsent] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    api
      .faceStatus()
      .then(setStatus)
      .catch((e) => setError(errorMessage(e)))
  }, [])

  const enrolled = async (s) => {
    setStatus(s)
    refreshUser().catch(() => {})
    push({
      title: 'Đã đăng ký khuôn mặt',
      message: seat ? `Giờ bạn có thể đặt ghế ${seat}.` : 'Lần sau đặt ghế chỉ cần xác thực nhanh.',
      tone: 'good',
    })
    navigate(seat ? `/?seat=${encodeURIComponent(seat)}` : '/')
  }

  const withdraw = async () => {
    if (!window.confirm('Xoá dữ liệu khuôn mặt? Bạn sẽ phải đăng ký lại trước lần đặt ghế tiếp theo.')) return
    try {
      await api.faceDelete()
      setStatus({ ...status, enrolled: false, enrolledAt: null })
      refreshUser().catch(() => {})
      push({ title: 'Đã xoá dữ liệu khuôn mặt', tone: 'good' })
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  return (
    <div className="max-w-xl mx-auto space-y-4">
      <div>
        <h1 className="text-2xl font-bold">Khuôn mặt của tôi</h1>
        <p className="secondary text-sm">
          Mỗi lần đặt ghế, hệ thống xác nhận đúng chủ tài khoản bằng khuôn mặt, để không ai giữ chỗ hộ người khác.
        </p>
      </div>
      {error && (
        <p className="text-sm text-[var(--critical)]" role="alert">
          {error}
        </p>
      )}
      {!status && !error && <p className="muted">Đang tải…</p>}

      {status?.enrolled && (
        <section className="card p-5 space-y-3">
          <p className="chip tone-good w-fit">
            <span className="dot" />
            Đã đăng ký {status.enrolledAt && `lúc ${formatDateTime(status.enrolledAt)}`}
          </p>
          <p className="text-sm secondary">
            Cần đăng ký lại (đổi kiểu tóc, kính…)? Hãy nhờ thủ thư đặt lại, hoặc xoá dữ liệu rồi đăng ký lại.
          </p>
          <div className="flex gap-2">
            <Link to="/" className="btn btn-primary">
              Về sơ đồ ghế
            </Link>
            <button className="btn btn-danger" onClick={withdraw}>
              Xoá dữ liệu khuôn mặt
            </button>
          </div>
        </section>
      )}

      {status && !status.enrolled && (
        <section className="card p-5 space-y-4">
          {seat && (
            <p className="text-sm">
              Đăng ký một lần để đặt ghế <strong>{seat}</strong>. Mất khoảng 10 giây.
            </p>
          )}
          <ul className="text-sm secondary list-disc pl-5 space-y-1">
            <li>Camera chụp vài khung hình khi bạn nhìn thẳng và quay đầu trái/phải.</li>
            <li>
              Ảnh <strong>không được lưu</strong>; hệ thống chỉ giữ một vector đặc trưng 512 chiều (ArcFace) để so khớp.
            </li>
            <li>Bạn có thể xoá dữ liệu này bất cứ lúc nào tại trang này.</li>
          </ul>
          <label className="flex items-start gap-2 text-sm">
            <input type="checkbox" className="mt-1" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
            <span>
              Tôi đồng ý cho thư viện xử lý dữ liệu khuôn mặt (dữ liệu sinh trắc học) của tôi chỉ để xác thực khi đặt
              chỗ, theo Nghị định 13/2023/NĐ-CP.
            </span>
          </label>
          <FaceCheck mode="enroll" consent={consent} onSuccess={enrolled} />
        </section>
      )}
    </div>
  )
}
