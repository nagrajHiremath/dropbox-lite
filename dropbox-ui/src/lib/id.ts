/**
 * crypto.randomUUID() only exists in a secure context (HTTPS, or localhost) -
 * on a plain-HTTP, non-localhost origin the browser doesn't expose it at all
 * (TypeError: crypto.randomUUID is not a function), which is exactly the
 * deployed environment here. Falls back to a Math.random()-based v4 UUID in
 * that case - fine since every caller only needs a unique-enough client-side
 * id (upload tray tracking key, upload idempotency-key header for
 * retry-dedup), not a cryptographically unguessable one.
 */
export function generateId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}
