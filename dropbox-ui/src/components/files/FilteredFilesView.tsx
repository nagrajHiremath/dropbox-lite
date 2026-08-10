import { useEffect, useMemo, useState } from 'react'
import { useQuery, type QueryKey } from '@tanstack/react-query'
import { toast } from 'sonner'
import type { LucideIcon } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { ListSkeleton } from '@/components/common/ListSkeleton'
import { EntryRow } from './EntryRow'
import { EntryGridTile } from './EntryGridTile'
import { ViewModeToggle } from './ViewModeToggle'
import { SelectAllCheckbox } from './SelectAllCheckbox'
import { RenameDialog } from '@/components/modals/RenameDialog'
import { MoveDialog } from '@/components/modals/MoveDialog'
import { VersionHistoryDialog } from '@/components/modals/VersionHistoryDialog'
import { ShareDialog } from '@/components/modals/ShareDialog'
import { DetailsDialog } from '@/components/modals/DetailsDialog'
import { FilePreviewDialog } from '@/components/modals/FilePreviewDialog'
import { useSelection } from '@/hooks/useSelection'
import { useSelectAllShortcut } from '@/hooks/useSelectAllShortcut'
import { useRestoreFile, useTrashFile } from '@/hooks/useFileMutations'
import { useViewModeStore } from '@/store/viewModeStore'
import { toBrowserEntry, type BrowserEntry } from '@/lib/fileIcons'
import type { FileEntry } from '@/api/files'
import type { PageResponse } from '@/api/folders'

const EMPTY_FILES: FileEntry[] = []

type ActiveDialog =
  | { type: 'rename'; entry: BrowserEntry }
  | { type: 'move'; entry: BrowserEntry }
  | { type: 'version-history'; entry: BrowserEntry }
  | { type: 'share'; entry: BrowserEntry }
  | { type: 'details'; entry: BrowserEntry }
  | { type: 'preview'; entry: BrowserEntry }
  | null

interface FilteredFilesViewProps {
  title: string
  queryKey: QueryKey
  queryFn: () => Promise<PageResponse<FileEntry>>
  emptyIcon: LucideIcon
  emptyTitle: string
  emptyDescription: string
}

function errorMessage(err: unknown) {
  return err instanceof Error ? err.message : 'Something went wrong. Please try again.'
}

/**
 * UI-13: shared by Recent/Photos/Videos - account-wide, files-only,
 * filtered views over the same files MyFilesPage browses by folder (see
 * FileService.listFiles's view=recent/type=image|video). Unlike
 * MyFilesPage, there's no folder context here - no breadcrumbs, no
 * create-folder, no upload (uploading "into Recent" doesn't mean anything),
 * and Move still works via MoveDialog's own folder picker. Every other
 * per-file action (rename/move/share/version history/details/preview/
 * trash) is identical, so this reuses EntryRow and the same dialogs rather
 * than rebuilding them.
 */
