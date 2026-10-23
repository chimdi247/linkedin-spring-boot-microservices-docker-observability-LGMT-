import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/endpoints'
import { Avatar, Empty, ErrorBox, PageLoading } from '../components/ui'
import { useUser } from '../components/hooks'
import { fullName, timeAgo } from '../lib/format'
import type { PostDocument } from '../api/types'

type Tab = 'people' | 'posts' | 'skills'
const TABS: { id: Tab; label: string }[] = [
  { id: 'people', label: 'People' }, { id: 'posts', label: 'Posts' }, { id: 'skills', label: 'Skills' },
]

function PostResult({ d }: { d: PostDocument }) {
  const { data: a } = useUser(d.authorId)
  return (
    <li data-testid="search-result-post" className="card p-4">
      <div className="flex items-center gap-3">
        <Avatar first={a?.firstName} last={a?.lastName} src={a?.profilePhotoUrl} size={40} />
        <div><Link to={`/profile/${d.authorId}`} className="font-semibold hover:text-brand-600 hover:underline">{fullName(a) || 'Member'}</Link>
          <p className="text-xs text-muted">{timeAgo(d.createdAt)}</p></div>
      </div>
      <p className="mt-2 whitespace-pre-wrap break-words text-[15px]">{d.content}</p>
      {d.imageUrl && <img src={d.imageUrl} alt="" loading="lazy" className="mt-2 max-h-64 rounded-lg object-cover" />}
    </li>
  )
}

export default function SearchPage() {
  const [params, setParams] = useSearchParams()
  const q = (params.get('q') ?? '').trim()
  const raw = params.get('type')
  const type: Tab = raw === 'posts' || raw === 'skills' ? raw : 'people'

  const results = useQuery({
    queryKey: ['search', type, q],
    enabled: q.length > 0,
    queryFn: () => (type === 'people' ? api.searchPeople(q) : type === 'skills' ? api.searchSkills(q) : api.searchPosts(q)),
  })

  const setTab = (t: Tab) => setParams({ q, type: t })
  const list = (results.data ?? []) as unknown[]

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="card p-2">
        <div role="tablist" className="flex gap-1">
          {TABS.map((t) => (
            <button key={t.id} role="tab" aria-selected={type === t.id} data-testid={`search-tab-${t.id}`} onClick={() => setTab(t.id)}
              className={`flex-1 rounded-lg px-4 py-2 text-sm font-semibold transition ${type === t.id ? 'bg-brand-600 text-white' : 'text-muted hover:bg-black/5'}`}>{t.label}</button>
          ))}
        </div>
      </div>
      {q ? <p className="text-sm text-muted">Results for “{q}”</p> : <Empty testId="search-empty">Type something in the search box to get started.</Empty>}
      {results.isLoading && <PageLoading />}
      {results.isError && <ErrorBox message={(results.error as Error).message} onRetry={() => results.refetch()} />}
      {q && results.isSuccess && list.length === 0 && <Empty testId="search-empty">No results for “{q}”. New people and posts can take a second or two to become searchable.</Empty>}
      <ul className="space-y-2">
        {type !== 'posts' && (results.data as import('../api/types').UserDocument[] | undefined)?.map((d) => (
          <li key={d.id} data-testid="search-result-person" className="card flex items-center gap-3 p-4">
            <Link to={`/profile/${d.id}`}><Avatar first={d.firstName} last={d.lastName} src={d.profilePhotoUrl} size={56} /></Link>
            <div className="min-w-0 flex-1">
              <Link to={`/profile/${d.id}`} className="block truncate font-semibold hover:text-brand-600 hover:underline">{d.firstName} {d.lastName}</Link>
              <p className="truncate text-sm text-muted">{d.headline}{d.location ? ` · ${d.location}` : ''}</p>
              {d.skills && d.skills.length > 0 && <p className="mt-1 truncate text-xs text-brand-700">{d.skills.join(' · ')}</p>}
            </div>
          </li>
        ))}
        {type === 'posts' && (results.data as PostDocument[] | undefined)?.map((d) => <PostResult key={d.id} d={d} />)}
      </ul>
    </div>
  )
}
