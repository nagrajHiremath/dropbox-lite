/**
 * navigator.clipboard only exists in a secure context (HTTPS, or localhost) -
 * same restriction as crypto.randomUUID (see lib/id.ts). On a plain-HTTP,
 * non-localhost origin the browser doesn't expose it at all, so
 * `navigator.clipboard.writeText(...)` throws synchronously
 * (TypeError: Cannot read properties of undefined) before any .then/.catch
 * ever runs - which is exactly the deployed environment here, and why the
 * Copy button silently did nothing. Falls back to the legacy
 * document.execCommand('copy') path.
 *
 * The fallback needs an existing, visible input/textarea to select from
 * (passed as `sourceEl`) rather than a detached element appended to
 * document.body: every caller here renders inside a Radix Dialog, whose
 * FocusScope traps focus and yanks it straight back into the dialog the
 * instant something outside that DOM subtree calls .focus() - synchronously,
 * before execCommand('copy') runs. execCommand still returns true (no
 * exception), so the caller sees "success" while nothing was actually
 * copied. Selecting from an element already inside the trapped scope avoids
 * that fight entirely. Only fall back to a detached textarea if no such
 * element was given.
 */
export async function copyText(
  text: string,
  sourceEl?: HTMLInputElement | HTMLTextAreaElement,
): Promise<boolean> {
  if (typeof navigator !== 'undefined' && navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      return false
    }
  }

  const el = sourceEl ?? document.createElement('textarea')
  if (!sourceEl) {
    el.value = text
    el.style.position = 'fixed'
    el.style.opacity = '0'
    document.body.appendChild(el)
  }
  el.focus()
  el.select()
  try {
    return document.execCommand('copy')
  } catch {
    return false
  } finally {
    if (!sourceEl) document.body.removeChild(el)
  }
}
