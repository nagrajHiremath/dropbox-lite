import { useQuery } from '@tanstack/react-query'
import { HistoryIcon, Share2Icon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Separator } from '@/components/ui/separator'
import { listVersions } from '@/api/versions'
import { versionsKey } from '@/hooks/queryKeys'
import { getEntryIcon, type BrowserEntry } from '@/lib/fileIcons'
import { formatBytes } from '@/lib/format'

interface DetailsDialogProps {
  entry: BrowserEntry | null
  onOpenChange: (open: boolean) => void
  onRequestVersionHistory: (entry: BrowserEntry) => void
  onRequestShare: (entry: BrowserEntry) => void
}

function formatDateTime(iso: string) {
  return new Date(iso).toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  })
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4 py-1.5 text-sm">
      <span className="text-muted-foreground">{label}</span>
      <span className="max-w-[70%] truncate text-right font-medium">{value}</span>
    </div>
  )
}

export function DetailsDialog({
  entry,
  onOpenChange,
  onRequestVersionHistory,
  onRequestShare,
}: DetailsDialogProps) {
  const file = entry?.type === 'file' ? entry.file : null

  // Reuses the same versionsKey query VersionHistoryDialog uses - if that
  // dialog already fetched it for this file, this is a cache hit, not a
  // second request. File size isn't on FileResponse itself, only on its
  // current FileVersion.
  const { data: versions } = useQuery({
    queryKey: versionsKey(file?.id ?? ''),
    queryFn: () => listVersions(file!.id),
    enabled: file !== null,
  })
  const currentVersion = versions?.content.find((v) => v.id === file?.currentVersionId)

  function handleOpenChange(next: boolean) {
    if (!next) onOpenChange(false)
  }

  const name = entry ? (entry.type === 'folder' ? entry.folder.name : entry.file.name) : ''
  const createdAt = entry
    ? entry.type === 'folder'
      ? entry.folder.createdAt
      : entry.file.createdAt
    : ''
  const updatedAt = entry
    ? entry.type === 'folder'
      ? entry.folder.updatedAt
      : entry.file.updatedAt
    : ''
  const Icon = entry ? getEntryIcon(entry) : null

  return (
    <Dialog open={entry !== null} onOpenChange={handleOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            {Icon && <Icon className="text-muted-foreground size-5 shrink-0" aria-hidden="true" />}
            <span className="truncate">{name}</span>
          </DialogTitle>
        </DialogHeader>

        <div>
          <Row
            label="Type"
            value={entry?.type === 'folder' ? 'Folder' : (file?.mimeType ?? 'File')}
          />
          {file && (
            <Row
              label="Size"
              value={currentVersion ? formatBytes(currentVersion.sizeBytes) : '—'}
            />
          )}
          {file && (
            <Row label="Version" value={currentVersion ? `${currentVersion.versionNumber}` : '—'} />
          )}
          <Row label="Created" value={createdAt ? formatDateTime(createdAt) : ''} />
          <Row label="Modified" value={updatedAt ? formatDateTime(updatedAt) : ''} />
        </div>

        {file && entry && (
          <>
            <Separator />
            <div className="flex gap-2">
              <Button
                type="button"
                variant="outline"
                className="flex-1"
                onClick={() => onRequestVersionHistory(entry)}
              >
                <HistoryIcon className="size-4" />
                Version history
              </Button>
              <Button
                type="button"
                variant="outline"
                className="flex-1"
                onClick={() => onRequestShare(entry)}
              >
                <Share2Icon className="size-4" />
                Share
              </Button>
            </div>
          </>
        )}

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
            Close
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
