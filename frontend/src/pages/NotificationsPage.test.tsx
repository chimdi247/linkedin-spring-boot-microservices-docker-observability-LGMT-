import { describe, expect, it } from 'vitest'
import { notificationLink } from './NotificationsPage'
import type { AppNotification } from '../api/types'

const base: AppNotification = { id: '1', type: 'WELCOME', title: '', message: '', refId: 'u2', createdAt: '', read: false }

describe('notificationLink', () => {
  it('links people notifications to the profile in refId', () => {
    expect(notificationLink({ ...base, type: 'CONNECTION_REQUEST' }, 'me')).toBe('/profile/u2')
  })
  it('links post notifications to my own profile', () => {
    expect(notificationLink({ ...base, type: 'POST_LIKED', refId: 'post9' }, 'me')).toBe('/profile/me')
  })
  it('falls back to home', () => {
    expect(notificationLink({ ...base, refId: null }, 'me')).toBe('/')
  })
})
