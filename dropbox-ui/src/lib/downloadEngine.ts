import { useAuthStore } from '@/store/authStore'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL
const CHUNK_SIZE = 8 * 1024 * 1024 // 8 MiB, matches uploadEngine's DEFAULT_CHUNK_SIZE
const MAX_CONCURRENT_CHUNKS = 3
const MAX_CHUNK_ATTEMPTS = 3
const CHUNK_RETRY_BACKOFF_MS = 1000

export interface DownloadFileInfo {
  fileId: string
  fileName: string
  mimeType: string | null
  totalBytes: number
  /** Omit to download the file's current version. */
  versionId?: string
}

export type DownloadEngineStatus =
  'downloading' | 'paused' | 'assembling' | 'done' | 'failed' | 'aborted'

export interface DownloadStatusExtra {
  error?: string
}

export interface DownloadEngineCallbacks {
  onProgress: (downloadedBytes: number) => void
  onStatusChange: (status: DownloadEngineStatus, extra?: DownloadStatusExtra) => void
}

function delay(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : 'Download failed'
}

function contentUrl(file: DownloadFileInfo): string {
  return file.versionId
    ? `${API_BASE_URL}/api/v1/files/${file.fileId}/versions/${file.versionId}/content`
    : `${API_BASE_URL}/api/v1/files/${file.fileId}/content`
}

/**
 * Promise wrapper around XMLHttpRequest for a single Range GET - mirrors
 * uploadEngine's xhrPutPart exactly (fetch cannot report download progress
 * mid-response the way XHR's progress event can), just GET+Range instead of
 * PUT. Only a 206 is accepted - download-service always honors Range (see
 * ByteRangeParser/DownloadResponseBuilder), so anything else (including a
 * plain 200, which would mean something in front of it dropped the Range
 * header) is treated as a failure rather than guessed-at and mis-assembled.
 */
function xhrGetRange(
  url: string,
  start: number,
  end: number,
  onProgress: (loaded: number) => void,
  signal: AbortSignal,
): Promise<Blob> {
  return new Promise((resolve, reject) => {
    if (signal.aborted) {
      reject(new DOMException('Aborted', 'AbortError'))
      return
    }

    const xhr = new XMLHttpRequest()
    xhr.open('GET', url)
    xhr.responseType = 'blob'
    const accessToken = useAuthStore.getState().accessToken
    if (accessToken) xhr.setRequestHeader('Authorization', `Bearer ${accessToken}`)
    xhr.setRequestHeader('Range', `bytes=${start}-${end}`)

    const onAbort = () => xhr.abort()
    const cleanup = () => signal.removeEventListener('abort', onAbort)
    signal.addEventListener('abort', onAbort)

    xhr.onprogress = (e) => {
      if (e.lengthComputable) onProgress(e.loaded)
    }
    xhr.onload = () => {
      cleanup()
      if (xhr.status === 206) {
        resolve(xhr.response as Blob)
      } else {
        reject(new Error(`Chunk download failed with status ${xhr.status}`))
      }
    }
    xhr.onerror = () => {
      cleanup()
      reject(new Error('Network error during chunk download'))
    }
    xhr.onabort = () => {
      cleanup()
      reject(new DOMException('Aborted', 'AbortError'))
    }

    xhr.send()
  })
}

function saveBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

/**
 * Owns one download attempt's full lifecycle - counterpart to uploadEngine's
 * UploadEngine, but simpler: a Range GET is stateless, so there's no
 * server-side session to create/query/complete/abort the way uploads have.
 * "Resume" is just re-requesting whichever chunk ranges this engine's own
 * in-memory bookkeeping doesn't have yet - this engine instance IS the only
 * source of truth for what's already been downloaded, which also means (like
 * uploadEngine) pause/resume survives navigation within the app but not a
 * page reload or tab close.
 */
export class DownloadEngine {
  private readonly file: DownloadFileInfo
  private readonly callbacks: DownloadEngineCallbacks
  private readonly url: string
  private readonly totalParts: number

  private readonly completedPartBytes = new Map<number, number>()
  private readonly inFlightPartBytes = new Map<number, number>()
  private readonly completedParts = new Map<number, Blob>()
  private abortController = new AbortController()
  private aborted = false
  private paused = false

