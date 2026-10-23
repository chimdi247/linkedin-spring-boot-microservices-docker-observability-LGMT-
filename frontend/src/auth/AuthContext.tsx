import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { api, type RegisterInput } from '../api/endpoints'
import { loadSession, saveSession, setUnauthorizedHandler, type Session } from '../api/client'
import type { AuthResponse } from '../api/types'

interface AuthApi {
  session: Session | null
  login: (email: string, password: string) => Promise<void>
  register: (input: RegisterInput) => Promise<void>
  logout: () => void
}

const Ctx = createContext<AuthApi | null>(null)

function toSession(r: AuthResponse): Session {
  return { accessToken: r.accessToken, userId: r.userId, email: r.email, firstName: r.firstName, lastName: r.lastName }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => loadSession())
  const navigate = useNavigate()
  const qc = useQueryClient()

  const clear = useCallback(() => {
    saveSession(null)
    setSession(null)
    qc.clear()
  }, [qc])

  // any 401 from the API (expired / invalid token) ends the session
  useEffect(() => {
    setUnauthorizedHandler(() => {
      clear()
      navigate('/login', { replace: true })
    })
    return () => setUnauthorizedHandler(null)
  }, [clear, navigate])

  const value = useMemo<AuthApi>(() => ({
    session,
    login: async (email, password) => {
      const s = toSession(await api.login(email, password))
      saveSession(s)
      setSession(s)
    },
    register: async (input) => {
      const s = toSession(await api.register(input))
      saveSession(s)
      setSession(s)
    },
    logout: () => {
      clear()
      navigate('/login', { replace: true })
    },
  }), [session, clear, navigate])

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useAuth(): AuthApi {
  const v = useContext(Ctx)
  if (!v) throw new Error('useAuth outside AuthProvider')
  return v
}

/** The signed-in user's id (only call inside protected routes). */
export function useMe(): Session {
  const { session } = useAuth()
  if (!session) throw new Error('not signed in')
  return session
}
