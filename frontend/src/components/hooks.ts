import { useQuery } from '@tanstack/react-query'
import { api } from '../api/endpoints'

/** Cached profile lookup (posts, comments, invitations all resolve ids to people). */
export function useUser(id?: string | null) {
  return useQuery({
    queryKey: ['user', id],
    queryFn: () => api.getUser(id as string),
    enabled: !!id,
    staleTime: 5 * 60_000,
  })
}
