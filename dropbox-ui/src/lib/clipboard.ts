/**
 * navigator.clipboard only exists in a secure context (HTTPS, or localhost) -
 * same restriction as crypto.randomUUID (see lib/id.ts). On a plain-HTTP,
 * non-localhost origin the browser doesn't expose it at all, so
 * `navigator.clipboard.writeText(...)` throws synchronously
 * (TypeError: Cannot read properties of undefined) before any .then/.catch
 * ever runs - which is exactly the deployed environment here, and why the
 * Copy button silently did nothing. Falls back to the legacy
 * document.execCommand('copy') path via a hidden, off-screen textarea.
 */
export async function copyText(text: string): Promise<boolean> {
  if (typeof navigator !== 'undefined' && navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      return false
    }
  }

  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.focus()
  textarea.select()
  try {
    return document.execCommand('copy')
  } catch {
    return false
  } finally {
    document.body.removeChild(textarea)
  }
}
