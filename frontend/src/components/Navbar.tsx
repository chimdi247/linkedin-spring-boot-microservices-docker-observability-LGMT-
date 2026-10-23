import { useState, type FormEvent } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Bell, Home, LogOut, Search, UserRound, Users } from 'lucide-react'
import { api } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'

export function Navbar() {
  const { session, logout } = useAuth()
  const navigate = useNavigate()
  const [q, setQ] = useState('')

  const unread = useQuery({
    queryKey: ['unread-count'],
    queryFn: api.unreadCount,
    refetchInterval: 20_000,
    enabled: !!session,
  })
  const count = unread.data?.count ?? 0

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const term = q.trim()
    if (term) navigate(`/search?q=${encodeURIComponent(term)}&type=people`)
  }

  const item = ({ isActive }: { isActive: boolean }) =>
    `relative flex flex-col items-center gap-0.5 px-3 py-1 text-[11px] font-medium transition ${isActive ? 'text-ink' : 'text-muted hover:text-ink'}`

  return (
    <header className="sticky top-0 z-40 border-b border-black/10 bg-white">
      <div className="mx-auto flex h-14 max-w-6xl items-center gap-3 px-3">
        <Link to="/" aria-label="Home" className="flex h-9 w-9 items-center justify-center rounded-lg bg-brand-600 text-lg font-bold text-white">in</Link>
        <form data-testid="nav-search-form" onSubmit={submit} className="relative hidden max-w-xs flex-1 sm:block">
          <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
          <input
            data-testid="nav-search-input"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search people, skills, posts"
            aria-label="Search"
            className="w-full rounded-md bg-brand-50 py-2 pl-9 pr-3 text-sm outline-none focus:ring-2 focus:ring-brand-600/40"
          />
        </form>
        <nav className="ml-auto flex items-center">
          <NavLink to="/" end data-testid="nav-home" className={item}><Home size={22} /><span className="hidden sm:inline">Home</span></NavLink>
          <NavLink to="/network" data-testid="nav-network" className={item}><Users size={22} /><span className="hidden sm:inline">Network</span></NavLink>
          <NavLink to="/notifications" data-testid="nav-notifications" className={item}>
            <span className="relative">
              <Bell size={22} />
              {count > 0 && (
                <span data-testid="nav-notifications-badge" className="absolute -right-2 -top-1.5 min-w-4 rounded-full bg-red-600 px-1 text-center text-[10px] font-bold leading-4 text-white">
                  {count > 99 ? '99+' : count}
                </span>
              )}
            </span>
            <span className="hidden sm:inline">Alerts</span>
          </NavLink>
          <NavLink to={`/profile/${session?.userId ?? ''}`} data-testid="nav-me" className={item}><UserRound size={22} /><span className="hidden sm:inline">Me</span></NavLink>
          <button data-testid="nav-logout" onClick={logout} className="flex flex-col items-center gap-0.5 px-3 py-1 text-[11px] font-medium text-muted transition hover:text-ink">
            <LogOut size={22} /><span className="hidden sm:inline">Sign out</span>
          </button>
        </nav>
      </div>
      <form onSubmit={submit} className="px-3 pb-2 sm:hidden">
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search" aria-label="Search (mobile)" className="w-full rounded-md bg-brand-50 px-3 py-2 text-sm outline-none" />
      </form>
    </header>
  )
}
