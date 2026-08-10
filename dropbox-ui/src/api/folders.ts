import { apiFetch } from './client'

export interface Folder {
  id: string
  ownerId: string
  parentId: string | null
  name: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export function getFolder(id: string) {
  return apiFetch<Folder>(`/api/v1/folders/${id}`)
}

/** parentId omitted lists root-level folders. */
export function listFolders(parentId: string | undefined, page = 0, size = 100) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (parentId) params.set('parentId', parentId)
  return apiFetch<PageResponse<Folder>>(`/api/v1/folders?${params}`)
}

export function createFolder(name: string, parentId: string | undefined) {
  return apiFetch<Folder>('/api/v1/folders', {
    method: 'POST',
    body: JSON.stringify({ name, parentId: parentId ?? null }),
  })
}

export function renameFolder(id: string, name: string) {
  return apiFetch<Folder>(`/api/v1/folders/${id}`, {
    method: 'PATCH',
    body: JSON.stringify({ name }),
  })
}

/**
 * No root option: UpdateFolderRequest's null parentId means "leave
 * unchanged," not "move to root" - there is no way to move a folder to root
 * in this MVP (see UpdateFolderRequest's javadoc). Callers must pass a real
 * folder id.
 */
export function moveFolder(id: string, parentId: string) {
  return apiFetch<Folder>(`/api/v1/folders/${id}`, {
    method: 'PATCH',
    body: JSON.stringify({ parentId }),
  })
}

export function trashFolder(id: string) {
  return apiFetch<void>(`/api/v1/folders/${id}`, { method: 'DELETE' })
}

export function restoreFolder(id: string) {
  return apiFetch<Folder>(`/api/v1/folders/${id}/restore`, { method: 'POST' })
}

export function permanentlyDeleteFolder(id: string) {
  return apiFetch<void>(`/api/v1/folders/${id}/permanent`, { method: 'DELETE' })
}

/** UI-14: account-wide trashed-folders listing (status=trashed ignores
 * parentId entirely - mirrors GET /files?status=trashed). */
export function listTrashedFolders(page = 0, size = 100) {
  const params = new URLSearchParams({ status: 'trashed', page: String(page), size: String(size) })
  return apiFetch<PageResponse<Folder>>(`/api/v1/folders?${params}`)
}
