import {
  AlertCircleIcon,
  CheckCircle2Icon,
  DownloadIcon,
  PauseCircleIcon,
  PauseIcon,
  PlayIcon,
  RotateCcwIcon,
  XIcon,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Progress } from '@/components/ui/progress'
import { formatBytes } from '@/lib/format'
import type { DownloadEntry } from '@/store/downloadQueueStore'

interface DownloadTrayItemProps {
  entry: DownloadEntry
  onRetry: () => void
  onPause: () => void
  onCancel: () => void
  onDismiss: () => void
}

/** Counterpart to UploadTrayItem - same layout/status language, adapted for
 * download's status set (no "completing" upload-finalization step; instead
 * "assembling" while downloadEngine joins the downloaded chunks into one
 * Blob for the browser's save dialog). */
export function DownloadTrayItem({
  entry,
  onRetry,
  onPause,
  onCancel,
  onDismiss,
}: DownloadTrayItemProps) {
  const isActive = entry.status === 'downloading' || entry.status === 'assembling'
  const showProgress = isActive || entry.status === 'paused'
  const canCancel = isActive || entry.status === 'paused'
  const percent =
    entry.totalBytes > 0
      ? Math.min(100, Math.round((entry.downloadedBytes / entry.totalBytes) * 100))
      : 0

  return (
    <div className="flex items-center gap-3 px-3 py-2">
      <StatusIcon status={entry.status} />

      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">{entry.fileName}</p>
        {showProgress && <Progress value={percent} className="mt-1 h-1.5" />}
        {entry.status === 'downloading' && (
          <p className="text-muted-foreground mt-0.5 text-xs">
            {formatBytes(entry.downloadedBytes)} of {formatBytes(entry.totalBytes)}
          </p>
        )}
        {entry.status === 'assembling' && (
          <p className="text-muted-foreground mt-0.5 text-xs">Finishing…</p>
        )}
        {entry.status === 'paused' && (
          <p className="text-muted-foreground mt-0.5 text-xs">
            Paused at {formatBytes(entry.downloadedBytes)} of {formatBytes(entry.totalBytes)}
          </p>
        )}
        {entry.status === 'failed' && (
          <p className="text-destructive mt-0.5 text-xs">{entry.error ?? 'Download failed'}</p>
        )}
        {entry.status === 'aborted' && (
          <p className="text-muted-foreground mt-0.5 text-xs">Canceled</p>
        )}
      </div>

      {entry.status === 'downloading' && (
        <Button
          variant="ghost"
          size="icon"
          className="size-6 shrink-0"
          onClick={onPause}
          aria-label="Pause download"
        >
          <PauseIcon className="size-4" />
        </Button>
      )}
      {entry.status === 'paused' && (
        <Button
          variant="ghost"
          size="icon"
          className="size-6 shrink-0"
          onClick={onRetry}
          aria-label="Resume download"
        >
          <PlayIcon className="size-4" />
        </Button>
      )}
      {canCancel && (
        <Button
          variant="ghost"
          size="icon"
          className="size-6 shrink-0"
          onClick={onCancel}
          aria-label="Cancel download"
        >
          <XIcon className="size-4" />
        </Button>
      )}
      {entry.status === 'failed' && (
        <Button
          variant="ghost"
          size="icon"
          className="size-6 shrink-0"
          onClick={onRetry}
          aria-label="Retry download"
        >
          <RotateCcwIcon className="size-4" />
        </Button>
      )}
      {(entry.status === 'done' || entry.status === 'failed' || entry.status === 'aborted') && (
        <Button
          variant="ghost"
          size="icon"
          className="size-6 shrink-0"
          onClick={onDismiss}
          aria-label="Dismiss"
        >
          <XIcon className="size-4" />
        </Button>
      )}
    </div>
  )
}

function StatusIcon({ status }: { status: DownloadEntry['status'] }) {
  switch (status) {
    case 'done':
      return <CheckCircle2Icon className="size-5 shrink-0 text-emerald-600" aria-hidden="true" />
    case 'failed':
      return <AlertCircleIcon className="text-destructive size-5 shrink-0" aria-hidden="true" />
    case 'paused':
      return (
        <PauseCircleIcon className="text-muted-foreground size-5 shrink-0" aria-hidden="true" />
      )
    default:
      return <DownloadIcon className="text-muted-foreground size-5 shrink-0" aria-hidden="true" />
  }
}
