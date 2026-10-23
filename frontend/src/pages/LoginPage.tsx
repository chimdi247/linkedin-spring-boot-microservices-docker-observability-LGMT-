import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { AuthShell } from './AuthShell'

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null); setBusy(true)
    try {
      await login(email.trim(), password)
      navigate('/', { replace: true })
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell title="Sign in" subtitle="Stay updated on your professional world">
      <form data-testid="login-form" onSubmit={submit} className="space-y-4">
        {error && <div data-testid="login-error" role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>}
        <div><label className="label" htmlFor="login-email">Email</label>
          <input id="login-email" data-testid="login-email" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} className="input" /></div>
        <div><label className="label" htmlFor="login-password">Password</label>
          <input id="login-password" data-testid="login-password" type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} className="input" /></div>
        <button data-testid="login-submit" type="submit" disabled={busy} className="btn-primary w-full py-2.5">
          {busy && <Loader2 size={16} className="animate-spin" />} Sign in
        </button>
      </form>
      <p className="mt-6 text-center text-sm text-muted">
        New here? <Link data-testid="go-register" to="/register" className="font-semibold text-brand-600 hover:underline">Join now</Link>
      </p>
    </AuthShell>
  )
}
