import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useRealtimeStatus } from '../realtime/RealtimeContext'
import BrandMark from './BrandMark'

const STUDENT_LINKS = [
  { to: '/', label: 'Sơ đồ', end: true },
  { to: '/me', label: 'Chỗ của tôi', student: true },
  { to: '/forecast', label: 'Dự báo' },
  { to: '/assistant', label: 'Trợ lý', student: true },
]

const ADMIN_LINKS = [
  { to: '/admin', label: 'Tổng quan', end: true },
  { to: '/admin/devices', label: 'Thiết bị' },
  { to: '/admin/users', label: 'Sinh viên' },
  { to: '/admin/qr', label: 'Mã QR' },
  { to: '/admin/ai', label: 'Mô hình' },
]

const CONNECTION = {
  connected: ['Trực tiếp', 'var(--good)'],
  connecting: ['Đang kết nối', 'var(--warning)'],
  disconnected: ['Mất kết nối', 'var(--critical)'],
}

function ConnectionStatus({ compact = false }) {
  const status = useRealtimeStatus()
  const [label, color] = CONNECTION[status] || CONNECTION.disconnected
  return (
    <span className="flex items-center gap-1.5 text-xs font-medium" title="Kết nối thời gian thực tới máy chủ">
      <span className="dot" style={{ background: color }} />
      <span className={compact ? 'sr-only' : ''}>{label}</span>
    </span>
  )
}

function useNavLinks() {
  const { user, isAdmin } = useAuth()
  const student = STUDENT_LINKS.filter((l) => !l.student || (user && !isAdmin))
  return { student, admin: isAdmin ? ADMIN_LINKS : [] }
}

function DesktopLink({ to, label, end }) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        'relative flex items-center h-16 text-sm whitespace-nowrap transition-colors ' +
        (isActive
          ? 'font-semibold text-[var(--ink-primary)] after:absolute after:inset-x-0 after:bottom-0 after:h-[3px] after:rounded-t after:bg-[var(--ink-primary)]'
          : 'font-medium text-[var(--ink-secondary)] hover:text-[var(--ink-primary)]')
      }
    >
      {label}
    </NavLink>
  )
}

function MobileLink({ to, label, end, onNavigate }) {
  return (
    <NavLink
      to={to}
      end={end}
      onClick={onNavigate}
      className={({ isActive }) =>
        'block rounded-[var(--radius-sm)] px-3 py-2.5 text-[0.95rem] ' +
        (isActive ? 'bg-[var(--sign)] text-[var(--sign-ink)] font-semibold' : 'hover:bg-[var(--surface-2)]')
      }
    >
      {label}
    </NavLink>
  )
}

function UserBlock({ onLogout, align = 'right' }) {
  const { user, isAdmin } = useAuth()
  if (!user) {
    return (
      <Link to="/login" className="btn btn-primary btn-sm">
        Đăng nhập
      </Link>
    )
  }
  return (
    <div className="flex items-center gap-3">
      <div className={(align === 'right' ? 'text-right' : 'text-left') + ' leading-tight'}>
        <p className="text-sm font-semibold">{user.fullName}</p>
        <p className="text-xs secondary">{isAdmin ? 'Thủ thư' : `${user.studentCode}, uy tín ${user.creditScore}`}</p>
      </div>
      <button className="btn btn-sm" onClick={onLogout}>
        Đăng xuất
      </button>
    </div>
  )
}

