import { create } from 'zustand'
import { UploadEngine, type EngineStatus, type UploadTarget } from '@/lib/uploadEngine'
import { queryClient } from '@/lib/queryClient'
import { versionsKey, storageUsageKey } from '@/hooks/queryKeys'
import { generateId } from '@/lib/id'

export interface UploadEntry {
  trackingId: string
  fileName: string
  totalBytes: number
  uploadedBytes: number
  status: EngineStatus
  error?: string
  uploadId?: string
  resultFileId?: string
  resultVersionId?: string
  /** Merge-sort key for TransferTray's combined upload+download list -
   * trackingId (a UUID) isn't chronological, so this is what "most recent
   * first" actually sorts on. */
  createdAt: number
}

interface UploadQueueState {
  entries: Record<string, UploadEntry>
  /** Transient status text for the folder-creation phase of a dropped-folder
   * upload (see MyFilesPage's handleDroppedEntries) - there's no per-file
   * entry yet at that point, so it can't be represented as one. Null once
   * folder creation is done/never started; UploadTray shows this in place of
   * its usual per-file summary while it's set. */
  folderPrepMessage: string | null
  setFolderPrepMessage: (message: string | null) => void
  enqueue: (file: File, target: UploadTarget) => string
  retry: (trackingId: string) => void
  pause: (trackingId: string) => void
  abort: (trackingId: string) => void
  dismiss: (trackingId: string) => void
}

/** Engine instances are imperative/stateful (XHRs, AbortControllers) and
 * don't belong in React-observed state - kept in a side registry the store's
 * actions look up by trackingId, separate from the plain display state in
 * `entries` that UI-06's tray renders. See UI-05 plan §2. */
const engines = new Map<string, UploadEngine>()

export const useUploadQueueStore = create<UploadQueueState>()((set) => ({
  entries: {},
  folderPrepMessage: null,
  setFolderPrepMessage: (message) => set({ folderPrepMessage: message }),

  enqueue: (file, target) => {
    const trackingId = generateId()

    set((state) => ({
      entries: {
        ...state.entries,
        [trackingId]: {
          trackingId,
          fileName: file.name,
          totalBytes: file.size,
          uploadedBytes: 0,
          status: 'uploading',
          createdAt: Date.now(),
        },
      },
    }))

    const engine = new UploadEngine(file, target, {
      onProgress: (uploadedBytes) => {
        set((state) => {
          const existing = state.entries[trackingId]
          if (!existing) return state
          return { entries: { ...state.entries, [trackingId]: { ...existing, uploadedBytes } } }
        })
      },
      onStatusChange: (status: EngineStatus, extra) => {
        set((state) => {
          const existing = state.entries[trackingId]
          if (!existing) return state
          return {
            entries: {
              ...state.entries,
              [trackingId]: {
                ...existing,
                status,
                // A transition back to "uploading" (fresh start or retry) clears any
                // previous failure; otherwise preserve unless a new one is provided.
                error: status === 'uploading' ? undefined : (extra?.error ?? existing.error),
                uploadId: extra?.uploadId ?? existing.uploadId,
                resultFileId: extra?.fileId ?? existing.resultFileId,
                resultVersionId: extra?.versionId ?? existing.resultVersionId,
              },
            },
          }
        })

        // A completed upload (new file or new version) doesn't otherwise
        // notify TanStack Query - without this, the file list / version
        // history stays stale until some unrelated refetch happens. Broad
        // prefix invalidation (not the exact folder key) matches the
        // precedent set for breadcrumb invalidation in UI-04: simpler and
        // safer than trying to track which folder a version-upload's parent
        // file lives in.
        if (status === 'done') {
          void queryClient.invalidateQueries({ queryKey: ['files'] })
          if (target.kind === 'new-version') {
            void queryClient.invalidateQueries({ queryKey: versionsKey(target.fileId) })
          }
          // Doesn't make the number correct immediately - account-service only
          // learns about this upload once its Kafka consumer processes the
          // FILE_CREATED/FILE_VERSION_CREATED event (outbox publisher polls
          // every ~2s), so the very next fetch can still return the pre-upload
          // value. StorageUsageSection's own refetchInterval is what catches
          // it up a few seconds later; this just gets the ball rolling
          // immediately instead of waiting for the next window-focus refetch.
          void queryClient.invalidateQueries({ queryKey: storageUsageKey })
        }
      },
    })
    engines.set(trackingId, engine)
    void engine.start()

    return trackingId
  },

  retry: (trackingId) => {
    void engines.get(trackingId)?.retry()
  },

  pause: (trackingId) => {
    engines.get(trackingId)?.pause()
  },

  abort: (trackingId) => {
    engines.get(trackingId)?.abort()
  },

  /** Removes the store entry only - never calls the abort/delete endpoint.
   * For an already-done upload there's nothing to abort; for an aborted or
   * fully-failed one the session is already inert (aborted) or will expire
   * on its own (UPL-08), so dismiss is purely a client-side "stop showing
   * this in the tray," not an API call. */
  dismiss: (trackingId) => {
    engines.delete(trackingId)
    set((state) => {
      const next = { ...state.entries }
      delete next[trackingId]
      return { entries: next }
    })
  },
}))
