import { request } from './client'
import type {
  AppNotification, AuthResponse, Connection, ConnectionStatus, Post, PostComment,
  PostDocument, User, UserDocument,
} from './types'

export interface RegisterInput {
  firstName: string
  lastName: string
  email: string
  password: string
  headline?: string
  location?: string
}

export const api = {
  // auth
  login: (email: string, password: string) =>
    request<AuthResponse>('/auth/login', { method: 'POST', body: { email, password }, auth: false }),
  register: (input: RegisterInput) =>
    request<AuthResponse>('/auth/register', { method: 'POST', body: input, auth: false }),

  // users
  getUser: (id: string) => request<User>(`/users/${id}`),
  updateProfile: (id: string, p: { headline: string; about: string; location: string; skills: string[] }) =>
    request<User>(`/users/${id}/profile`, { method: 'PUT', body: p }),
  uploadPhoto: (id: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request<User>(`/users/${id}/profile-photo`, { method: 'POST', form })
  },
  suggestions: (limit = 12) => request<User[]>(`/users/suggestions?limit=${limit}`),
  connectionStatus: (id: string) => request<ConnectionStatus>(`/users/${id}/connection-status`),
  connect: (id: string) => request<string>(`/users/${id}/connect`, { method: 'POST' }),
  accept: (connectionId: string) => request<string>(`/users/connection/${connectionId}/accept`, { method: 'PUT' }),
  reject: (connectionId: string) => request<string>(`/users/connection/${connectionId}/reject`, { method: 'PUT' }),
  pending: (id: string) => request<Connection[]>(`/users/${id}/connections/pending`),
  connections: (id: string) => request<User[]>(`/users/${id}/connections`),

  // posts
  createPost: (content: string, image?: File | null) => {
    const form = new FormData()
    form.append('content', content)
    if (image) form.append('image', image)
    return request<Post>('/posts', { method: 'POST', form })
  },
  getPosts: (ids: string[]) =>
    ids.length ? request<Post[]>(`/posts?ids=${ids.map(encodeURIComponent).join(',')}`) : Promise.resolve([] as Post[]),
  userPosts: (userId: string) => request<Post[]>(`/posts/user/${userId}`),
  toggleLike: (id: string) => request<string>(`/posts/${id}/like`, { method: 'POST' }),
  comments: (id: string) => request<PostComment[]>(`/posts/${id}/comments`),
  addComment: (id: string, content: string) =>
    request<PostComment>(`/posts/${id}/comments`, { method: 'POST', urlencoded: { content } }),
  deletePost: (id: string) => request<string>(`/posts/${id}`, { method: 'DELETE' }),

  // feed
  feed: (userId: string, page: number, size: number) =>
    request<string[]>(`/feed/${userId}?page=${page}&size=${size}`),

  // search
  searchPeople: (q: string) => request<UserDocument[]>(`/search/people?q=${encodeURIComponent(q)}`),
  searchSkills: (skill: string) => request<UserDocument[]>(`/search/skills?skill=${encodeURIComponent(skill)}`),
  searchPosts: (q: string) => request<PostDocument[]>(`/search/posts?q=${encodeURIComponent(q)}`),

  // notifications
  notifications: () => request<AppNotification[]>('/notifications'),
  unreadCount: () => request<{ count: number }>('/notifications/unread-count'),
  readAll: () => request<void>('/notifications/read-all', { method: 'POST' }),
}
