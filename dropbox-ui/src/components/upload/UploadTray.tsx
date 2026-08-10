import { useState } from 'react'
import { ChevronDownIcon, ChevronUpIcon, Loader2Icon, XIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useUploadQueueStore } from '@/store/uploadQueueStore'
import { UploadTrayItem } from './UploadTrayItem'

/**
 * Mounted once in AppShell (not per-page) so it stays visible while browsing,
 * per DROPBOX_UI.md's "Upload panel should remain accessible while browsing."
 * Purely a view over uploadQueueStore - UploadEngine instances already
 * survive navigation on their own (UI-05), this just renders their state.
 *
 * A dropped-folder upload has a folder-creation phase before any file
 * entries exist to show (see MyFilesPage's handleDroppedEntries) - while
 * folderPrepMessage is set, the tray shows that status text instead of its
 * usual per-file summary/list, then transitions to the normal view the
 * moment the first file entry is enqueued and folderPrepMessage clears.
 */
export function UploadTray() {
  const entries = useUploadQueueStore((s) => s.entries)
  const folderPrepMessage = useUploadQueueStore((s) => s.folderPrepMessage)
  const retry = useUploadQueueStore((s) => s.retry)
  const abort = useUploadQueueStore((s) => s.abort)
  const dismiss = useUploadQueueStore((s) => s.dismiss)
  const [collapsed, setCollapsed] = useState(false)

  const items = Object.values(entries).sort((a, b) => a.trackingId.localeCompare(b.trackingId))
  const isPreparing = items.length === 0 && folderPrepMessage !== null
  if (items.length === 0 && !isPreparing) return null

  const activeCount = items.filter(
    (e) => e.status === 'uploading' || e.status === 'completing',
  ).length
  const failedCount = items.filter((e) => e.status === 'failed').length

  const summary = isPreparing
    ? folderPrepMessage
    : activeCount > 0
      ? `Uploading ${activeCount} file${activeCount === 1 ? '' : 's'}…`
      : failedCount > 0
        ? `${failedCount} upload${failedCount === 1 ? '' : 's'} failed`
        : `${items.length} upload${items.length === 1 ? '' : 's'} complete`

  function dismissAll() {
    items.forEach((entry) => {
      if (entry.status !== 'uploading' && entry.status !== 'completing') dismiss(entry.trackingId)
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
              aria-label={collapsed ? 'Expand upload tray' : 'Collapse upload tray'}
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
              aria-label="Clear finished uploads"
            >
              <XIcon className="size-4" />
            </Button>
          </>
        )}
      </div>

      {!collapsed && !isPreparing && (
        <div className="max-h-80 divide-y overflow-y-auto">
          {items.map((entry) => (
            <UploadTrayItem
              key={entry.trackingId}
              entry={entry}
              onRetry={() => retry(entry.trackingId)}
              onCancel={() => abort(entry.trackingId)}
              onDismiss={() => dismiss(entry.trackingId)}
            />
          ))}
        </div>
      )}
    </div>
  )
}
