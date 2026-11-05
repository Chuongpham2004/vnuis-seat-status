import { Link } from 'react-router-dom'

export default function NotFoundPage() {
  return (
    <div className="card p-6 max-w-md">
      <p className="font-semibold">Không tìm thấy trang.</p>
      <Link to="/" className="text-[var(--accent)] font-semibold text-sm">
        Về bản đồ ghế
      </Link>
    </div>
  )
}
