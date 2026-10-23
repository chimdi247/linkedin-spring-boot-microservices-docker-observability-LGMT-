import { useRef, useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { ImagePlus, Loader2, X } from 'lucide-react'
import { api } from '../api/endpoints'
import type { Post } from '../api/types'
import { useMe } from '../auth/AuthContext'
import { useUser } from './hooks'
import { Avatar } from './ui'
import { useToast } from './Toast'
import type { ApiError } from '../api/client'

const MAX_BYTES = 10 * 1024 * 1024

export function Composer({ onCreated }: { onCreated: (p: Post) => void }) {
  const me = useMe()
  const { data: user } = useUser(me.userId)
  const { toast } = useToast()
  const [text, setText] = useState('')
  const [image, setImage] = useState<File | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const fileRef = useRef<HTMLInputElement>(null)

  const pick = (f: File | null) => {
    if (preview) URL.revokeObjectURL(preview)
    if (f && f.size > MAX_BYTES) { toast('Image is too large (max 10 MB)', 'error'); if (fileRef.current) fileRef.current.value = ''; return }
    if (f && !f.type.startsWith('image/')) { toast('Please choose an image file', 'error'); if (fileRef.current) fileRef.current.value = ''; return }
    setImage(f); setPreview(f ? URL.createObjectURL(f) : null)
  }

  const create = useMutation({
    mutationFn: () => api.createPost(text.trim(), image),
    onSuccess: (p) => {
      setText(''); pick(null); if (fileRef.current) fileRef.current.value = ''
      toast('Post published')
      onCreated(p)
    },
    onError: (e: ApiError) => toast(e.message, 'error'),
  })

  const submit = (e: FormEvent) => { e.preventDefault(); if (text.trim()) create.mutate() }

  return (
    <form onSubmit={submit} className="card p-4">
      <div className="flex gap-3">
        <Avatar first={user?.firstName ?? me.firstName} last={user?.lastName ?? me.lastName} src={user?.profilePhotoUrl} size={48} />
        <textarea data-testid="composer-input" value={text} onChange={(e) => setText(e.target.value)} rows={text ? 4 : 2} maxLength={5000}
          placeholder="Start a post — what do you want to talk about?" aria-label="Post content"
          className="w-full resize-none rounded-2xl border border-black/20 px-4 py-3 text-sm outline-none transition focus:border-brand-600 focus:ring-2 focus:ring-brand-600/25" />
      </div>
      {preview && (
        <div className="relative mt-3">
          <img src={preview} alt="Selected" className="max-h-72 w-full rounded-lg object-cover" />
          <button type="button" aria-label="Remove image" onClick={() => { pick(null); if (fileRef.current) fileRef.current.value = '' }} className="absolute right-2 top-2 rounded-full bg-black/60 p-1 text-white"><X size={16} /></button>
        </div>
      )}
      <div className="mt-3 flex items-center justify-between">
        <label className="btn-ghost cursor-pointer">
          <ImagePlus size={18} /> Photo
          <input ref={fileRef} data-testid="composer-image-input" type="file" accept="image/*" className="sr-only" onChange={(e) => pick(e.target.files?.[0] ?? null)} />
        </label>
        <button data-testid="composer-submit" type="submit" disabled={!text.trim() || create.isPending} className="btn-primary">
          {create.isPending && <Loader2 size={16} className="animate-spin" />} Post
        </button>
      </div>
    </form>
  )
}
