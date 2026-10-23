export type Role = 'NORMAL_USER' | 'ADMIN' | 'RECRUITER'

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  userId: string
  email: string
  firstName: string
  lastName: string
  tokenType: string
}

export interface User {
  id: string
  email: string
  firstName: string
  lastName: string
  headline: string | null
  about: string | null
  location: string | null
  profilePhotoUrl: string | null
  coverPhotoUrl: string | null
  role: Role
  skills: string[]
  createdAt: string
}

export type ConnectionState = 'SELF' | 'NONE' | 'PENDING_SENT' | 'PENDING_RECEIVED' | 'CONNECTED' | 'REJECTED'

export interface ConnectionStatus {
  status: ConnectionState
  connectionId: string | null
}

export interface Connection {
  id: string
  requesterId: string
  receiverId: string
  status: 'PENDING' | 'CONNECTED' | 'REJECTED'
  createdAt: string
  updatedAt: string | null
}

export interface Post {
  id: string
  authorId: string
  content: string
  imageUrl: string | null
  likeCount: number
  commentCount: number
  likedByMe: boolean
  createdAt: string
  updatedAt: string | null
}

export interface PostComment {
  id: string
  postId: string
  authorId: string
  content: string
  createdAt: string
}

export interface UserDocument {
  id: string
  firstName: string
  lastName: string
  headline: string | null
  location: string | null
  skills: string[] | null
  email: string | null
  profilePhotoUrl: string | null
}

export interface PostDocument {
  id: string
  content: string
  authorId: string
  imageUrl: string | null
  createdAt: string | null
}

export type NotificationType = 'WELCOME' | 'CONNECTION_REQUEST' | 'CONNECTION_ACCEPTED' | 'POST_LIKED' | 'POST_COMMENTED'

export interface AppNotification {
  id: string
  type: NotificationType
  title: string
  message: string
  refId: string | null
  createdAt: string
  read: boolean
}
