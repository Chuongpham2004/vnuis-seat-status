import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useRealtimeStatus } from '../realtime/RealtimeContext'

const STUDENT_LINKS = [
  { to: '/', label: 'Bản đồ ghế', end: true },
  { to: '/me', label: 'Chỗ của tôi', student: true },
  { to: '/forecast', label: 'Dự báo' },
  { to: '/assistant', label: 'Trợ lý AI', student: true },
]

const ADMIN_LINKS = [
  { to: '/admin', label: 'Tổng quan', end: true },
  { to: '/admin/devices', label: 'Thiết bị & demo' },
  { to: '/admin/users', label: 'Sinh viên' },
  { to: '/admin/qr', label: 'Mã QR' },
  { to: '/admin/ai', label: 'Mô hình AI' },
]

const CONNECTION = {
  connected: ['Realtime', 'tone-good'],
  connecting: ['Đang kết nối…', 'tone-warning'],
  disconnected: ['Mất kết nối', 'tone-critical'],
}

function ConnectionBadge() {
  const status = useRealtimeStatus()
  const [label, tone] = CONNECTION[status] || CONNECTION.disconnected
  return (
    <span className={`chip ${tone}`} title="Kết nối WebSocket tới backend-core">
      <span className="dot" />
      {label}
    </span>
  )
}

function NavItem({ to, label, end }) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        'px-3 py-1.5 rounded-md text-sm font-medium whitespace-nowrap ' +
        (isActive
          ? 'bg-[var(--surface-2)] text-[var(--ink-primary)]'
          : 'text-[var(--ink-secondary)] hover:text-[var(--ink-primary)]')
      }
    >
      {label}
    </NavLink>
  )
}

export default function Layout() {
  const { user, isAdmin, logout } = useAuth()
  const navigate = useNavigate()
  const links = STUDENT_LINKS.filter((l) => !l.student || (user && !isAdmin))

  return (
    <div className="min-h-screen flex flex-col">
      <header className="no-print border-b border-[var(--border)] bg-[var(--surface-1)] sticky top-0 z-20">
        <div className="max-w-7xl mx-auto px-4 h-14 flex items-center gap-4">
          <NavLink to="/" className="flex items-center gap-2 shrink-0">
            <span className="w-8 h-8 rounded-lg bg-[var(--accent)] text-white grid place-items-center font-bold text-sm">
              VL
            </span>
            <span className="hidden sm:block leading-tight">
              <span className="block font-bold text-sm">VNU-IS Library</span>
              <span className="block text-xs muted">Không gian tự học thông minh</span>
            </span>
          </NavLink>
          <nav className="flex items-center gap-1 overflow-x-auto">
            {links.map((l) => (
              <NavItem key={l.to} {...l} />
            ))}
            {isAdmin && <span className="w-px h-5 bg-[var(--grid)] mx-1" />}
            {isAdmin && ADMIN_LINKS.map((l) => <NavItem key={l.to} {...l} />)}
          </nav>
          <div className="ml-auto flex items-center gap-3 shrink-0">
            <ConnectionBadge />
            {user ? (
              <div className="flex items-center gap-2">
                <div className="hidden md:block text-right leading-tight">
                  <p className="text-sm font-semibold">{user.fullName}</p>
                  <p className="text-xs muted">
                    {user.studentCode}
                    {!isAdmin && ` · Uy tín ${user.creditScore}`}
                  </p>
                </div>
                <button
                  className="btn btn-sm"
                  onClick={() => {
                    logout()
                    navigate('/')
                  }}
                >
                  Đăng xuất
                </button>
              </div>
            ) : (
              <NavLink to="/login" className="btn btn-primary btn-sm">
                Đăng nhập
              </NavLink>
            )}
          </div>
        </div>
      </header>
      <main className="flex-1 w-full max-w-7xl mx-auto px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}
