const BASE = '/api/v1'
const SESSION_KEY = 'linkedin.session'

export interface Session {
  accessToken: string
  userId: string
  email: string
  firstName: string
  lastName: string
}

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export function loadSession(): Session | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    if (!raw) return null
    const s = JSON.parse(raw) as Session
    return s?.accessToken && s?.userId ? s : null
  } catch {
    return null
  }
}

export function saveSession(s: Session | null): void {
  try {
    if (s) localStorage.setItem(SESSION_KEY, JSON.stringify(s))
    else localStorage.removeItem(SESSION_KEY)
  } catch {
    /* storage unavailable: session lives in memory only */
  }
}

let unauthorizedHandler: (() => void) | null = null
export function setUnauthorizedHandler(fn: (() => void) | null): void {
  unauthorizedHandler = fn
}

/** Extracts a human readable message from the service error JSON, with a status based fallback. */
export function messageFromBody(status: number, body: unknown, fallback?: string): string {
  if (body && typeof body === 'object') {
    const m = (body as Record<string, unknown>).message
    if (typeof m === 'string' && m.trim()) return m
  }
  if (typeof body === 'string' && body.trim() && body.length < 300) return body
  if (fallback) return fallback
  if (status === 0) return 'Cannot reach the server. Please try again.'
  if (status === 401) return 'Your session has expired. Please sign in again.'
  if (status === 403) return 'You are not allowed to do that.'
  if (status === 404) return 'Not found.'
  if (status === 413) return 'That file is too large (max 10 MB).'
  return `Something went wrong (${status}).`
}

interface Options {
  method?: string
  body?: unknown
  form?: FormData
  urlencoded?: Record<string, string>
  auth?: boolean // default true
  signal?: AbortSignal
}

export async function request<T>(path: string, opts: Options = {}): Promise<T> {
  const headers: Record<string, string> = {}
  const session = loadSession()
  const useAuth = opts.auth !== false
  if (useAuth && session) headers.Authorization = `Bearer ${session.accessToken}`

  let body: BodyInit | undefined
  if (opts.form) body = opts.form
  else if (opts.urlencoded) {
    headers['Content-Type'] = 'application/x-www-form-urlencoded'
    body = new URLSearchParams(opts.urlencoded).toString()
  } else if (opts.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(opts.body)
  }

  let res: Response
  try {
    res = await fetch(BASE + path, { method: opts.method ?? 'GET', headers, body, signal: opts.signal })
  } catch (e) {
    if ((e as Error)?.name === 'AbortError') throw e
    throw new ApiError(0, messageFromBody(0, null))
  }

  // The gateway answers 401 with an EMPTY body -> parse defensively.
  const text = await res.text().catch(() => '')
  let parsed: unknown = text
  const ct = res.headers.get('content-type') ?? ''
  if (text && ct.includes('json')) {
    try {
      parsed = JSON.parse(text)
    } catch {
      parsed = text
    }
  }

  if (!res.ok) {
    if (res.status === 401 && useAuth) unauthorizedHandler?.()
    throw new ApiError(res.status, messageFromBody(res.status, parsed))
  }
  return (text ? parsed : undefined) as T
}
