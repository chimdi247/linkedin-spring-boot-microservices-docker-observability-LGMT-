import { describe, expect, it } from 'vitest'
import { messageFromBody } from './client'

describe('messageFromBody', () => {
  it('uses the service error message', () => {
    expect(messageFromBody(409, { status: 409, message: 'Email already registered' })).toBe('Email already registered')
  })
  it('handles the gateway empty 401 body', () => {
    expect(messageFromBody(401, '')).toMatch(/session has expired/i)
  })
  it('handles network failures and big uploads', () => {
    expect(messageFromBody(0, null)).toMatch(/cannot reach/i)
    expect(messageFromBody(413, undefined)).toMatch(/too large/i)
  })
})