  constructor(file: DownloadFileInfo, callbacks: DownloadEngineCallbacks) {
    this.file = file
    this.callbacks = callbacks
    this.url = contentUrl(file)
    this.totalParts = file.totalBytes === 0 ? 0 : Math.ceil(file.totalBytes / CHUNK_SIZE)
  }

  async start(): Promise<void> {
    try {
      this.callbacks.onStatusChange('downloading')
      await this.runQueue(range(1, this.totalParts))
      if (this.aborted || this.paused) return
      await this.finish()
    } catch (err) {
      if (!this.aborted && !this.paused) {
        this.callbacks.onStatusChange('failed', { error: errorMessage(err) })
      }
    }
  }

  /** Resume a paused download. No server round-trip needed first (unlike
   * uploadEngine.retry()'s getUploadStatus call) - see class-level note. */
  async resume(): Promise<void> {
    this.aborted = false
    this.paused = false
    this.abortController = new AbortController()

    try {
      this.callbacks.onStatusChange('downloading')
      const remaining = range(1, this.totalParts).filter((p) => !this.completedParts.has(p))
      await this.runQueue(remaining)
      if (this.aborted || this.paused) return
      await this.finish()
    } catch (err) {
      if (!this.aborted && !this.paused) {
        this.callbacks.onStatusChange('failed', { error: errorMessage(err) })
      }
    }
  }

  /** Stops in-flight chunk requests without discarding already-downloaded
   * chunks, so resume() can continue from where this left off. */
  pause(): void {
    if (this.paused || this.aborted) return
    this.paused = true
    this.abortController.abort()
    this.callbacks.onStatusChange('paused')
  }

  abort(): void {
    this.aborted = true
    this.paused = false
    this.abortController.abort()
    this.completedParts.clear()
    this.callbacks.onStatusChange('aborted')
  }

  private async finish(): Promise<void> {
    this.callbacks.onStatusChange('assembling')
    const orderedBlobs = range(1, this.totalParts).map((p) => this.completedParts.get(p)!)
    const fullBlob = new Blob(orderedBlobs, {
      type: this.file.mimeType || 'application/octet-stream',
    })
    saveBlob(fullBlob, this.file.fileName)
    this.callbacks.onStatusChange('done')
  }

  /** Bounded-concurrency pump - identical shape to uploadEngine's runQueue. */
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
        while (active < MAX_CONCURRENT_CHUNKS && pending.length > 0) {
          const partNumber = pending.shift()!
          active++
          this.downloadPartWithRetry(partNumber)
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

  private async downloadPartWithRetry(partNumber: number, attempt = 1): Promise<void> {
    const { start, end } = this.rangeForPart(partNumber)
    try {
      const blob = await xhrGetRange(
        this.url,
        start,
        end,
        (loaded) => {
          this.inFlightPartBytes.set(partNumber, loaded)
          this.reportProgress()
        },
        this.abortController.signal,
      )
      this.inFlightPartBytes.delete(partNumber)
      this.completedParts.set(partNumber, blob)
      this.completedPartBytes.set(partNumber, blob.size)
      this.reportProgress()
    } catch (err) {
      this.inFlightPartBytes.delete(partNumber)
      if (this.aborted || this.paused) throw err
      if (attempt < MAX_CHUNK_ATTEMPTS) {
        await delay(attempt * CHUNK_RETRY_BACKOFF_MS)
        return this.downloadPartWithRetry(partNumber, attempt + 1)
      }
      throw err
    }
  }

  private reportProgress(): void {
    let downloaded = 0
    this.completedPartBytes.forEach((bytes) => (downloaded += bytes))
    this.inFlightPartBytes.forEach((bytes) => (downloaded += bytes))
    this.callbacks.onProgress(downloaded)
  }

  private rangeForPart(partNumber: number): { start: number; end: number } {
    const start = (partNumber - 1) * CHUNK_SIZE
    const end = Math.min(start + CHUNK_SIZE, this.file.totalBytes) - 1
    return { start, end }
  }
}

function range(start: number, endInclusive: number): number[] {
  const result: number[] = []
  for (let i = start; i <= endInclusive; i++) result.push(i)
  return result
}
