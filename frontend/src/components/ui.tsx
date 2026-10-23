import type { ReactNode } from 'react'
import { Loader2 } from 'lucide-react'
import { initials } from '../lib/format'

export function Avatar({ first, last, src, size = 40 }: { first?: string | null; last?: string | null; src?: string | null; size?: number }) {
  const style = { width: size, height: size, fontSize: Math.max(11, size * 0.38) }
  if (src) {
    return <img src={src} alt="" style={style} className="shrink-0 rounded-full bg-brand-100 object-cover" loading="lazy" />
  }
  return (
    <span style={style} aria-hidden className="inline-flex shrink-0 select-none items-center justify-center rounded-full bg-gradient-to-br from-brand-500 to-brand-700 font-semibold text-white">
      {initials(first, last)}
    </span>
  )
}

export function PageLoading({ label = 'Loading…' }: { label?: string }) {
  return (
    <div data-testid="page-loading" className="flex items-center justify-center gap-2 py-16 text-muted" role="status">
      <Loader2 className="animate-spin" size={20} /> {label}
    </div>
  )
}

export function Skeleton({ className = '' }: { className?: string }) {
  return <div className={`animate-pulse rounded bg-black/10 ${className}`} />
}

export function PostSkeleton() {
  return (
    <div className="card space-y-3 p-4" aria-hidden>
      <div className="flex gap-3"><Skeleton className="h-10 w-10 rounded-full" /><div className="flex-1 space-y-2"><Skeleton className="h-3 w-1/3" /><Skeleton className="h-3 w-1/5" /></div></div>
      <Skeleton className="h-3 w-full" /><Skeleton className="h-3 w-4/5" />
    </div>
  )
}

export function ErrorBox({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="card p-6 text-center text-sm text-red-700">
      <p>{message}</p>
      {onRetry && <button className="btn-outline mt-3" onClick={onRetry}>Try again</button>}
    </div>
  )
}

export function Empty({ children, testId }: { children: ReactNode; testId?: string }) {
  return <div data-testid={testId} className="card p-8 text-center text-sm text-muted">{children}</div>
}
