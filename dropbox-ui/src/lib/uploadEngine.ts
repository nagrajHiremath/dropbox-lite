import { useAuthStore } from '@/store/authStore'
import {
  abortUploadSession,
  completeUpload,
  getUploadStatus,
  initiateUpload,
  initiateVersionUpload,
  type UploadStatusResponse,
} from '@/api/uploads'
import { generateId } from '@/lib/id'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL
const MAX_CONCURRENT_PARTS = 3
const MAX_PART_ATTEMPTS = 3
const PART_RETRY_BACKOFF_MS = 1000

export type UploadTarget =
  { kind: 'new-file'; folderId: string | undefined } | { kind: 'new-version'; fileId: string }

export type EngineStatus = 'uploading' | 'completing' | 'done' | 'failed' | 'aborted' | 'paused'

export interface EngineStatusExtra {
  error?: string
  uploadId?: string
  fileId?: string
  versionId?: string
}

export interface EngineCallbacks {
  onProgress: (uploadedBytes: number) => void
  onStatusChange: (status: EngineStatus, extra?: EngineStatusExtra) => void
}

function delay(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : 'Upload failed'
}

/**
 * Promise wrapper around XMLHttpRequest for a single part PUT - fetch cannot
 * report upload progress, XHR's upload.onprogress can. The auth header is
 * read fresh from the store at send time (not cached at engine construction)
 * so a token refreshed by unrelated app activity is still picked up for any
 * part not yet sent - see UI-05 plan's disclosed limitation: this path does
 * NOT get client.ts's automatic refresh-on-401, only unrelated fetch calls
 * refresh it.
 */
function xhrPutPart(
  url: string,
  blob: Blob,
  onProgress: (loaded: number) => void,
  signal: AbortSignal,
): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal.aborted) {
      reject(new DOMException('Aborted', 'AbortError'))
      return
    }

    const xhr = new XMLHttpRequest()
    xhr.open('PUT', url)
    const accessToken = useAuthStore.getState().accessToken
    if (accessToken) xhr.setRequestHeader('Authorization', `Bearer ${accessToken}`)

    const onAbort = () => xhr.abort()
    const cleanup = () => signal.removeEventListener('abort', onAbort)
    signal.addEventListener('abort', onAbort)

    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onProgress(e.loaded)
    }
    xhr.onload = () => {
      cleanup()
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve()
      } else {
        reject(new Error(`Part upload failed with status ${xhr.status}`))
      }
    }
    xhr.onerror = () => {
      cleanup()
      reject(new Error('Network error during part upload'))
    }
    xhr.onabort = () => {
      cleanup()
      reject(new DOMException('Aborted', 'AbortError'))
    }

    xhr.send(blob)
  })
}

/**
 * Owns one upload attempt's full lifecycle. A plain class, not a hook - it
 * outlives whatever component triggered it (survives navigation) via the
 * callbacks the store wires up when constructing it. See UI-05 plan for the
 * full design rationale (bounded concurrency, resumable retry via GET
 * status, cooperative abort, Idempotency-Key reuse).
 */
export class UploadEngine {
  private readonly file: File
  private readonly target: UploadTarget
  private readonly callbacks: EngineCallbacks

  private uploadId: string | undefined
  private chunkSize = 0
  private totalParts = 0
  private readonly idempotencyKey = generateId()

  private readonly completedPartBytes = new Map<number, number>()
  private readonly inFlightPartBytes = new Map<number, number>()
  private abortController = new AbortController()
  private aborted = false
  /** A local-only stop, unlike abort(): no abortUploadSession call, so the
   * server-side session (and its already-uploaded parts) stays intact and
   * `retry()` can pick it back up - resume is just retry() with a fresh
   * AbortController. */
  private paused = false

  constructor(file: File, target: UploadTarget, callbacks: EngineCallbacks) {
    this.file = file
    this.target = target
    this.callbacks = callbacks
  }

  async start(): Promise<void> {
    try {
      this.callbacks.onStatusChange('uploading')
      const init = await this.initiate()
      this.uploadId = init.uploadId
      this.chunkSize = init.chunkSize
      this.totalParts = init.totalParts
      this.callbacks.onStatusChange('uploading', { uploadId: init.uploadId })

      await this.runQueue(range(1, this.totalParts))
      if (this.aborted || this.paused) return
      await this.finish()
    } catch (err) {
      if (!this.aborted && !this.paused) {
        this.callbacks.onStatusChange('failed', { error: errorMessage(err) })
      }
    }
  }

