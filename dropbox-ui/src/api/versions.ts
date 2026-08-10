import { apiFetch } from './client'
import type { PageResponse } from './folders'

export interface FileVersion {
  id: string
  fileId: string
  versionNumber: number
  sizeBytes: number
  checksum: string
  etag: string
  createdBy: string
  createdAt: string
}

export function listVersions(fileId: string, page = 0, size = 20) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  return apiFetch<PageResponse<FileVersion>>(`/api/v1/files/${fileId}/versions?${params}`)
}

export function restoreVersion(fileId: string, versionId: string) {
  return apiFetch<FileVersion>(`/api/v1/files/${fileId}/versions/${versionId}/restore`, {
    method: 'POST',
  })
}
