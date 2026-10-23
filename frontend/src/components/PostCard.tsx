import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { MessageCircle, ThumbsUp, Trash2 } from 'lucide-react'
import { api } from '../api/endpoints'
import type { Post, PostComment } from '../api/types'
import { useMe } from '../auth/AuthContext'
import { fullName, timeAgo } from '../lib/format'
import { Avatar } from './ui'
import { useToast } from './Toast'
import { useUser } from './hooks'
import { ApiError } from '../api/client'

function CommentRow({ c }: { c: PostComment }) {
  const { data: u } = useUser(c.authorId)
  return (
    <li data-testid="comment-item" className="flex gap-2">
      <Avatar first={u?.firstName} last={u?.lastName} src={u?.profilePhotoUrl} size={32} />
      <div className="flex-1 rounded-xl bg-canvas px-3 py-2 text-sm">
        <div className="flex items-baseline justify-between gap-2">
          <Link to={`/profile/${c.authorId}`} className="font-semibold hover:text-brand-600 hover:underline">{fullName(u) || 'Member'}</Link>
          <span className="text-xs text-muted">{timeAgo(c.createdAt)}</span>
        </div>
        <p className="whitespace-pre-wrap break-words">{c.content}</p>
      </div>
    </li>
  )
}

export function PostCard({ post, onDeleted }: { post: Post; onDeleted?: (id: string) => void }) {
  const me = useMe()
  const { toast } = useToast()
  const qc = useQueryClient()
  const { data: author } = useUser(post.authorId)

  const [liked, setLiked] = useState(post.likedByMe)
  const [likes, setLikes] = useState(post.likeCount)
  const [commentCount, setCommentCount] = useState(post.commentCount)
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState('')

  // fresh server data (e.g. after a refetch) wins over local optimistic state
  useEffect(() => {
    setLiked(post.likedByMe); setLikes(post.likeCount); setCommentCount(post.commentCount)
  }, [post.id, post.likedByMe, post.likeCount, post.commentCount])

  const comments = useQuery({ queryKey: ['comments', post.id], queryFn: () => api.comments(post.id), enabled: open })

  const like = useMutation({
    mutationFn: () => api.toggleLike(post.id),
    onMutate: () => {
      setLiked((l) => !l)
      setLikes((n) => Math.max(0, n + (liked ? -1 : 1)))
    },
    onError: (e: ApiError) => {
      setLiked(post.likedByMe); setLikes(post.likeCount)
      toast(e.message, 'error')
    },
  })

  const addComment = useMutation({
    mutationFn: (text: string) => api.addComment(post.id, text),
    onSuccess: (c) => {
      setDraft('')
      setCommentCount((n) => n + 1)
      qc.setQueryData<PostComment[]>(['comments', post.id], (old) => [c, ...(old ?? [])])
    },
    onError: (e: ApiError) => toast(e.message, 'error'),
  })

  const del = useMutation({
    mutationFn: () => api.deletePost(post.id),
    onSuccess: () => { toast('Post deleted'); onDeleted?.(post.id) },
    onError: (e: ApiError) => toast(e.message, 'error'),
  })

  const submitComment = (e: FormEvent) => {
    e.preventDefault()
    const t = draft.trim()
    if (t) addComment.mutate(t)
  }

  return (
    <article data-testid="post-card" className="card overflow-hidden">
      <div className="flex items-start gap-3 p-4 pb-2">
        <Avatar first={author?.firstName} last={author?.lastName} src={author?.profilePhotoUrl} size={48} />
        <div className="min-w-0 flex-1">
          <Link data-testid="post-author-link" to={`/profile/${post.authorId}`} className="block truncate font-semibold hover:text-brand-600 hover:underline">
            {fullName(author) || 'Member'}
          </Link>
          {author?.headline && <p className="truncate text-xs text-muted">{author.headline}</p>}
          <p className="text-xs text-muted">{timeAgo(post.createdAt)}</p>
        </div>
        {post.authorId === me.userId && (
          <button data-testid="post-delete-button" aria-label="Delete post" disabled={del.isPending}
            onClick={() => { if (window.confirm('Delete this post?')) del.mutate() }}
            className="rounded-full p-2 text-muted hover:bg-black/5 hover:text-red-700">
            <Trash2 size={18} />
          </button>
        )}
      </div>

      <p data-testid="post-content" className="whitespace-pre-wrap break-words px-4 pb-3 text-[15px] leading-relaxed">{post.content}</p>
      {post.imageUrl && <img src={post.imageUrl} alt="" loading="lazy" className="max-h-[32rem] w-full bg-black/5 object-cover" />}

      <div className="flex items-center gap-1 border-t border-black/10 px-2 py-1">
        <button data-testid="post-like-button" aria-pressed={liked} onClick={() => like.mutate()} disabled={like.isPending}
          className={`flex flex-1 items-center justify-center gap-2 rounded-lg px-3 py-2 text-sm font-semibold transition hover:bg-black/5 ${liked ? 'text-brand-600' : 'text-muted'}`}>
          <ThumbsUp size={18} fill={liked ? 'currentColor' : 'none'} /> Like
          <span data-testid="post-like-count" className="font-normal">{likes}</span>
        </button>
        <button data-testid="post-comment-toggle" aria-expanded={open} onClick={() => setOpen((o) => !o)}
          className="flex flex-1 items-center justify-center gap-2 rounded-lg px-3 py-2 text-sm font-semibold text-muted transition hover:bg-black/5">
          <MessageCircle size={18} /> Comment
          <span data-testid="post-comment-count" className="font-normal">{commentCount}</span>
        </button>
      </div>

      {open && (
        <div className="space-y-3 border-t border-black/10 p-4">
          <form onSubmit={submitComment} className="flex gap-2">
            <input data-testid="post-comment-input" value={draft} onChange={(e) => setDraft(e.target.value)} maxLength={1000}
              placeholder="Add a comment…" aria-label="Add a comment" className="input rounded-full" />
            <button data-testid="post-comment-submit" type="submit" disabled={!draft.trim() || addComment.isPending} className="btn-primary">Post</button>
          </form>
          {comments.isLoading && <p className="text-sm text-muted">Loading comments…</p>}
          <ul className="space-y-2">{comments.data?.map((c) => <CommentRow key={c.id} c={c} />)}</ul>
        </div>
      )}
    </article>
  )
}
