import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell, FileText, Hand, UserPlus, UserCheck, MessageSquare, ThumbsUp } from 'lucide-react'
import { api } from '../api/endpoints'
import type { AppNotification } from '../api/types'
import { useMe } from '../auth/AuthContext'
import { Empty, ErrorBox, PageLoading } from '../components/ui'
import { useToast } from '../components/Toast'
import { timeAgo } from '../lib/format'
import type { ApiError } from '../api/client'

const ICONS = { WELCOME: Hand, CONNECTION_REQUEST: UserPlus, CONNECTION_ACCEPTED: UserCheck, POST_LIKED: ThumbsUp, POST_COMMENTED: MessageSquare } as const

/** Where a notification leads: people -> their profile, post events -> my own profile (my posts live there). */
export function notificationLink(n: AppNotification, meId: string): string {
  if (n.type === 'POST_LIKED' || n.type === 'POST_COMMENTED') return `/profile/${meId}`
  return n.refId ? `/profile/${n.refId}` : '/'
}

export default function NotificationsPage() {
  const me = useMe()
  const qc = useQueryClient()
  const { toast } = useToast()
  const list = useQuery({ queryKey: ['notifications'], queryFn: api.notifications })
  const readAll = useMutation({
    mutationFn: api.readAll,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['notifications'] })
      qc.setQueryData(['unread-count'], { count: 0 })
      toast('All notifications marked as read')
    },
    onError: (e: ApiError) => toast(e.message, 'error'),
  })
  const unread = list.data?.filter((n) => !n.read).length ?? 0

  return (
    <div className="mx-auto max-w-2xl space-y-3">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">Notifications</h1>
        <button data-testid="notifications-mark-all-read" className="btn-outline" disabled={unread === 0 || readAll.isPending} onClick={() => readAll.mutate()}>Mark all as read</button>
      </div>
      {list.isLoading && <PageLoading />}
      {list.isError && <ErrorBox message={(list.error as Error).message} onRetry={() => list.refetch()} />}
      {list.data?.length === 0 && <Empty testId="notifications-empty"><Bell className="mx-auto mb-2" /> You are all caught up.</Empty>}
      <ul className="space-y-2">
        {list.data?.map((n) => {
          const Icon = ICONS[n.type] ?? FileText
          return (
            <li key={n.id} data-testid="notification-item" data-read={n.read}>
              <Link to={notificationLink(n, me.userId)} className={`card flex items-start gap-3 p-4 transition hover:bg-brand-50 ${n.read ? '' : 'border-l-4 border-brand-600 bg-brand-50/60'}`}>
                <span className="mt-0.5 rounded-full bg-brand-100 p-2 text-brand-700"><Icon size={18} /></span>
                <span className="min-w-0 flex-1"><span className="block font-semibold">{n.title}</span><span className="block text-sm text-ink/80">{n.message}</span></span>
                <span className="shrink-0 text-xs text-muted">{timeAgo(n.createdAt)}</span>
              </Link>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
