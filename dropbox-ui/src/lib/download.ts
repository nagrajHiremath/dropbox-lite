import { useAuthStore } from '@/store/authStore'

// Duplicated from client.ts's private constant - same reason
// uploadEngine.ts/publicShares.ts duplicate it: this needs a full URL for a
// raw fetch, not an apiFetch(path) call.
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL

/**
 * FileDownloadController requires a Bearer token, so a plain <img>/<a> can't
 * hit it directly (browsers don't attach custom headers to those). Reads the
 * token straight from the store rather than going through apiFetch - same
 * as uploadEngine's xhrPutPart, this deliberately does NOT get apiFetch's
 * automatic refresh-on-401, only unrelated calls elsewhere refresh it.
 */
export async function fetchFileBlob(fileId: string): Promise<Blob> {
  const { accessToken } = useAuthStore.getState()
  const res = await fetch(`${API_BASE_URL}/api/v1/files/${fileId}/content`, {
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : undefined,
  })
  if (!res.ok) throw new Error('Failed to load file content')
  return res.blob()
}

export async function downloadFile(fileId: string, fileName: string): Promise<void> {
  const blob = await fetchFileBlob(fileId)
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}
