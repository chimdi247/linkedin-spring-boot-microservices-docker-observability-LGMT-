import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/endpoints'
import type { Connection } from '../api/types'
import { useMe } from '../auth/AuthContext'
import { PersonCard } from '../components/PersonCard'
import { Empty, ErrorBox, Skeleton } from '../components/ui'
import { useUser } from '../components/hooks'
import { useToast } from '../components/Toast'
import type { ApiError } from '../api/client'
import { pluralize } from '../lib/format'

function Invitation({ c }: { c: Connection }) {
  const qc = useQueryClient()
  const me = useMe()
  const { toast } = useToast()
  const { data: u } = useUser(c.requesterId)
  const done = () => {
    qc.invalidateQueries({ queryKey: ['pending', me.userId] })
    qc.invalidateQueries({ queryKey: ['connections', me.userId] })
    qc.invalidateQueries({ queryKey: ['suggestions'] })
  }
  const accept = useMutation({ mutationFn: () => api.accept(c.id), onSuccess: () => { toast('Connection accepted'); done() }, onError: (e: ApiError) => toast(e.message, 'error') })
  const reject = useMutation({ mutationFn: () => api.reject(c.id), onSuccess: () => { toast('Invitation ignored'); done() }, onError: (e: ApiError) => toast(e.message, 'error') })
  return (
    <PersonCard testId="invitation-card" id={c.requesterId} first={u?.firstName} last={u?.lastName} headline={u?.headline} photo={u?.profilePhotoUrl}
      actions={<>
        <button data-testid="invitation-reject" className="btn-ghost" disabled={reject.isPending || accept.isPending} onClick={() => reject.mutate()}>Ignore</button>
        <button data-testid="invitation-accept" className="btn-outline" disabled={reject.isPending || accept.isPending} onClick={() => accept.mutate()}>Accept</button>
      </>} />
  )
}

export default function NetworkPage() {
  const me = useMe()
  const qc = useQueryClient()
  const { toast } = useToast()
  const pending = useQuery({ queryKey: ['pending', me.userId], queryFn: () => api.pending(me.userId) })
  const connections = useQuery({ queryKey: ['connections', me.userId], queryFn: () => api.connections(me.userId) })
  const suggestions = useQuery({ queryKey: ['suggestions', 'page'], queryFn: () => api.suggestions(12) })

  const connect = useMutation({
    mutationFn: (id: string) => api.connect(id),
    onSuccess: (_d, id) => {
      toast('Invitation sent')
      qc.setQueryData<import('../api/types').User[]>(['suggestions', 'page'], (old) => old?.filter((u) => u.id !== id))
      qc.invalidateQueries({ queryKey: ['connection-status', id] })
    },
    onError: (e: ApiError) => toast(e.message, 'error'),
  })

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <section>
        <h2 className="mb-3 text-lg font-semibold">Invitations {pending.data && pending.data.length > 0 && <span className="text-muted">({pending.data.length})</span>}</h2>
        {pending.isLoading && <Skeleton className="h-20 w-full" />}
        {pending.isError && <ErrorBox message={(pending.error as Error).message} onRetry={() => pending.refetch()} />}
        <div className="space-y-2">{pending.data?.map((c) => <Invitation key={c.id} c={c} />)}</div>
        {pending.data?.length === 0 && <Empty>No pending invitations.</Empty>}
      </section>

      <section>
        <h2 className="mb-3 text-lg font-semibold">Your connections {connections.data && <span className="text-muted">· {pluralize(connections.data.length, 'person', 'people')}</span>}</h2>
        {connections.isLoading && <Skeleton className="h-20 w-full" />}
        <div className="grid gap-2 sm:grid-cols-2">
          {connections.data?.map((u) => (
            <PersonCard key={u.id} testId="connection-card" id={u.id} first={u.firstName} last={u.lastName} headline={u.headline} photo={u.profilePhotoUrl} />
          ))}
        </div>
        {connections.data?.length === 0 && <Empty>You have no connections yet — connect with someone below.</Empty>}
      </section>

      <section>
        <h2 className="mb-3 text-lg font-semibold">People you may know</h2>
        {suggestions.isLoading && <Skeleton className="h-20 w-full" />}
        <div className="grid gap-2 sm:grid-cols-2">
          {suggestions.data?.map((u) => (
            <PersonCard key={u.id} testId="suggestion-card" id={u.id} first={u.firstName} last={u.lastName} headline={u.headline} photo={u.profilePhotoUrl}
              actions={<button data-testid="suggestion-connect" className="btn-outline" disabled={connect.isPending} onClick={() => connect.mutate(u.id)}>Connect</button>} />
          ))}
        </div>
        {suggestions.data?.length === 0 && <Empty>No more suggestions right now.</Empty>}
      </section>
    </div>
  )
}