export function FilteredFilesView({
  title,
  queryKey,
  queryFn,
  emptyIcon,
  emptyTitle,
  emptyDescription,
}: FilteredFilesViewProps) {
  const [activeDialog, setActiveDialog] = useState<ActiveDialog>(null)
  const viewMode = useViewModeStore((s) => s.mode)

  const filesQuery = useQuery({ queryKey, queryFn })
  const files = filesQuery.data?.content ?? EMPTY_FILES
  const orderedIds = useMemo(() => files.map((f) => f.id), [files])

  const { selected, select, clear, selectAll } = useSelection(orderedIds)
  useSelectAllShortcut(selectAll)

  useEffect(() => {
    clear()
  }, [queryKey, clear])

  const trashFile = useTrashFile()
  const restoreFile = useRestoreFile()

  /** Single-item trash. Clears selection only if this item was selected. */
  function trashEntry(entry: BrowserEntry) {
    if (entry.type === 'folder') return // files-only view, never reachable
    const { id, folderId: fId } = entry.file
    const folder = fId ?? undefined
    const wasSelected = selected.has(id)

    trashFile.mutate(
      { id, folderId: folder },
      {
        onSuccess: () => {
          toast.success('Moved to Trash', {
            action: {
              label: 'Undo',
              onClick: () => restoreFile.mutate({ id, folderId: folder }),
            },
          })
          if (wasSelected) clear()
        },
        onError: (err) => toast.error(errorMessage(err)),
      },
    )
  }

  function trashSelected() {
    files.filter((f) => selected.has(f.id)).forEach((file) => trashEntry({ type: 'file', file }))
    clear()
  }

  return (
    <>
      <PageHeader
        breadcrumbs={[{ label: title }]}
        selection={{
          count: selected.size,
          onClear: clear,
          onSelectAll: selectAll,
          onTrashSelected: trashSelected,
        }}
      >
        <ViewModeToggle />
      </PageHeader>

      {filesQuery.isLoading ? (
        <ListSkeleton />
      ) : filesQuery.isError ? (
        <ErrorState description="Couldn't load this view." onRetry={() => filesQuery.refetch()} />
      ) : files.length === 0 ? (
        <EmptyState icon={emptyIcon} title={emptyTitle} description={emptyDescription} />
      ) : viewMode === 'grid' ? (
        <div
          role="table"
          aria-label={title}
          className="grid grid-cols-2 gap-4 p-6 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6"
        >
          {files.map((file) => (
            <EntryGridTile
              key={file.id}
              entry={toBrowserEntry(file)}
              selected={selected.has(file.id)}
              onSelect={select}
              onClearSelection={clear}
              onRequestRename={(entry) => setActiveDialog({ type: 'rename', entry })}
              onRequestMove={(entry) => setActiveDialog({ type: 'move', entry })}
              onRequestVersionHistory={(entry) =>
                setActiveDialog({ type: 'version-history', entry })
              }
              onRequestShare={(entry) => setActiveDialog({ type: 'share', entry })}
              onRequestDetails={(entry) => setActiveDialog({ type: 'details', entry })}
              onRequestPreview={(entry) => setActiveDialog({ type: 'preview', entry })}
              onRequestTrash={trashEntry}
            />
          ))}
        </div>
      ) : (
        <div role="table" aria-label={title}>
          <div className="text-muted-foreground flex items-center gap-3 border-b px-6 py-2 text-xs font-medium">
            <SelectAllCheckbox
              total={files.length}
              selectedCount={selected.size}
              onSelectAll={selectAll}
              onClear={clear}
            />
            <span className="min-w-0 flex-1">Name</span>
            <span className="w-28 shrink-0 text-right">Modified</span>
            <span className="w-16 shrink-0 text-right">Size</span>
            <span className="w-7 shrink-0" />
          </div>
          {files.map((file) => (
            <EntryRow
              key={file.id}
              entry={toBrowserEntry(file)}
              selected={selected.has(file.id)}
              onSelect={select}
              onClearSelection={clear}
              onRequestRename={(entry) => setActiveDialog({ type: 'rename', entry })}
              onRequestMove={(entry) => setActiveDialog({ type: 'move', entry })}
              onRequestVersionHistory={(entry) =>
                setActiveDialog({ type: 'version-history', entry })
              }
              onRequestShare={(entry) => setActiveDialog({ type: 'share', entry })}
              onRequestDetails={(entry) => setActiveDialog({ type: 'details', entry })}
              onRequestPreview={(entry) => setActiveDialog({ type: 'preview', entry })}
              onRequestTrash={trashEntry}
            />
          ))}
        </div>
      )}

      <RenameDialog
        entry={activeDialog?.type === 'rename' ? activeDialog.entry : null}
        onOpenChange={(open) => !open && setActiveDialog(null)}
      />
      <MoveDialog
        entry={activeDialog?.type === 'move' ? activeDialog.entry : null}
        onOpenChange={(open) => !open && setActiveDialog(null)}
      />
      <VersionHistoryDialog
        file={
          activeDialog?.type === 'version-history' && activeDialog.entry.type === 'file'
            ? activeDialog.entry.file
            : null
        }
        onOpenChange={(open) => !open && setActiveDialog(null)}
      />
      <ShareDialog
        file={
          activeDialog?.type === 'share' && activeDialog.entry.type === 'file'
            ? activeDialog.entry.file
            : null
        }
        onOpenChange={(open) => !open && setActiveDialog(null)}
      />
      <DetailsDialog
        entry={activeDialog?.type === 'details' ? activeDialog.entry : null}
        onOpenChange={(open) => !open && setActiveDialog(null)}
        onRequestVersionHistory={(entry) => setActiveDialog({ type: 'version-history', entry })}
        onRequestShare={(entry) => setActiveDialog({ type: 'share', entry })}
      />
      <FilePreviewDialog
        file={
          activeDialog?.type === 'preview' && activeDialog.entry.type === 'file'
            ? activeDialog.entry.file
            : null
        }
        onOpenChange={(open) => !open && setActiveDialog(null)}
        onRequestShare={() =>
          activeDialog?.type === 'preview' &&
          setActiveDialog({ type: 'share', entry: activeDialog.entry })
        }
      />
    </>
  )
}
