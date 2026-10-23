import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Avatar } from './ui'

export function PersonCard({ id, first, last, headline, photo, testId, actions }: {
  id: string; first?: string | null; last?: string | null; headline?: string | null; photo?: string | null
  testId: string; actions?: ReactNode
}) {
  return (
    <div data-testid={testId} className="card flex items-center gap-3 p-4">
      <Link to={`/profile/${id}`}><Avatar first={first} last={last} src={photo} size={56} /></Link>
      <div className="min-w-0 flex-1">
        <Link to={`/profile/${id}`} className="block truncate font-semibold hover:text-brand-600 hover:underline">{`${first ?? ''} ${last ?? ''}`.trim() || 'Member'}</Link>
        {headline && <p className="truncate text-sm text-muted">{headline}</p>}
      </div>
      {actions && <div className="flex shrink-0 gap-2">{actions}</div>}
    </div>
  )
}
