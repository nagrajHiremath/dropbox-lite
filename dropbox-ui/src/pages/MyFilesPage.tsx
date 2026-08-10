import { useEffect, useMemo, useRef, useState, type ChangeEvent } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { FolderPlusIcon, SearchIcon, UploadIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { PageHeader } from '@/components/common/PageHeader'
import type { BreadcrumbItem } from '@/components/layout/Breadcrumbs'
import { FileTable } from '@/components/files/FileTable'
import { EntryGrid } from '@/components/files/EntryGrid'
import { ViewModeToggle } from '@/components/files/ViewModeToggle'
import { CreateFolderDialog } from '@/components/modals/CreateFolderDialog'
import { RenameDialog } from '@/components/modals/RenameDialog'
import { MoveDialog } from '@/components/modals/MoveDialog'
import { VersionHistoryDialog } from '@/components/modals/VersionHistoryDialog'
import { ShareDialog } from '@/components/modals/ShareDialog'
import { DetailsDialog } from '@/components/modals/DetailsDialog'
import { FilePreviewDialog } from '@/components/modals/FilePreviewDialog'
import { UploadDropZone } from '@/components/upload/UploadDropZone'
import { EmptyFolderDropZone } from '@/components/upload/EmptyFolderDropZone'
import { EmptyAreaContextMenu } from '@/components/files/EmptyAreaContextMenu'
import { useBreadcrumbs } from '@/hooks/useBreadcrumbs'
import { useSelection } from '@/hooks/useSelection'
import { useSelectAllShortcut } from '@/hooks/useSelectAllShortcut'
import { foldersKey, filesKey } from '@/hooks/queryKeys'
import { listFolders, createFolder, type Folder } from '@/api/folders'
import { listFiles, type FileEntry } from '@/api/files'
import { useRestoreFolder, useTrashFolder } from '@/hooks/useFolderMutations'
import { useRestoreFile, useTrashFile } from '@/hooks/useFileMutations'
import { useUploadQueueStore } from '@/store/uploadQueueStore'
import { useViewModeStore } from '@/store/viewModeStore'
import { buildDropTree } from '@/lib/dropTree'
import type { BrowserEntry } from '@/lib/fileIcons'

const EMPTY_FOLDERS: Folder[] = []
const EMPTY_FILES: FileEntry[] = []

type ActiveDialog =
  | { type: 'create' }
  | { type: 'rename'; entry: BrowserEntry }
  | { type: 'move'; entry: BrowserEntry }
  | { type: 'version-history'; entry: BrowserEntry }
  | { type: 'share'; entry: BrowserEntry }
  | { type: 'details'; entry: BrowserEntry }
  | { type: 'preview'; entry: BrowserEntry }
  | null

export default function MyFilesPage() {
  const { folderId } = useParams<{ folderId?: string }>()
  const [activeDialog, setActiveDialog] = useState<ActiveDialog>(null)
  const [search, setSearch] = useState('')
  const fileInputRef = useRef<HTMLInputElement>(null)
  const enqueueUpload = useUploadQueueStore((s) => s.enqueue)
  const setFolderPrepMessage = useUploadQueueStore((s) => s.setFolderPrepMessage)
  const viewMode = useViewModeStore((s) => s.mode)
  const queryClient = useQueryClient()

  const foldersQuery = useQuery({
    queryKey: foldersKey(folderId),
    queryFn: () => listFolders(folderId),
  })
  const filesQuery = useQuery({
    queryKey: filesKey(folderId),
    queryFn: () => listFiles(folderId),
  })
  const { data: chain } = useBreadcrumbs(folderId)

  const folders = foldersQuery.data?.content ?? EMPTY_FOLDERS
  const files = filesQuery.data?.content ?? EMPTY_FILES

  const query = search.trim().toLowerCase()
  const visibleFolders = useMemo(
    () => (query ? folders.filter((f) => f.name.toLowerCase().includes(query)) : folders),
    [folders, query],
  )
  const visibleFiles = useMemo(
    () => (query ? files.filter((f) => f.name.toLowerCase().includes(query)) : files),
    [files, query],
  )
  const orderedIds = useMemo(
    () => [...visibleFolders.map((f) => f.id), ...visibleFiles.map((f) => f.id)],
    [visibleFolders, visibleFiles],
  )

  const { selected, select, clear, selectAll } = useSelection(orderedIds)
  useSelectAllShortcut(selectAll)

  useEffect(() => {
    clear()
    setSearch('')
  }, [folderId, clear])

  const trashFolder = useTrashFolder()
  const trashFile = useTrashFile()
  const restoreFolder = useRestoreFolder()
  const restoreFile = useRestoreFile()

  function errorMessage(err: unknown) {
    return err instanceof Error ? err.message : 'Something went wrong. Please try again.'
  }

  /** Single-item trash (context menu). Clears selection only if this item was selected. */
  function trashEntry(entry: BrowserEntry) {
    const wasSelected = selected.has(entry.type === 'folder' ? entry.folder.id : entry.file.id)

    if (entry.type === 'folder') {
      const { id, parentId } = entry.folder
      const parent = parentId ?? undefined
      trashFolder.mutate(
        { id, parentId: parent },
        {
          onSuccess: () => {
            toast.success('Moved to Trash', {
              action: {
                label: 'Undo',
                onClick: () => restoreFolder.mutate({ id, parentId: parent }),
              },
            })
            if (wasSelected) clear()
          },
          onError: (err) => toast.error(errorMessage(err)),
        },
      )
    } else {
      const { id, folderId: fId } = entry.file
      const folder = fId ?? undefined
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
  }

  /** Bulk trash (toolbar). Clears selection immediately - matches common bulk-action UX; a
   * per-item failure still surfaces via its own error toast even though the row stays selected
   * only until the next list refetch confirms it wasn't actually removed. */
  function trashSelected() {
    folders
      .filter((f) => selected.has(f.id))
      .forEach((folder) => trashEntry({ type: 'folder', folder }))
    files.filter((f) => selected.has(f.id)).forEach((file) => trashEntry({ type: 'file', file }))
    clear()
  }

  function uploadFiles(uploadFiles: File[]) {
    uploadFiles.forEach((file) => enqueueUpload(file, { kind: 'new-file', folderId }))
  }

  function handleFileInputChange(e: ChangeEvent<HTMLInputElement>) {
    if (e.target.files) uploadFiles(Array.from(e.target.files))
    e.target.value = '' // allow re-selecting the same file(s) again later
  }

  /**
   * Drag-and-drop counterpart to uploadFiles - the OS drop payload may
   * contain whole folders, not just flat files (the file-input path above
   * never does, so it doesn't need any of this). Walks the dropped tree,
   * recreates any folder structure via the existing single-folder
   * createFolder API (sequential, top-down - a child's parent id must exist
   * before it can be created), then enqueues each file against its resolved
   * folder. A folder that fails to create isn't retried; anything that would
   * have landed inside it is skipped with one summary toast rather than
   * failing/uploading into the wrong place.
   *
   * UploadTray shows folderPrepMessage in place of its usual per-file
   * summary while folders are being created (there's no file entry yet to
   * show progress for) - cleared the moment file enqueueing starts, so the
   * tray transitions to its normal view exactly when the first real upload
   * appears.
   */
  async function handleDroppedEntries(entries: FileSystemEntry[]) {
    const hasFolders = entries.some((entry) => entry.isDirectory)
    if (hasFolders) setFolderPrepMessage('Preparing folder upload…')

    const tree = await buildDropTree(entries)

    if (tree.folderPaths.length === 0) {
      setFolderPrepMessage(null)
      tree.files.forEach(({ file }) => enqueueUpload(file, { kind: 'new-file', folderId }))
      return
    }

    setFolderPrepMessage(
      `Creating ${tree.folderPaths.length} folder${tree.folderPaths.length === 1 ? '' : 's'}…`,
    )

    const idByPath = new Map<string, string | undefined>([['', folderId]])
    const failedPaths = new Set<string>()

    for (const path of tree.folderPaths) {
      const key = path.join('/')
      const parentKey = path.slice(0, -1).join('/')
      if (failedPaths.has(parentKey)) {
        failedPaths.add(key)
        continue
      }
      try {
        const folder = await createFolder(path[path.length - 1], idByPath.get(parentKey))
        idByPath.set(key, folder.id)
      } catch (err) {
        toast.error(`Couldn't create folder "${path[path.length - 1]}": ${errorMessage(err)}`)
        failedPaths.add(key)
      }
    }

    setFolderPrepMessage(null)
    void queryClient.invalidateQueries({ queryKey: foldersKey(folderId) })

    let skipped = 0
    tree.files.forEach(({ file, folderPath }) => {
      const key = folderPath.join('/')
      if (failedPaths.has(key)) {
        skipped++
        return
      }
      enqueueUpload(file, { kind: 'new-file', folderId: idByPath.get(key) })
    })
    if (skipped > 0) {
      toast.error(
        `Skipped ${skipped} file${skipped === 1 ? '' : 's'} in folders that couldn't be created`,
      )
    }
  }

  const isLoading = foldersQuery.isLoading || filesQuery.isLoading
  const isError = foldersQuery.isError || filesQuery.isError
  const breadcrumbItems: BreadcrumbItem[] = [
    { label: 'My Files', to: '/files', isFolderCrumb: true, folderId: undefined },
    ...(chain ?? []).map((f) => ({
      label: f.name,
      to: `/files/${f.id}`,
      isFolderCrumb: true,
      folderId: f.id,
    })),
  ]

  return (
    <UploadDropZone onDropEntries={handleDroppedEntries}>
      {(isDragging) => (
        <>
          <input
            ref={fileInputRef}
            type="file"
            multiple
            className="hidden"
            onChange={handleFileInputChange}
          />

          <PageHeader
            breadcrumbs={breadcrumbItems}
            selection={{
              count: selected.size,
              onClear: clear,
              onSelectAll: selectAll,
              onTrashSelected: trashSelected,
            }}
          >
            <div className="relative w-56">
              <SearchIcon
                className="text-muted-foreground pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2"
                aria-hidden="true"
              />
              <Input
                type="search"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search this folder"
                aria-label="Search files and folders in this folder"
                className="bg-background h-9 rounded-full border-transparent pl-9 shadow-none"
              />
            </div>
            <ViewModeToggle />
            <Button size="sm" variant="outline" onClick={() => fileInputRef.current?.click()}>
              <UploadIcon className="size-4" />
              Upload
            </Button>
            <Button size="sm" onClick={() => setActiveDialog({ type: 'create' })}>
              <FolderPlusIcon className="size-4" />
              New folder
            </Button>
          </PageHeader>

          {viewMode === 'grid' ? (
            <EntryGrid
              isLoading={isLoading}
              isError={isError}
              onRetry={() => {
                void foldersQuery.refetch()
                void filesQuery.refetch()
              }}
              folders={visibleFolders}
              files={visibleFiles}
              selected={selected}
              onSelectEntry={select}
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
              emptyState={
                query ? (
                  <p className="text-muted-foreground p-6 text-center text-sm">
                    No files or folders match "{search.trim()}".
                  </p>
                ) : (
                  <EmptyAreaContextMenu
                    hasItems={false}
                    onNewFolder={() => setActiveDialog({ type: 'create' })}
                    onUploadClick={() => fileInputRef.current?.click()}
                  >
                    <EmptyFolderDropZone
                      isDragging={isDragging}
                      onUploadClick={() => fileInputRef.current?.click()}
                    />
                  </EmptyAreaContextMenu>
                )
              }
            />
          ) : (
            <FileTable
              isLoading={isLoading}
              isError={isError}
              onRetry={() => {
                void foldersQuery.refetch()
                void filesQuery.refetch()
              }}
              folders={visibleFolders}
              files={visibleFiles}
              selected={selected}
              onSelectEntry={select}
              onClearSelection={clear}
              onSelectAll={selectAll}
              onRequestRename={(entry) => setActiveDialog({ type: 'rename', entry })}
              onRequestMove={(entry) => setActiveDialog({ type: 'move', entry })}
              onRequestVersionHistory={(entry) =>
                setActiveDialog({ type: 'version-history', entry })
              }
              onRequestShare={(entry) => setActiveDialog({ type: 'share', entry })}
              onRequestDetails={(entry) => setActiveDialog({ type: 'details', entry })}
              onRequestPreview={(entry) => setActiveDialog({ type: 'preview', entry })}
              onRequestTrash={trashEntry}
              emptyState={
                query ? (
                  <p className="text-muted-foreground p-6 text-center text-sm">
                    No files or folders match "{search.trim()}".
                  </p>
                ) : (
                  <EmptyAreaContextMenu
                    hasItems={false}
                    onNewFolder={() => setActiveDialog({ type: 'create' })}
                    onUploadClick={() => fileInputRef.current?.click()}
                  >
                    <EmptyFolderDropZone
                      isDragging={isDragging}
                      onUploadClick={() => fileInputRef.current?.click()}
                    />
                  </EmptyAreaContextMenu>
                )
              }
            />
          )}

          {folders.length + files.length > 0 && (
            <EmptyAreaContextMenu
              hasItems
              onNewFolder={() => setActiveDialog({ type: 'create' })}
              onUploadClick={() => fileInputRef.current?.click()}
              onSelectAll={selectAll}
            >
              <div className="min-h-[50vh]" />
            </EmptyAreaContextMenu>
          )}

          <CreateFolderDialog
            open={activeDialog?.type === 'create'}
            onOpenChange={(open) => setActiveDialog(open ? { type: 'create' } : null)}
            parentId={folderId}
          />
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
      )}
    </UploadDropZone>
  )
}
