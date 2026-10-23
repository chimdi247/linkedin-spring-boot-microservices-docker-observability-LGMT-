import { describe, expect, it } from 'vitest'
import { fullName, initials, parseServerDate, parseSkills, pluralize, timeAgo } from './format'

describe('format helpers', () => {
  it('builds initials with a fallback', () => {
    expect(initials('ada', 'lovelace')).toBe('AL')
    expect(initials(' grace ', undefined)).toBe('G')
    expect(initials('', '')).toBe('?')
  })

  it('joins names', () => {
    expect(fullName({ firstName: 'Ada', lastName: 'Lovelace' })).toBe('Ada Lovelace')
    expect(fullName(null)).toBe('')
  })

  it('treats zone-less server timestamps as UTC', () => {
    expect(parseServerDate('2025-01-01T10:00:00')?.toISOString()).toBe('2025-01-01T10:00:00.000Z')
    expect(parseServerDate('2025-01-01T10:00:00+02:00')?.toISOString()).toBe('2025-01-01T08:00:00.000Z')
    expect(parseServerDate('nope')).toBeNull()
  })

  it('formats relative time', () => {
    const now = new Date('2025-01-01T12:00:00Z')
    expect(timeAgo('2025-01-01T11:59:50', now)).toBe('just now')
    expect(timeAgo('2025-01-01T11:30:00', now)).toBe('30m')
    expect(timeAgo('2025-01-01T09:00:00', now)).toBe('3h')
    expect(timeAgo('2024-12-30T12:00:00', now)).toBe('2d')
    expect(timeAgo(null, now)).toBe('')
  })

  it('parses skills', () => {
    expect(parseSkills('java, Spring ,JAVA,\n kafka,')).toEqual(['java', 'Spring', 'kafka'])
    expect(parseSkills('')).toEqual([])
  })

  it('pluralizes', () => {
    expect(pluralize(1, 'like')).toBe('1 like')
    expect(pluralize(3, 'like')).toBe('3 likes')
  })
})
