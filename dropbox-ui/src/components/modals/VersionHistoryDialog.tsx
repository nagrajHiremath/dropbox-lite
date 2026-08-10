import { useRef, type ChangeEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { HistoryIcon, RotateCcwIcon, UploadIcon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { ScrollArea } from '@/components/ui/scroll-area'
import { listVersions } from '@/api/versions'
import { ApiError } from '@/api/client'
import { versionsKey } from '@/hooks/queryKeys'
import { useRestoreVersion } from '@/hooks/useVersionMutations'
import { useUploadQueueStore } from '@/store/uploadQueueStore'
import { formatBytes } from '@/lib/format'
import type { FileEntry } from '@/api/files'

interface VersionHistoryDialogProps {
  file: FileEntry | null
  onOpenChange: (open: boolean) => void
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

function errorMessage(err: unknown) {
  return err instanceof ApiError ? err.message : 'Something went wrong. Please try again.'
}

export function VersionHistoryDialog({ file, onOpenChange }: VersionHistoryDialogProps) {
  const fileInputRef = useRef<HTMLInputElement>(null)
  const enqueueUpload = useUploadQueueStore((s) => s.enqueue)
  const restoreVersionMutation = useRestoreVersion()

  const { data, isLoading, isError } = useQuery({
    queryKey: versionsKey(file?.id ?? ''),
    queryFn: () => listVersions(file!.id),
    enabled: file !== null,
  })

  const versions = [...(data?.content ?? [])].sort((a, b) => b.versionNumber - a.versionNumber)

  function handleOpenChange(next: boolean) {
    if (!next) onOpenChange(false)
  }

  function handleRestore(versionId: string) {
    if (!file) return
    restoreVersionMutation.mutate(
      { fileId: file.id, versionId, folderId: file.folderId ?? undefined },
      {
        onSuccess: () => toast.success('Version restored'),
        onError: (err) => toast.error(errorMessage(err)),
      },
    )
  }

  function handleNewVersionChange(e: ChangeEvent<HTMLInputElement>) {
    const selected = e.target.files?.[0]
    e.target.value = ''
    if (!selected || !file) return

    // Only enforce when both sides have a known mimeType - an empty
    // selected.type means the browser couldn't detect it (common for some
    // extensions), and blocking on an unknown value would reject valid
    // same-type files more often than it'd catch mismatches.
    if (file.mimeType && selected.type && selected.type !== file.mimeType) {
      toast.error(`Select a ${file.mimeType} file to match the current version.`)
      return
    }

    enqueueUpload(selected, { kind: 'new-version', fileId: file.id })
    onOpenChange(false)
  }

  return (
    <Dialog open={file !== null} onOpenChange={handleOpenChange}>
      <DialogContent>
        <input
          ref={fileInputRef}
          type="file"
          className="hidden"
          accept={file?.mimeType ?? undefined}
          onChange={handleNewVersionChange}
        />

        <DialogHeader>
          <DialogTitle>Version history</DialogTitle>
          <DialogDescription>{file?.name}</DialogDescription>
        </DialogHeader>

        <ScrollArea className="h-72 rounded-md border">
          {isLoading ? (
            <p className="text-muted-foreground p-4 text-sm">Loading…</p>
          ) : isError ? (
            <p className="text-destructive p-4 text-sm">Couldn't load version history.</p>
          ) : versions.length === 0 ? (
            <p className="text-muted-foreground p-4 text-sm">No versions yet.</p>
          ) : (
            <div className="divide-y">
              {versions.map((version) => {
                const isCurrent = version.id === file?.currentVersionId
                return (
                  <div key={version.id} className="flex items-center gap-3 px-4 py-3 text-sm">
                    <HistoryIcon
                      className="text-muted-foreground size-4 shrink-0"
                      aria-hidden="true"
                    />
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <span className="font-medium">Version {version.versionNumber}</span>
                        {isCurrent && <Badge variant="secondary">Current</Badge>}
                      </div>
                      <p className="text-muted-foreground">
                        {formatBytes(version.sizeBytes)} · {formatDateTime(version.createdAt)}
                      </p>
                    </div>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      disabled={isCurrent || restoreVersionMutation.isPending}
                      onClick={() => handleRestore(version.id)}
                    >
                      <RotateCcwIcon className="size-4" />
                      Restore
                    </Button>
                  </div>
                )
              })}
            </div>
          )}
        </ScrollArea>

        <DialogFooter className="sm:justify-between">
          <Button type="button" variant="outline" onClick={() => fileInputRef.current?.click()}>
            <UploadIcon className="size-4" />
            Upload new version
          </Button>
          <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
            Close
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
