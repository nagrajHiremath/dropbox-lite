import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { RotateCcwIcon, Trash2Icon } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { ListSkeleton } from '@/components/common/ListSkeleton'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { PermanentDeleteDialog } from '@/components/modals/PermanentDeleteDialog'
import { trashedFilesKey, trashedFoldersKey } from '@/hooks/queryKeys'
import { listTrashedFiles } from '@/api/files'
import { listTrashedFolders } from '@/api/folders'
import { useRestoreFile, usePermanentlyDeleteFile } from '@/hooks/useFileMutations'
import { useRestoreFolder, usePermanentlyDeleteFolder } from '@/hooks/useFolderMutations'
import { ApiError } from '@/api/client'
import { getEntryIcon, toBrowserEntry, type BrowserEntry } from '@/lib/fileIcons'
import { formatBytes, formatDate } from '@/lib/format'

function errorMessage(err: unknown) {
  return err instanceof ApiError ? err.message : 'Something went wrong. Please try again.'
}

interface TrashRowProps {
  entry: BrowserEntry
  onRestore: (entry: BrowserEntry) => void
  onRequestDelete: (entry: BrowserEntry) => void
}

/**
 * Not EntryRow - a trashed item can't be renamed/moved/shared/previewed
 * (backend actions all require ACTIVE status), so it only needs the two
 * actions that actually apply here, no menu needed for just two buttons.
 */
function TrashRow({ entry, onRestore, onRequestDelete }: TrashRowProps) {
  const name = entry.type === 'folder' ? entry.folder.name : entry.file.name
  const updatedAt = entry.type === 'folder' ? entry.folder.updatedAt : entry.file.updatedAt
  const sizeLabel =
    entry.type === 'folder'
      ? '—'
      : entry.file.sizeBytes != null
        ? formatBytes(entry.file.sizeBytes)
        : '—'
  const Icon = getEntryIcon(entry)

  return (
    <div className="flex items-center gap-3 border-b px-6 py-2 text-sm">
      <Icon className="text-muted-foreground size-4.5 shrink-0" aria-hidden="true" />
      <span className="min-w-0 flex-1 truncate font-medium">{name}</span>
      <span className="text-muted-foreground w-28 shrink-0 text-right">
        {formatDate(updatedAt)}
      </span>
      <span className="text-muted-foreground w-16 shrink-0 text-right">{sizeLabel}</span>
      <div className="flex shrink-0 gap-1">
        <Button
          variant="ghost"
          size="icon"
          className="size-7"
          onClick={() => onRestore(entry)}
          aria-label={`Restore ${name}`}
        >
          <RotateCcwIcon className="size-4" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          className="text-destructive hover:text-destructive size-7"
          onClick={() => onRequestDelete(entry)}
          aria-label={`Permanently delete ${name}`}
        >
          <Trash2Icon className="size-4" />
        </Button>
      </div>
    </div>
  )
}

export default function TrashPage() {
  const [deleteTarget, setDeleteTarget] = useState<BrowserEntry | null>(null)
  const [emptyTrashOpen, setEmptyTrashOpen] = useState(false)

  const foldersQuery = useQuery({
    queryKey: trashedFoldersKey,
    queryFn: () => listTrashedFolders(),
  })
  const filesQuery = useQuery({ queryKey: trashedFilesKey, queryFn: () => listTrashedFiles() })

  const restoreFolder = useRestoreFolder()
  const restoreFile = useRestoreFile()
  const deleteFolder = usePermanentlyDeleteFolder()
  const deleteFile = usePermanentlyDeleteFile()

  const folders = foldersQuery.data?.content ?? []
  const files = filesQuery.data?.content ?? []

  function handleRestore(entry: BrowserEntry) {
    if (entry.type === 'folder') {
      restoreFolder.mutate(
        { id: entry.folder.id, parentId: entry.folder.parentId ?? undefined },
        {
          onSuccess: () => toast.success('Restored'),
          onError: (err) => toast.error(errorMessage(err)),
        },
      )
    } else {
      restoreFile.mutate(
        { id: entry.file.id, folderId: entry.file.folderId ?? undefined },
        {
          onSuccess: () => toast.success('Restored'),
          onError: (err) => toast.error(errorMessage(err)),
        },
      )
    }
  }

  /** No bulk "empty trash" endpoint exists server-side - fires the same
   * per-item permanent-delete mutation PermanentDeleteDialog uses, once per
   * currently-listed row, mirroring MyFilesPage's trashSelected() bulk-action
   * precedent: each fire-and-forget mutation surfaces its own error toast on
   * failure rather than blocking/rolling back the rest of the batch. */
  function handleEmptyTrash() {
    folders.forEach((folder) =>
      deleteFolder.mutate(
        { id: folder.id },
        { onError: (err) => toast.error(`Couldn't delete "${folder.name}": ${errorMessage(err)}`) },
      ),
    )
    files.forEach((file) =>
      deleteFile.mutate(
        { id: file.id },
        { onError: (err) => toast.error(`Couldn't delete "${file.name}": ${errorMessage(err)}`) },
      ),
    )
    toast.success('Trash emptied')
    setEmptyTrashOpen(false)
  }

  const isLoading = foldersQuery.isLoading || filesQuery.isLoading
  const isError = foldersQuery.isError || filesQuery.isError
  const isEmpty = folders.length === 0 && files.length === 0
  const itemCount = folders.length + files.length

  return (
    <>
      <PageHeader breadcrumbs={[{ label: 'Trash' }]}>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="text-destructive hover:text-destructive"
          disabled={isEmpty || isLoading}
          onClick={() => setEmptyTrashOpen(true)}
        >
          <Trash2Icon className="size-4" />
          Empty Trash
        </Button>
      </PageHeader>

      {isLoading ? (
        <ListSkeleton />
      ) : isError ? (
        <ErrorState
          description="Couldn't load Trash."
          onRetry={() => {
            void foldersQuery.refetch()
            void filesQuery.refetch()
          }}
        />
      ) : isEmpty ? (
        <EmptyState
          icon={Trash2Icon}
          title="Trash is empty"
          description="Items you delete will appear here until you restore or permanently delete them."
        />
      ) : (
        <div role="table" aria-label="Trash">
          <div className="text-muted-foreground flex items-center gap-3 border-b px-6 py-2 text-xs font-medium">
            <span className="min-w-0 flex-1">Name</span>
            <span className="w-28 shrink-0 text-right">Trashed</span>
            <span className="w-16 shrink-0 text-right">Size</span>
            <span className="w-[60px] shrink-0" />
          </div>
          {folders.map((folder) => (
            <TrashRow
              key={folder.id}
              entry={toBrowserEntry(folder)}
              onRestore={handleRestore}
              onRequestDelete={setDeleteTarget}
            />
          ))}
          {files.map((file) => (
            <TrashRow
              key={file.id}
              entry={toBrowserEntry(file)}
              onRestore={handleRestore}
              onRequestDelete={setDeleteTarget}
            />
          ))}
        </div>
      )}

      <PermanentDeleteDialog
        entry={deleteTarget}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
      />

      <Dialog open={emptyTrashOpen} onOpenChange={setEmptyTrashOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Empty Trash?</DialogTitle>
            <DialogDescription>
              This will permanently delete all {itemCount} item{itemCount === 1 ? '' : 's'} in
              Trash. This can&apos;t be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setEmptyTrashOpen(false)}>
              Cancel
            </Button>
            <Button type="button" variant="destructive" onClick={handleEmptyTrash}>
              Empty Trash
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
