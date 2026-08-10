import { useState } from 'react'
import { ChevronDownIcon, ChevronUpIcon, Loader2Icon, XIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useUploadQueueStore, type UploadEntry } from '@/store/uploadQueueStore'
import { useDownloadQueueStore, type DownloadEntry } from '@/store/downloadQueueStore'
import { UploadTrayItem } from '@/components/upload/UploadTrayItem'
import { DownloadTrayItem } from './DownloadTrayItem'

type TransferItem =
  { kind: 'upload'; entry: UploadEntry } | { kind: 'download'; entry: DownloadEntry }

/**
 * Unified upload+download activity tray - mounted once in AppShell, same
 * "stays visible while browsing" rationale as the upload-only tray it
 * replaced. Reads both queue stores (each still owns its own engines/state
 * independently - see uploadQueueStore/downloadQueueStore) and merges their
 * entries into one list, newest first, via createdAt (trackingId is a UUID,
 * not chronological, so it can't be the sort key here).
 */
export function TransferTray() {
  const uploadEntries = useUploadQueueStore((s) => s.entries)
  const folderPrepMessage = useUploadQueueStore((s) => s.folderPrepMessage)
  const retryUpload = useUploadQueueStore((s) => s.retry)
  const pauseUpload = useUploadQueueStore((s) => s.pause)
  const abortUpload = useUploadQueueStore((s) => s.abort)
  const dismissUpload = useUploadQueueStore((s) => s.dismiss)

  const downloadEntries = useDownloadQueueStore((s) => s.entries)
  const retryDownload = useDownloadQueueStore((s) => s.retry)
  const pauseDownload = useDownloadQueueStore((s) => s.pause)
  const abortDownload = useDownloadQueueStore((s) => s.abort)
  const dismissDownload = useDownloadQueueStore((s) => s.dismiss)

  const [collapsed, setCollapsed] = useState(false)

  const uploadItems = Object.values(uploadEntries)
  const downloadItems = Object.values(downloadEntries)
  const items: TransferItem[] = [
    ...uploadItems.map((entry): TransferItem => ({ kind: 'upload', entry })),
    ...downloadItems.map((entry): TransferItem => ({ kind: 'download', entry })),
  ].sort((a, b) => b.entry.createdAt - a.entry.createdAt)

  const isPreparing = items.length === 0 && folderPrepMessage !== null
  if (items.length === 0 && !isPreparing) return null

  const uploadActive = uploadItems.filter(
    (e) => e.status === 'uploading' || e.status === 'completing',
  ).length
  const downloadActive = downloadItems.filter(
    (e) => e.status === 'downloading' || e.status === 'assembling',
  ).length
  const activeCount = uploadActive + downloadActive
  const failedCount =
    uploadItems.filter((e) => e.status === 'failed').length +
    downloadItems.filter((e) => e.status === 'failed').length

  const summary = isPreparing
    ? folderPrepMessage
    : activeCount > 0
      ? uploadActive > 0 && downloadActive > 0
        ? `Transferring ${activeCount} items…`
        : uploadActive > 0
          ? `Uploading ${uploadActive} file${uploadActive === 1 ? '' : 's'}…`
          : `Downloading ${downloadActive} file${downloadActive === 1 ? '' : 's'}…`
      : failedCount > 0
        ? `${failedCount} transfer${failedCount === 1 ? '' : 's'} failed`
        : `${items.length} transfer${items.length === 1 ? '' : 's'} complete`

  function dismissAll() {
    uploadItems.forEach((entry) => {
      if (entry.status !== 'uploading' && entry.status !== 'completing') {
        dismissUpload(entry.trackingId)
      }
    })
    downloadItems.forEach((entry) => {
      if (entry.status !== 'downloading' && entry.status !== 'assembling') {
        dismissDownload(entry.trackingId)
      }
    })
  }

  return (
    <div className="bg-popover fixed right-4 bottom-4 z-50 w-80 rounded-lg border shadow-lg">
      <div className="flex items-center gap-2 border-b px-3 py-2">
        {isPreparing && (
          <Loader2Icon
            className="text-muted-foreground size-4 shrink-0 animate-spin"
            aria-hidden="true"
          />
        )}
        <span className="flex-1 truncate text-sm font-medium">{summary}</span>
        {!isPreparing && (
          <>
            <Button
              variant="ghost"
              size="icon"
              className="size-6"
              onClick={() => setCollapsed((c) => !c)}
              aria-label={collapsed ? 'Expand transfer tray' : 'Collapse transfer tray'}
            >
              {collapsed ? (
                <ChevronUpIcon className="size-4" />
              ) : (
                <ChevronDownIcon className="size-4" />
              )}
            </Button>
            <Button
              variant="ghost"
              size="icon"
              className="size-6"
              onClick={dismissAll}
              aria-label="Clear finished transfers"
            >
              <XIcon className="size-4" />
            </Button>
          </>
        )}
      </div>

      {!collapsed && !isPreparing && (
        <div className="max-h-80 divide-y overflow-y-auto">
          {items.map((item) =>
            item.kind === 'upload' ? (
              <UploadTrayItem
                key={item.entry.trackingId}
                entry={item.entry}
                onRetry={() => retryUpload(item.entry.trackingId)}
                onPause={() => pauseUpload(item.entry.trackingId)}
                onCancel={() => abortUpload(item.entry.trackingId)}
                onDismiss={() => dismissUpload(item.entry.trackingId)}
              />
            ) : (
              <DownloadTrayItem
                key={item.entry.trackingId}
                entry={item.entry}
                onRetry={() => retryDownload(item.entry.trackingId)}
                onPause={() => pauseDownload(item.entry.trackingId)}
                onCancel={() => abortDownload(item.entry.trackingId)}
                onDismiss={() => dismissDownload(item.entry.trackingId)}
              />
            ),
          )}
        </div>
      )}
    </div>
  )
}
