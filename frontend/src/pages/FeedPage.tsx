import { Link } from 'react-router-dom'
import { useInfiniteQuery, useQuery, useQueryClient, type InfiniteData } from '@tanstack/react-query'
import { api } from '../api/endpoints'
import type { Post } from '../api/types'
import { useMe } from '../auth/AuthContext'
import { Composer } from '../components/Composer'
import { PostCard } from '../components/PostCard'
import { Avatar, Empty, ErrorBox, PostSkeleton } from '../components/ui'
import { useUser } from '../components/hooks'
import { fullName } from '../lib/format'

const PAGE = 10

export default function FeedPage() {
  const me = useMe()
  const qc = useQueryClient()
  const key = ['feed', me.userId]
  const { data: user } = useUser(me.userId)

  const feed = useInfiniteQuery({
    queryKey: key,
    initialPageParam: 0,
    queryFn: async ({ pageParam }) => {
      const ids = await api.feed(me.userId, pageParam, PAGE)
      const posts = await api.getPosts(ids)
      return { posts, hasMore: ids.length === PAGE, page: pageParam }
    },
    getNextPageParam: (last) => (last.hasMore ? last.page + 1 : undefined),
  })

  const suggestions = useQuery({ queryKey: ['suggestions', 'side'], queryFn: () => api.suggestions(3) })

  // insert the freshly created post locally (other people's feeds are filled asynchronously via Kafka)
  const onCreated = (p: Post) => {
    qc.setQueryData<InfiniteData<{ posts: Post[]; hasMore: boolean; page: number }>>(key, (old) => {
      if (!old) return { pageParams: [0], pages: [{ posts: [p], hasMore: false, page: 0 }] }
      const [first, ...rest] = old.pages
      return { ...old, pages: [{ ...first, posts: [p, ...first.posts.filter((x) => x.id !== p.id)] }, ...rest] }
    })
    qc.invalidateQueries({ queryKey: ['posts', 'user', me.userId] })
  }

  const onDeleted = (id: string) => {
    qc.setQueryData<InfiniteData<{ posts: Post[]; hasMore: boolean; page: number }>>(key, (old) =>
      old && { ...old, pages: old.pages.map((pg) => ({ ...pg, posts: pg.posts.filter((p) => p.id !== id) })) })
  }

  const posts = feed.data?.pages.flatMap((p) => p.posts) ?? []

  return (
    <div className="grid gap-5 md:grid-cols-[14rem_1fr] lg:grid-cols-[14rem_minmax(0,36rem)_16rem] lg:justify-center">
      <aside className="hidden md:block">
        <div className="card overflow-hidden">
          <div className="h-14 bg-gradient-to-r from-brand-600 to-brand-500" />
          <div className="-mt-7 px-4 pb-4">
            <Link to={`/profile/${me.userId}`}><Avatar first={user?.firstName ?? me.firstName} last={user?.lastName ?? me.lastName} src={user?.profilePhotoUrl} size={56} /></Link>
            <Link to={`/profile/${me.userId}`} className="mt-2 block font-semibold hover:underline">{fullName(user) || `${me.firstName} ${me.lastName}`}</Link>
            <p className="text-xs text-muted">{user?.headline ?? ''}</p>
          </div>
        </div>
      </aside>

      <section className="space-y-4">
        <Composer onCreated={onCreated} />
        {feed.isLoading && <><PostSkeleton /><PostSkeleton /></>}
        {feed.isError && <ErrorBox message={(feed.error as Error).message} onRetry={() => feed.refetch()} />}
        {!feed.isLoading && !feed.isError && posts.length === 0 && (
          <Empty testId="feed-empty">Your feed is empty. Share your first post or connect with people in <Link className="font-semibold text-brand-600 hover:underline" to="/network">My Network</Link>.</Empty>
        )}
        {posts.map((p) => <PostCard key={p.id} post={p} onDeleted={onDeleted} />)}
        {feed.hasNextPage && (
          <button className="btn-outline w-full" disabled={feed.isFetchingNextPage} onClick={() => feed.fetchNextPage()}>
            {feed.isFetchingNextPage ? 'Loading…' : 'Load more'}
          </button>
        )}
      </section>

      <aside className="hidden lg:block">
        <div className="card p-4">
          <h2 className="mb-3 text-sm font-semibold">People you may know</h2>
          <ul className="space-y-3">
            {suggestions.data?.map((u) => (
              <li key={u.id} className="flex items-center gap-2">
                <Avatar first={u.firstName} last={u.lastName} src={u.profilePhotoUrl} size={36} />
                <div className="min-w-0"><Link to={`/profile/${u.id}`} className="block truncate text-sm font-semibold hover:underline">{fullName(u)}</Link>
                  <p className="truncate text-xs text-muted">{u.headline}</p></div>
              </li>
            ))}
            {suggestions.data?.length === 0 && <li className="text-xs text-muted">No suggestions right now.</li>}
          </ul>
          <Link to="/network" className="mt-3 block text-sm font-semibold text-brand-600 hover:underline">See all</Link>
        </div>
      </aside>
    </div>
  )
}