  /** Resumable: re-fetches the server's own record of which parts already
   * landed rather than trusting client-side bookkeeping, which may be stale
   * after a page navigation or a race with an in-flight part. Doubles as
   * resume() for a paused upload - same "trust the server" logic applies. */
  async retry(): Promise<void> {
    if (!this.uploadId) return this.start()

    this.aborted = false
    this.paused = false
    this.abortController = new AbortController()

    try {
      this.callbacks.onStatusChange('uploading')
      const status: UploadStatusResponse = await getUploadStatus(this.uploadId)
      const uploaded = new Set(status.uploadedParts)
      uploaded.forEach((partNumber) =>
        this.completedPartBytes.set(partNumber, this.partSize(partNumber)),
      )

      const remaining = range(1, this.totalParts).filter((p) => !uploaded.has(p))
      await this.runQueue(remaining)
      if (this.aborted || this.paused) return
      await this.finish()
    } catch (err) {
      if (!this.aborted && !this.paused) {
        this.callbacks.onStatusChange('failed', { error: errorMessage(err) })
      }
    }
  }

  /** Stops in-flight part uploads without touching the server-side session -
   * only meaningful once an uploadId exists (initiate already completed). */
  pause(): void {
    if (this.paused || this.aborted || !this.uploadId) return
    this.paused = true
    this.abortController.abort()
    this.callbacks.onStatusChange('paused')
  }

  abort(): void {
    this.aborted = true
    this.paused = false
    this.abortController.abort()
    this.callbacks.onStatusChange('aborted')
    if (this.uploadId) {
      void abortUploadSession(this.uploadId).catch(() => {
        // best-effort - the session will also expire on its own (UPL-08)
      })
    }
  }

  private async finish(): Promise<void> {
    this.callbacks.onStatusChange('completing')
    const result = await completeUpload(this.uploadId!)
    this.callbacks.onStatusChange('done', { fileId: result.fileId, versionId: result.versionId })
  }

  private initiate() {
    if (this.target.kind === 'new-file') {
      return initiateUpload(
        {
          fileName: this.file.name,
          folderId: this.target.folderId,
          size: this.file.size,
          mimeType: this.file.type || undefined,
        },
        this.idempotencyKey,
      )
    }
    return initiateVersionUpload(
      this.target.fileId,
      { size: this.file.size, mimeType: this.file.type || undefined },
      this.idempotencyKey,
    )
  }

  /** Bounded-concurrency pump: refills up to MAX_CONCURRENT_PARTS every time
   * a part settles, until the queue drains or one part exhausts its retries. */
  private runQueue(partNumbers: number[]): Promise<void> {
    return new Promise((resolve, reject) => {
      const pending = [...partNumbers]
      let active = 0
      let settled = false

      const next = () => {
        if (settled) return
        if (this.aborted || this.paused) {
          settled = true
          resolve()
          return
        }
        if (pending.length === 0 && active === 0) {
          settled = true
          resolve()
          return
        }
        while (active < MAX_CONCURRENT_PARTS && pending.length > 0) {
          const partNumber = pending.shift()!
          active++
          this.uploadPartWithRetry(partNumber)
            .then(() => {
              active--
              next()
            })
            .catch((err: unknown) => {
              active--
              if (this.aborted || this.paused) {
                next() // treat as a clean stop, not a failure
                return
              }
              if (!settled) {
                settled = true
                reject(err)
              }
            })
        }
      }

      next()
    })
  }

  private async uploadPartWithRetry(partNumber: number, attempt = 1): Promise<void> {
    const blob = this.sliceForPart(partNumber)
    const url = `${API_BASE_URL}/api/v1/uploads/${this.uploadId}/parts/${partNumber}`
    try {
      await xhrPutPart(
        url,
        blob,
        (loaded) => {
          this.inFlightPartBytes.set(partNumber, loaded)
          this.reportProgress()
        },
        this.abortController.signal,
      )
      this.inFlightPartBytes.delete(partNumber)
      this.completedPartBytes.set(partNumber, blob.size)
      this.reportProgress()
    } catch (err) {
      this.inFlightPartBytes.delete(partNumber)
      if (this.aborted || this.paused) throw err
      if (attempt < MAX_PART_ATTEMPTS) {
        await delay(attempt * PART_RETRY_BACKOFF_MS)
        return this.uploadPartWithRetry(partNumber, attempt + 1)
      }
      throw err
    }
  }

  private reportProgress(): void {
    let uploaded = 0
    this.completedPartBytes.forEach((bytes) => (uploaded += bytes))
    this.inFlightPartBytes.forEach((bytes) => (uploaded += bytes))
    this.callbacks.onProgress(uploaded)
  }

  private sliceForPart(partNumber: number): Blob {
    const start = (partNumber - 1) * this.chunkSize
    const end = Math.min(start + this.chunkSize, this.file.size)
    return this.file.slice(start, end)
  }

  private partSize(partNumber: number): number {
    return partNumber === this.totalParts
      ? this.file.size - this.chunkSize * (this.totalParts - 1)
      : this.chunkSize
  }
}

function range(start: number, endInclusive: number): number[] {
  const result: number[] = []
  for (let i = start; i <= endInclusive; i++) result.push(i)
  return result
}
