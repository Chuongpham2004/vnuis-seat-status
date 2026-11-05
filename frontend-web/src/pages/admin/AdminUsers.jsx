import { Fragment, useEffect, useState } from 'react'
import { useToast } from '../../components/Toasts'
import { api } from '../../lib/api'
import { errorMessage } from '../../lib/errors'
import { formatDateTime } from '../../lib/format'

function CreditForm({ user, onDone, onCancel }) {
  const { push } = useToast()
  const [delta, setDelta] = useState(10)
  const [reason, setReason] = useState('')
  const [clearBan, setClearBan] = useState(Boolean(user.bannedUntil))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const updated = await api.adjustCredit(user.id, Number(delta), reason.trim(), clearBan)
      push({ title: `Đã cập nhật ${user.studentCode}`, message: `Điểm uy tín: ${updated.creditScore}`, tone: 'good' })
      onDone(updated)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="flex flex-wrap items-end gap-2 p-3 bg-[var(--surface-2)] rounded-lg" onSubmit={submit}>
      <label className="space-y-1">
        <span className="label block">Điều chỉnh (±)</span>
        <input type="number" min={-100} max={100} className="input w-24" value={delta} onChange={(e) => setDelta(e.target.value)} required />
      </label>
      <label className="space-y-1 flex-1 min-w-[12rem]">
        <span className="label block">Lý do</span>
        <input className="input" maxLength={200} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="VD: khiếu nại được chấp nhận" required />
      </label>
      <label className="flex items-center gap-1.5 text-sm pb-2">
        <input type="checkbox" checked={clearBan} onChange={(e) => setClearBan(e.target.checked)} />
        Gỡ khoá
      </label>
      <button className="btn btn-primary btn-sm" disabled={busy}>
        Lưu
      </button>
      <button type="button" className="btn btn-sm" onClick={onCancel}>
        Huỷ
      </button>
      {error && <p className="w-full text-sm text-[var(--critical)]">{error}</p>}
    </form>
  )
}

export default function AdminUsers() {
  const [q, setQ] = useState('')
  const [users, setUsers] = useState([])
  const [error, setError] = useState(null)
  const [editing, setEditing] = useState(null)

  useEffect(() => {
    const t = setTimeout(() => {
      api
        .users(q, 100)
        .then((list) => {
          setUsers(list)
          setError(null)
        })
        .catch((e) => setError(errorMessage(e)))
    }, 250)
    return () => clearTimeout(t)
  }, [q])

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">Sinh viên & điểm uy tín</h1>
      <input
        className="input max-w-md"
        placeholder="Tìm theo mã SV, tên hoặc email"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        aria-label="Tìm sinh viên"
      />
      {error && <p className="text-sm text-[var(--critical)]">{error}</p>}
      <section className="card overflow-x-auto">
        <table className="data">
          <thead>
            <tr>
              <th>Mã</th>
              <th>Họ tên</th>
              <th>Email</th>
              <th>Vai trò</th>
              <th>Uy tín</th>
              <th>Đặt chỗ</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <Fragment key={u.id}>
                <tr>
                  <td className="font-semibold">{u.studentCode}</td>
                  <td>{u.fullName}</td>
                  <td className="secondary">{u.email}</td>
                  <td>{u.role === 'ADMIN' ? 'Quản trị' : 'Sinh viên'}</td>
                  <td className="font-semibold">{u.creditScore}</td>
                  <td>
                    {u.canBook ? (
                      <span className="chip tone-good">
                        <span className="dot" />
                        Được phép
                      </span>
                    ) : (
                      <span className="chip tone-critical" title={u.bannedUntil ? `Khoá đến ${formatDateTime(u.bannedUntil)}` : undefined}>
                        <span className="dot" />
                        {u.bannedUntil ? `Khoá đến ${formatDateTime(u.bannedUntil)}` : 'Không đủ điểm'}
                      </span>
                    )}
                  </td>
                  <td>
                    {u.role !== 'ADMIN' && (
                      <button className="btn btn-sm" onClick={() => setEditing(editing === u.id ? null : u.id)}>
                        Điều chỉnh
                      </button>
                    )}
                  </td>
                </tr>
                {editing === u.id && (
                  <tr>
                    <td colSpan={7}>
                      <CreditForm
                        user={u}
                        onCancel={() => setEditing(null)}
                        onDone={(updated) => {
                          setUsers((list) => list.map((x) => (x.id === updated.id ? updated : x)))
                          setEditing(null)
                        }}
                      />
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  )
}
