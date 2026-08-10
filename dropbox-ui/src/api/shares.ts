import { apiFetch } from './client'

export type SharePermission = 'VIEW' | 'DOWNLOAD'
export type ShareStatus = 'ACTIVE' | 'REVOKED'

export interface CreateShareRequest {
  permission: SharePermission
  expiresAt: string | null
}

/** Returned only from creation - the raw token is shown exactly once, only
 * its hash is persisted server-side, so it never appears in listShares. */
export interface ShareCreatedResponse {
  id: string
  fileId: string
  token: string
  permission: string
  status: ShareStatus
  expiresAt: string | null
  createdAt: string
}

export interface ShareResponse {
  id: string
  fileId: string
  permission: string
  status: ShareStatus
  expiresAt: string | null
  createdAt: string
}

export function createShare(fileId: string, request: CreateShareRequest) {
  return apiFetch<ShareCreatedResponse>(`/api/v1/files/${fileId}/shares`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

/** Not paginated server-side - FileShareController returns a plain List. */
export function listShares(fileId: string) {
  return apiFetch<ShareResponse[]>(`/api/v1/files/${fileId}/shares`)
}

export function revokeShare(shareId: string) {
  return apiFetch<void>(`/api/v1/shares/${shareId}`, { method: 'DELETE' })
}
