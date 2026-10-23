import { useState, type FormEvent } from 'react'
import { useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Camera, MapPin, Pencil } from 'lucide-react'
import { api } from '../api/endpoints'
import type { ConnectionState } from '../api/types'
import { useMe } from '../auth/AuthContext'
import { PostCard } from '../components/PostCard'
import { Avatar, Empty, ErrorBox, PageLoading } from '../components/ui'
import { useUser } from '../components/hooks'
import { useToast } from '../components/Toast'
import type { ApiError } from '../api/client'
import { fullName, parseSkills } from '../lib/format'

const STATUS_LABEL: Record<ConnectionState, string> = {
  SELF: 'This is you',
  NONE: 'Not connected',
  PENDING_SENT: 'Invitation sent',
  PENDING_RECEIVED: 'Wants to connect with you',
  CONNECTED: 'Connected',
  REJECTED: 'Invitation declined',
}

export default function ProfilePage() {
  const me = useMe()
  const params = useParams()
  const userId = params.userId ?? me.userId
  const isSelf = userId === me.userId
  const qc = useQueryClient()
  const { toast } = useToast()
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState({ headline: '', about: '', location: '', skills: '' })

  const user = useUser(userId)
  const status = useQuery({ queryKey: ['connection-status', userId], queryFn: () => api.connectionStatus(userId), enabled: !isSelf })
  const posts = useQuery({ queryKey: ['posts', 'user', userId], queryFn: () => api.userPosts(userId) })

  const save = useMutation({
    mutationFn: () => api.updateProfile(userId, { headline: form.headline.trim(), about: form.about.trim(), location: form.location.trim(), skills: parseSkills(form.skills) }),
    onSuccess: (u) => { qc.setQueryData(['user', userId], u); setEditing(false); toast('Profile updated') },
    onError: (e: ApiError) => toast(e.message, 'error'),
  })
  const photo = useMutation({
    mutationFn: (f: File) => api.uploadPhoto(userId, f),
    onSuccess: (u) => { qc.setQueryData(['user', userId], u); toast('Profile photo updated') },
    onError: (e: ApiError) => toast(e.message, 'error'),
  })
  const connect = useMutation({
    mutationFn: () => api.connect(userId),
    onSuccess: () => { toast('Invitation sent'); qc.invalidateQueries({ queryKey: ['connection-status', userId] }) },
    onError: (e: ApiError) => toast(e.message, 'error'),
  })

  if (user.isLoading) return <PageLoading />
  if (user.isError || !user.data) return <ErrorBox message={(user.error as Error)?.message ?? 'Profile not found'} onRetry={() => user.refetch()} />
  const u = user.data

  const startEdit = () => {
    setForm({ headline: u.headline ?? '', about: u.about ?? '', location: u.location ?? '', skills: (u.skills ?? []).join(', ') })
    setEditing(true)
  }
  const submit = (e: FormEvent) => { e.preventDefault(); save.mutate() }
  const st = status.data?.status

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <section className="card overflow-hidden">
        <div className="h-32 bg-gradient-to-r from-brand-700 via-brand-600 to-brand-500 sm:h-40" />
        <div className="px-6 pb-6">
          <div className="-mt-14 flex items-end justify-between">
            <div className="relative rounded-full ring-4 ring-white">
              <Avatar first={u.firstName} last={u.lastName} src={u.profilePhotoUrl} size={112} />
              {isSelf && (
                <label className="absolute bottom-0 right-0 cursor-pointer rounded-full bg-white p-2 shadow ring-1 ring-black/10 hover:bg-brand-50" title="Change photo">
                  <Camera size={16} />
                  <input data-testid="profile-photo-input" type="file" accept="image/*" className="sr-only"
                    onChange={(e) => { const f = e.target.files?.[0]; if (f) photo.mutate(f); e.target.value = '' }} />
                </label>
              )}
            </div>
            {isSelf && !editing && <button data-testid="profile-edit-button" className="btn-outline" onClick={startEdit}><Pencil size={16} /> Edit profile</button>}
          </div>

          <h1 data-testid="profile-name" className="mt-3 text-2xl font-semibold">{fullName(u)}</h1>
          <p data-testid="profile-headline" className="text-ink/80">{u.headline}</p>
          {u.location && <p className="mt-1 flex items-center gap-1 text-sm text-muted"><MapPin size={14} />{u.location}</p>}

          {!isSelf && (
            <div className="mt-4 flex items-center gap-3">
              {st === 'NONE' && <button data-testid="profile-connect-button" className="btn-primary" disabled={connect.isPending} onClick={() => connect.mutate()}>Connect</button>}
              <span data-testid="profile-status" className="rounded-full bg-brand-50 px-3 py-1 text-sm font-medium text-brand-700">{st ? STATUS_LABEL[st] : '…'}</span>
            </div>
          )}
        </div>
      </section>

      {editing ? (
        <form data-testid="profile-form" onSubmit={submit} className="card space-y-4 p-6">
          <div><label className="label" htmlFor="p-h">Headline</label><input id="p-h" data-testid="profile-headline-input" className="input" value={form.headline} onChange={(e) => setForm({ ...form, headline: e.target.value })} /></div>
          <div><label className="label" htmlFor="p-l">Location</label><input id="p-l" data-testid="profile-location-input" className="input" value={form.location} onChange={(e) => setForm({ ...form, location: e.target.value })} /></div>
          <div><label className="label" htmlFor="p-a">About</label><textarea id="p-a" data-testid="profile-about-input" rows={5} className="input" value={form.about} onChange={(e) => setForm({ ...form, about: e.target.value })} /></div>
          <div><label className="label" htmlFor="p-s">Skills (comma separated)</label><input id="p-s" data-testid="profile-skills-input" className="input" value={form.skills} onChange={(e) => setForm({ ...form, skills: e.target.value })} placeholder="Java, Kafka, React" /></div>
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-ghost" onClick={() => setEditing(false)}>Cancel</button>
            <button data-testid="profile-save" type="submit" className="btn-primary" disabled={save.isPending}>Save</button>
          </div>
        </form>
      ) : (
        <>
          <section className="card p-6">
            <h2 className="mb-2 text-lg font-semibold">About</h2>
            <p data-testid="profile-about" className="whitespace-pre-wrap text-[15px] text-ink/90">{u.about || (isSelf ? 'Tell people about yourself.' : '')}</p>
          </section>
          <section className="card p-6">
            <h2 className="mb-3 text-lg font-semibold">Skills</h2>
            <div className="flex flex-wrap gap-2">
              {(u.skills ?? []).map((s) => <span key={s} data-testid="profile-skill" className="rounded-full border border-brand-600/30 bg-brand-50 px-3 py-1 text-sm font-medium text-brand-700">{s}</span>)}
              {(u.skills ?? []).length === 0 && <span className="text-sm text-muted">No skills listed.</span>}
            </div>
          </section>
        </>
      )}

      <section className="space-y-3">
        <h2 className="text-lg font-semibold">Posts</h2>
        {posts.isLoading && <PageLoading />}
        {posts.data?.map((p) => <PostCard key={p.id} post={p} onDeleted={() => { qc.invalidateQueries({ queryKey: ['posts', 'user', userId] }); qc.invalidateQueries({ queryKey: ['feed'] }) }} />)}
        {posts.data?.length === 0 && <Empty>No posts yet.</Empty>}
      </section>
    </div>
  )
}
