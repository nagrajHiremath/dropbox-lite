import { apiFetch } from './client'

export interface InitiateUploadRequest {
  fileName: string
  folderId: string | undefined
  size: number
  mimeType: string | undefined
}

export interface InitiateVersionUploadRequest {
  size: number
  mimeType: string | undefined
}

export interface InitiateUploadResponse {
  uploadId: string
  chunkSize: number
  totalParts: number
  status: string
}

export interface UploadStatusResponse {
  uploadId: string
  status: string
  totalParts: number
  uploadedParts: number[]
}

export interface CompleteUploadResponse {
  uploadId: string
  status: string
  fileId: string
  versionId: string
}

function idempotencyHeaders(idempotencyKey: string | undefined): HeadersInit | undefined {
  return idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined
}

export function initiateUpload(request: InitiateUploadRequest, idempotencyKey?: string) {
  return apiFetch<InitiateUploadResponse>('/api/v1/uploads', {
    method: 'POST',
    body: JSON.stringify(request),
    headers: idempotencyHeaders(idempotencyKey),
  })
}

export function initiateVersionUpload(
  fileId: string,
  request: InitiateVersionUploadRequest,
  idempotencyKey?: string,
) {
  return apiFetch<InitiateUploadResponse>(`/api/v1/files/${fileId}/uploads`, {
    method: 'POST',
    body: JSON.stringify(request),
    headers: idempotencyHeaders(idempotencyKey),
  })
}

export function getUploadStatus(uploadId: string) {
  return apiFetch<UploadStatusResponse>(`/api/v1/uploads/${uploadId}`)
}

export function completeUpload(uploadId: string) {
  return apiFetch<CompleteUploadResponse>(`/api/v1/uploads/${uploadId}/complete`, {
    method: 'POST',
  })
}

export function abortUploadSession(uploadId: string) {
  return apiFetch<void>(`/api/v1/uploads/${uploadId}`, { method: 'DELETE' })
}