function Navbar() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const { student, admin } = useNavLinks()
  const [open, setOpen] = useState(false)
  const [openedAt, setOpenedAt] = useState(location.pathname)

  // Close the mobile menu after navigating (also covers the browser back button).
  if (open && openedAt !== location.pathname) setOpen(false)

  useEffect(() => {
    if (!open) return undefined
    const onKey = (e) => e.key === 'Escape' && setOpen(false)
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])

  const handleLogout = () => {
    setOpen(false)
    logout()
    navigate('/')
  }

  return (
    <header className="no-print sticky top-0 z-30 bg-[var(--paper)]/95 backdrop-blur border-b border-[var(--border)]">
      <div className="max-w-[1400px] mx-auto px-4 sm:px-6 h-16 flex items-center gap-8">
        <Link to="/" className="flex items-center gap-2.5 shrink-0" aria-label="Thư viện VNU-IS, về trang sơ đồ">
          <BrandMark size={34} />
          <span className="leading-none">
            <span className="block text-[1.02rem] font-extrabold tracking-tight">Thư viện VNU-IS</span>
            <span className="block text-xs secondary mt-1">Không gian tự học</span>
          </span>
        </Link>

        <nav className="hidden lg:flex items-center gap-6 h-full" aria-label="Điều hướng chính">
          {student.map((l) => (
            <DesktopLink key={l.to} {...l} />
          ))}
          {admin.length > 0 && (
            <>
              <span className="w-px h-5 bg-[var(--axis)]" aria-hidden />
              <span className="text-xs font-semibold muted -mr-2">Thủ thư</span>
              {admin.map((l) => (
                <DesktopLink key={l.to} {...l} />
              ))}
            </>
          )}
        </nav>

        <div className="ml-auto flex items-center gap-5">
          <span className="hidden sm:block secondary">
            <ConnectionStatus />
          </span>
          <span className="hidden lg:block">
            <UserBlock onLogout={handleLogout} />
          </span>
          <span className="sm:hidden">
            <ConnectionStatus compact />
          </span>
          <button
            type="button"
            className="lg:hidden btn btn-sm"
            aria-expanded={open}
            aria-controls="mobile-menu"
            onClick={() => {
              setOpenedAt(location.pathname)
              setOpen((v) => !v)
            }}
          >
            {open ? 'Đóng' : 'Menu'}
          </button>
        </div>
      </div>

      {open && (
        <div id="mobile-menu" className="lg:hidden border-t border-[var(--border)] bg-[var(--surface-1)]">
          <nav className="max-w-[1400px] mx-auto px-4 sm:px-6 py-3 grid gap-1" aria-label="Điều hướng chính">
            {student.map((l) => (
              <MobileLink key={l.to} {...l} onNavigate={() => setOpen(false)} />
            ))}
            {admin.length > 0 && (
              <>
                <p className="text-xs font-semibold muted px-3 pt-3 pb-1">Thủ thư</p>
                {admin.map((l) => (
                  <MobileLink key={l.to} {...l} onNavigate={() => setOpen(false)} />
                ))}
              </>
            )}
            <div className="mt-2 pt-3 border-t border-[var(--grid)] flex items-center justify-between gap-3 px-1">
              {user ? (
                <UserBlock onLogout={handleLogout} align="left" />
              ) : (
                <Link to="/login" className="btn btn-primary w-full" onClick={() => setOpen(false)}>
                  Đăng nhập
                </Link>
              )}
            </div>
          </nav>
        </div>
      )}
    </header>
  )
}

function Footer() {
  const { student } = useNavLinks()
  return (
    <footer className="no-print mt-12 bg-[var(--sign)] text-[var(--sign-ink)]">
      <div className="max-w-[1400px] mx-auto px-4 sm:px-6 py-10 grid gap-8 md:grid-cols-[1.4fr_1fr_1.4fr]">
        <div className="space-y-3 max-w-sm">
          <div className="flex items-center gap-2.5">
            <BrandMark size={30} inverted />
            <span className="font-extrabold tracking-tight">Thư viện VNU-IS</span>
          </div>
          <p className="text-sm opacity-75">
            Không gian tự học của Trường Quốc tế, Đại học Quốc gia Hà Nội. Cảm biến dưới mỗi ghế cho biết chỗ nào đang
            trống, theo thời gian thực.
          </p>
        </div>

        <nav aria-label="Liên kết cuối trang">
          <p className="text-sm font-semibold mb-3">Trang</p>
          <ul className="space-y-2 text-sm">
            {student.map((l) => (
              <li key={l.to}>
                <Link to={l.to} className="opacity-75 hover:opacity-100 hover:underline underline-offset-4">
                  {l.label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>

        <div>
          <p className="text-sm font-semibold mb-3">Cách giữ chỗ</p>
          <ol className="space-y-2 text-sm opacity-75 list-decimal pl-5">
            <li>Chọn một ghế trống trên sơ đồ hoặc nhờ trợ lý tìm giúp.</li>
            <li>Đến bàn, ngồi xuống và quét mã QR dán trên bàn để check-in.</li>
            <li>Khi về, bấm trả chỗ để người khác dùng và được cộng điểm uy tín.</li>
          </ol>
        </div>
      </div>

      <div className="border-t border-white/15">
        <div className="max-w-[1400px] mx-auto px-4 sm:px-6 py-4 flex flex-wrap items-center justify-between gap-3 text-xs opacity-75">
          <p>© 2026 Thư viện VNU-IS, Trường Quốc tế, ĐHQGHN.</p>
          <p className="flex items-center gap-4">
            <span>Đồ án tốt nghiệp: nền tảng không gian tự học dựa trên IoT và AI</span>
            <ConnectionStatus />
          </p>
        </div>
      </div>
    </footer>
  )
}

export default function Layout() {
  return (
    <div className="min-h-screen flex flex-col">
      <Navbar />
      <main className="flex-1 w-full max-w-[1400px] mx-auto px-4 sm:px-6 py-6">
        <Outlet />
      </main>
      <Footer />
    </div>
  )
}
