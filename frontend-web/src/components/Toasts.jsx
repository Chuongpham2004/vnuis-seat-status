import { createContext, useCallback, useContext, useMemo, useState } from 'react'

const ToastContext = createContext(null)
let seq = 0

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])

  const dismiss = useCallback((id) => setToasts((list) => list.filter((t) => t.id !== id)), [])

  const push = useCallback(
    (toast) => {
      const id = ++seq
      const entry = { tone: 'info', timeout: 6000, ...toast, id }
      setToasts((list) => [...list.slice(-4), entry])
      if (entry.timeout) setTimeout(() => dismiss(id), entry.timeout)
      return id
    },
    [dismiss],
  )

  const value = useMemo(() => ({ push, dismiss }), [push, dismiss])

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toast-stack" role="status" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className={`toast toast-${t.tone}`}>
            <div className="min-w-0">
              {t.title && <p className="font-semibold">{t.title}</p>}
              {t.message && <p className="text-sm text-[var(--ink-secondary)]">{t.message}</p>}
            </div>
            <button className="toast-close" onClick={() => dismiss(t.id)} aria-label="Đóng">
              ×
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useToast() {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be used inside <ToastProvider>')
  return ctx
}
