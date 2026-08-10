import { AlertCircleIcon, CheckCircle2Icon, FileIcon, RotateCcwIcon, XIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Progress } from '@/components/ui/progress'
import { formatBytes } from '@/lib/format'
import type { UploadEntry } from '@/store/uploadQueueStore'

interface UploadTrayItemProps {
  entry: UploadEntry
  onRetry: () => void
  onCancel: () => void
  onDismiss: () => void
}

export function UploadTrayItem({ entry, onRetry, onCancel, onDismiss }: UploadTrayItemProps) {
  const isActive = entry.status === 'uploading' || entry.status === 'completing'
  const percent =
    entry.totalBytes > 0
      ? Math.min(100, Math.round((entry.uploadedBytes / entry.totalBytes) * 100))
      : 0

  return (
    <div className="flex items-center gap-3 px-3 py-2">
      <StatusIcon status={entry.status} />

      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">{entry.fileName}</p>
        {isActive && <Progress value={percent} className="mt-1 h-1.5" />}
        {entry.status === 'uploading' && (
          <p className="text-muted-foreground mt-0.5 text-xs">
            {formatBytes(entry.uploadedBytes)} of {formatBytes(entry.totalBytes)}
          </p>
        )}
        {entry.status === 'completing' && (
          <p className="text-muted-foreground mt-0.5 text-xs">Finishing…</p>
        )}
        {entry.status === 'failed' && (
          <p className="text-destructive mt-0.5 text-xs">{entry.error ?? 'Upload failed'}</p>
        )}
        {entry.status === 'aborted' && (
          <p className="text-muted-foreground mt-0.5 text-xs">Canceled</p>
        )}
      </div>

      {isActive && (
        <Button
          variant="ghost"
          size="icon"
          className="size-6 shrink-0"
          onClick={onCancel}
          aria-label="Cancel upload"
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
          aria-label="Retry upload"
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

function StatusIcon({ status }: { status: UploadEntry['status'] }) {
  switch (status) {
    case 'done':
      return <CheckCircle2Icon className="size-5 shrink-0 text-emerald-600" aria-hidden="true" />
    case 'failed':
      return <AlertCircleIcon className="text-destructive size-5 shrink-0" aria-hidden="true" />
    default:
      return <FileIcon className="text-muted-foreground size-5 shrink-0" aria-hidden="true" />
  }
}
