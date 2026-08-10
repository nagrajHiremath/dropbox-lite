import { apiFetch } from './client'
import type { PageResponse } from './folders'

export interface FileEntry {
  id: string
  ownerId: string
  folderId: string | null
  name: string
  mimeType: string | null
  currentVersionId: string | null
  /** Current version's size, resolved server-side in one batched query per
   * page (not per-file) - see metadata-service's FileService.currentSizesFor.
   * null if the file has no current version yet. */
  sizeBytes: number | null
  status: string
  createdAt: string
  updatedAt: string
}

export function getFile(id: string) {
  return apiFetch<FileEntry>(`/api/v1/files/${id}`)
}

/** folderId omitted lists root-level files. */
export function listFiles(folderId: string | undefined, page = 0, size = 100) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (folderId) params.set('folderId', folderId)
  return apiFetch<PageResponse<FileEntry>>(`/api/v1/files?${params}`)
}

/** UI-13: account-wide, not folder-scoped - FileService.listFiles treats
 * view=recent the same way it treats type=image/video below, sorted by
 * updatedAt descending server-side. */
export function listRecentFiles(page = 0, size = 100) {
  const params = new URLSearchParams({ view: 'recent', page: String(page), size: String(size) })
  return apiFetch<PageResponse<FileEntry>>(`/api/v1/files?${params}`)
}

/** UI-13: account-wide mimeType-prefix filter (only "image"/"video" are
 * accepted server-side - FileService.ALLOWED_TYPES). */
export function listFilesByType(type: 'image' | 'video', page = 0, size = 100) {
  const params = new URLSearchParams({ type, page: String(page), size: String(size) })
  return apiFetch<PageResponse<FileEntry>>(`/api/v1/files?${params}`)
}

/** UI-14: account-wide trashed-files listing (status=trashed ignores
 * folderId entirely, same as view=recent/type= above). */
export function listTrashedFiles(page = 0, size = 100) {
  const params = new URLSearchParams({ status: 'trashed', page: String(page), size: String(size) })
  return apiFetch<PageResponse<FileEntry>>(`/api/v1/files?${params}`)
}

export function renameFile(id: string, name: string) {
  return apiFetch<FileEntry>(`/api/v1/files/${id}`, {
    method: 'PATCH',
    body: JSON.stringify({ name }),
  })
}

/** Unlike folders, files genuinely support moving to root - pass null for folderId. */
export function moveFile(id: string, folderId: string | null) {
  return apiFetch<FileEntry>(`/api/v1/files/${id}/move`, {
    method: 'POST',
    body: JSON.stringify({ folderId }),
  })
}

export function trashFile(id: string) {
  return apiFetch<void>(`/api/v1/files/${id}`, { method: 'DELETE' })
}

export function restoreFile(id: string) {
  return apiFetch<FileEntry>(`/api/v1/files/${id}/restore`, { method: 'POST' })
}

export function permanentlyDeleteFile(id: string) {
  return apiFetch<void>(`/api/v1/files/${id}/permanent`, { method: 'DELETE' })
}
