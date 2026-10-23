/** Pure helpers (unit tested). */

export function initials(first?: string | null, last?: string | null): string {
  const a = (first ?? '').trim().charAt(0)
  const b = (last ?? '').trim().charAt(0)
  return (a + b).toUpperCase() || '?'
}

export function fullName(u?: { firstName?: string | null; lastName?: string | null } | null): string {
  if (!u) return ''
  return `${u.firstName ?? ''} ${u.lastName ?? ''}`.trim()
}

/** Server timestamps are LocalDateTime strings in UTC without a zone. */
export function parseServerDate(value?: string | null): Date | null {
  if (!value) return null
  const hasZone = /([zZ]|[+-]\d\d:?\d\d)$/.test(value)
  const d = new Date(hasZone ? value : value + 'Z')
  return Number.isNaN(d.getTime()) ? null : d
}

export function timeAgo(value?: string | null, now: Date = new Date()): string {
  const d = parseServerDate(value)
  if (!d) return ''
  const s = Math.max(0, Math.floor((now.getTime() - d.getTime()) / 1000))
  if (s < 60) return 'just now'
  const m = Math.floor(s / 60)
  if (m < 60) return `${m}m`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}h`
  const days = Math.floor(h / 24)
  if (days < 7) return `${days}d`
  const w = Math.floor(days / 7)
  if (w < 5) return `${w}w`
  return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
}

/** "java, Spring ,java" -> ["java","Spring"] (trimmed, de-duplicated case-insensitively). */
export function parseSkills(input: string): string[] {
  const seen = new Set<string>()
  const out: string[] = []
  for (const raw of input.split(/[,\n]/)) {
    const s = raw.trim()
    if (!s) continue
    const k = s.toLowerCase()
    if (seen.has(k)) continue
    seen.add(k)
    out.push(s)
  }
  return out
}

export function pluralize(n: number, one: string, many = one + 's'): string {
  return `${n} ${n === 1 ? one : many}`
}
