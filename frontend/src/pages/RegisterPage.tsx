import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { AuthShell } from './AuthShell'

export default function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [f, setF] = useState({ firstName: '', lastName: '', email: '', password: '', headline: '', location: '' })
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF((s) => ({ ...s, [k]: e.target.value }))

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (f.password.length < 6) { setError('Password must be at least 6 characters'); return }
    setBusy(true)
    try {
      await register({
        firstName: f.firstName.trim(), lastName: f.lastName.trim(), email: f.email.trim(), password: f.password,
        headline: f.headline.trim() || undefined, location: f.location.trim() || undefined,
      })
      navigate('/', { replace: true })
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell title="Join now" subtitle="Make the most of your professional life">
      <form data-testid="register-form" onSubmit={submit} className="space-y-3">
        {error && <div data-testid="register-error" role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>}
        <div className="grid grid-cols-2 gap-3">
          <div><label className="label" htmlFor="r-first">First name</label>
            <input id="r-first" data-testid="register-first-name" required value={f.firstName} onChange={set('firstName')} className="input" autoComplete="given-name" /></div>
          <div><label className="label" htmlFor="r-last">Last name</label>
            <input id="r-last" data-testid="register-last-name" required value={f.lastName} onChange={set('lastName')} className="input" autoComplete="family-name" /></div>
        </div>
        <div><label className="label" htmlFor="r-email">Email</label>
          <input id="r-email" data-testid="register-email" type="email" required value={f.email} onChange={set('email')} className="input" autoComplete="email" /></div>
        <div><label className="label" htmlFor="r-pass">Password (6+ characters)</label>
          <input id="r-pass" data-testid="register-password" type="password" required minLength={6} value={f.password} onChange={set('password')} className="input" autoComplete="new-password" /></div>
        <div><label className="label" htmlFor="r-headline">Headline (optional)</label>
          <input id="r-headline" data-testid="register-headline" value={f.headline} onChange={set('headline')} className="input" placeholder="e.g. Backend engineer" /></div>
        <div><label className="label" htmlFor="r-loc">Location (optional)</label>
          <input id="r-loc" data-testid="register-location" value={f.location} onChange={set('location')} className="input" /></div>
        <button data-testid="register-submit" type="submit" disabled={busy} className="btn-primary w-full py-2.5">
          {busy && <Loader2 size={16} className="animate-spin" />} Agree & Join
        </button>
      </form>
      <p className="mt-6 text-center text-sm text-muted">
        Already on the platform? <Link data-testid="go-login" to="/login" className="font-semibold text-brand-600 hover:underline">Sign in</Link>
      </p>
    </AuthShell>
  )
}
