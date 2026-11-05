import { Client } from '@stomp/stompjs'
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { useAuth } from '../auth/AuthContext'

// One STOMP connection for the whole app. It reconnects when the access token changes
// (the backend reads the JWT from the CONNECT frame) and re-subscribes every
// registered destination after each (re)connect.

const RealtimeContext = createContext(null)

function defaultBrokerUrl() {
  if (import.meta.env.VITE_WS_URL) return import.meta.env.VITE_WS_URL
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${scheme}://${window.location.host}/ws`
}

export function RealtimeProvider({ children, brokerURL = defaultBrokerUrl() }) {
  const { token, loading } = useAuth()
  const [status, setStatus] = useState('connecting') // connecting | connected | disconnected
  const clientRef = useRef(null)
  // id -> { destination, handler, stompSub }
  const registry = useRef(new Map())
  const nextId = useRef(1)

  useEffect(() => {
    if (loading) return undefined
    const entries = registry.current
    const client = new Client({
      brokerURL,
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => {},
    })
    const isCurrent = () => clientRef.current === client
    client.onConnect = () => {
      if (!isCurrent()) return
      setStatus('connected')
      for (const entry of registry.current.values()) {
        entry.stompSub = client.subscribe(entry.destination, (msg) => dispatch(entry, msg))
      }
    }
    client.onWebSocketClose = () => {
      if (!isCurrent()) return
      setStatus('disconnected')
      for (const entry of registry.current.values()) entry.stompSub = null
    }
    client.onStompError = () => isCurrent() && setStatus('disconnected')
    clientRef.current = client
    client.activate()
    return () => {
      clientRef.current = null
      for (const entry of entries.values()) entry.stompSub = null
      client.deactivate()
      setStatus('connecting')
    }
  }, [brokerURL, token, loading])

  const subscribe = useCallback((destination, handler) => {
    const id = nextId.current++
    const entry = { destination, handler, stompSub: null }
    registry.current.set(id, entry)
    const client = clientRef.current
    if (client?.connected) {
      entry.stompSub = client.subscribe(destination, (msg) => dispatch(entry, msg))
    }
    return () => {
      registry.current.delete(id)
      try {
        entry.stompSub?.unsubscribe()
      } catch {
        // connection already gone
      }
    }
  }, [])

  const value = useMemo(() => ({ status, subscribe }), [status, subscribe])
  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>
}

function dispatch(entry, msg) {
  let body = msg.body
  try {
    body = JSON.parse(msg.body)
  } catch {
    // non-JSON payload: pass through
  }
  entry.handler(body)
}

// eslint-disable-next-line react-refresh/only-export-components
export function useRealtimeStatus() {
  return useContext(RealtimeContext)?.status ?? 'disconnected'
}

/**
 * Subscribe to a STOMP destination while the component is mounted.
 * The latest `handler` is always used, so callers need not memoise it.
 */
// eslint-disable-next-line react-refresh/only-export-components
export function useSubscription(destination, handler, enabled = true) {
  const subscribe = useContext(RealtimeContext)?.subscribe
  const handlerRef = useRef(handler)
  useEffect(() => {
    handlerRef.current = handler
  })
  useEffect(() => {
    if (!subscribe || !destination || !enabled) return undefined
    return subscribe(destination, (body) => handlerRef.current(body))
  }, [subscribe, destination, enabled])
}
