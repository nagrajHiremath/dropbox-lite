import { apiFetch } from './client'

export interface StorageUsageResponse {
  usedBytes: number
  maxBytes: number
}

export function getStorageUsage() {
  return apiFetch<StorageUsageResponse>('/api/v1/users/me/storage')
}
