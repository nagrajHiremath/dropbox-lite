import { create } from 'zustand'
import {
  DownloadEngine,
  type DownloadEngineStatus,
  type DownloadFileInfo,
} from '@/lib/downloadEngine'
import { generateId } from '@/lib/id'

export interface DownloadEntry {
  trackingId: string
  fileName: string
  totalBytes: number
  downloadedBytes: number
  status: DownloadEngineStatus
  error?: string
  /** Merge-sort key for TransferTray's combined upload+download list -
   * trackingId (a UUID) isn't chronological, so this is what "most recent
   * first" actually sorts on. */
  createdAt: number
}

interface DownloadQueueState {
  entries: Record<string, DownloadEntry>
  enqueue: (file: DownloadFileInfo) => string
  retry: (trackingId: string) => void
  pause: (trackingId: string) => void
  abort: (trackingId: string) => void
  dismiss: (trackingId: string) => void
}

/** Same imperative-instance-in-a-side-registry split as uploadQueueStore -
 * see its own comment for the full rationale. */
const engines = new Map<string, DownloadEngine>()

export const useDownloadQueueStore = create<DownloadQueueState>()((set) => ({
  entries: {},

  enqueue: (file) => {
    const trackingId = generateId()

    set((state) => ({
      entries: {
        ...state.entries,
        [trackingId]: {
          trackingId,
          fileName: file.fileName,
          totalBytes: file.totalBytes,
          downloadedBytes: 0,
          status: 'downloading',
          createdAt: Date.now(),
        },
      },
    }))

    const engine = new DownloadEngine(file, {
      onProgress: (downloadedBytes) => {
        set((state) => {
          const existing = state.entries[trackingId]
          if (!existing) return state
          return { entries: { ...state.entries, [trackingId]: { ...existing, downloadedBytes } } }
        })
      },
      onStatusChange: (status, extra) => {
        set((state) => {
          const existing = state.entries[trackingId]
          if (!existing) return state
          return {
            entries: {
              ...state.entries,
              [trackingId]: {
                ...existing,
                status,
                error: status === 'downloading' ? undefined : (extra?.error ?? existing.error),
              },
            },
          }
        })
      },
    })
    engines.set(trackingId, engine)
    void engine.start()

    return trackingId
  },

  retry: (trackingId) => {
    void engines.get(trackingId)?.resume()
  },

  pause: (trackingId) => {
    engines.get(trackingId)?.pause()
  },

  abort: (trackingId) => {
    engines.get(trackingId)?.abort()
  },

  /** Same "store entry only" semantics as uploadQueueStore.dismiss - no
   * abort/delete endpoint exists for a download to begin with. */
  dismiss: (trackingId) => {
    engines.delete(trackingId)
    set((state) => {
      const next = { ...state.entries }
      delete next[trackingId]
      return { entries: next }
    })
  },
}))
