import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, onUnauthorized, tokenStore } from '../lib/api'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => tokenStore.get())
  const [user, setUser] = useState(null)
  // While a stored token is being verified we do not know who the user is yet.
  const [loading, setLoading] = useState(() => Boolean(tokenStore.get()))

  const logout = useCallback(() => {
    tokenStore.clear()
    setToken(null)
    setUser(null)
    setLoading(false)
  }, [])

  useEffect(() => {
    onUnauthorized(logout)
    return () => onUnauthorized(null)
  }, [logout])

  useEffect(() => {
    if (!token) return undefined
    let cancelled = false
    api
      .me()
      .then((me) => !cancelled && setUser(me))
      .catch(() => !cancelled && logout())
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [token, logout])

  const login = useCallback(async (username, password) => {
    const res = await api.login(username, password)
    tokenStore.set(res.accessToken)
    setUser(res.user)
    setToken(res.accessToken)
    return res.user
  }, [])

  const refreshUser = useCallback(async () => {
    if (!tokenStore.get()) return null
    const me = await api.me()
    setUser(me)
    return me
  }, [])

  const value = useMemo(
    () => ({
      token,
      user,
      loading,
      isAdmin: user?.role === 'ADMIN',
      login,
      logout,
      refreshUser,
    }),
    [token, user, loading, login, logout, refreshUser],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
