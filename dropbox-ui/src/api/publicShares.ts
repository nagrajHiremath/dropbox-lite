import { apiFetch } from './client'

// Duplicated from client.ts's private constant, same reason uploadEngine.ts
// duplicates it: a full absolute URL is needed here (a plain <a href>, not
// an apiFetch call), not just a path.
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL

export interface PublicShareInfo {
  fileId: string
  fileName: string
  mimeType: string | null
  sizeBytes: number | null
  permission: string
}

/** No Authorization header - this must work for anonymous, logged-out
 * visitors, and auth:false also skips apiFetch's 401-refresh-then-redirect
 * interceptor so a stale/expired token from an unrelated logged-in session
 * never bounces a public-link visitor to /login. */
export function getPublicShare(token: string) {
  return apiFetch<PublicShareInfo>(`/api/v1/public/shares/${token}`, { auth: false })
}

/** download-service streams the content directly with a Content-Disposition:
 * attachment header already set server-side, so a plain anchor href is
 * enough - no fetch/blob handling needed, same as the server-driven
 * filename/mimetype resolution FileDownloadController uses. */
export function publicShareContentUrl(token: string) {
  return `${API_BASE_URL}/api/v1/public/shares/${token}/content`
}
