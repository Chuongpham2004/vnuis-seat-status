import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { errorMessage } from '../lib/errors'

const DEMO_ACCOUNTS = [
  ['SV001', 'Sinh viên (100 điểm)'],
  ['SV005', 'Sinh viên (65 điểm, demo khoá)'],
  ['ADMIN001', 'Thủ thư / quản trị'],
]

export default function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  if (user) return <Navigate to={user.role === 'ADMIN' ? '/admin' : '/'} replace />

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const me = await login(username.trim(), password)
      const from = location.state?.from
      navigate(from || (me.role === 'ADMIN' ? '/admin' : '/'), { replace: true })
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="max-w-sm mx-auto mt-8 space-y-4">
      <form className="card p-6 space-y-4" onSubmit={submit}>
        <div>
          <h1 className="text-xl font-bold">Đăng nhập</h1>
          <p className="text-sm secondary">Dùng mã sinh viên hoặc email trường.</p>
        </div>
        <div className="space-y-1">
          <label className="label" htmlFor="username">
            Mã sinh viên / email
          </label>
          <input
            id="username"
            className="input"
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
            autoFocus
          />
        </div>
        <div className="space-y-1">
          <label className="label" htmlFor="password">
            Mật khẩu
          </label>
          <input
            id="password"
            type="password"
            className="input"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </div>
        {error && (
          <p className="text-sm text-[var(--critical)]" role="alert">
            {error}
          </p>
        )}
        <button className="btn btn-primary w-full" disabled={busy}>
          {busy ? 'Đang đăng nhập…' : 'Đăng nhập'}
        </button>
      </form>

      {(import.meta.env.DEV || import.meta.env.VITE_SHOW_DEMO_ACCOUNTS === 'true') && (
        <div className="card p-4 text-sm space-y-2">
          <p className="label">Tài khoản demo (mật khẩu Password@123)</p>
          <div className="flex flex-wrap gap-2">
            {DEMO_ACCOUNTS.map(([code, label]) => (
              <button
                key={code}
                type="button"
                className="btn btn-sm"
                title={label}
                onClick={() => {
                  setUsername(code)
                  setPassword('Password@123')
                }}
              >
                {code}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
