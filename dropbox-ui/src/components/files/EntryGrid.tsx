import { FolderIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { ListSkeleton } from '@/components/common/ListSkeleton'
import { EntryGridTile } from './EntryGridTile'
import { toBrowserEntry, type BrowserEntry } from '@/lib/fileIcons'
import type { Folder } from '@/api/folders'
import type { FileEntry } from '@/api/files'

interface EntryGridProps {
  isLoading: boolean
  isError: boolean
  onRetry?: () => void
  folders: Folder[]
  files: FileEntry[]
  selected: Set<string>
  onSelectEntry: (id: string, options: { toggle?: boolean; range?: boolean }) => void
  onClearSelection: () => void
  onRequestRename: (entry: BrowserEntry) => void
  onRequestMove: (entry: BrowserEntry) => void
  onRequestVersionHistory: (entry: BrowserEntry) => void
  onRequestShare: (entry: BrowserEntry) => void
  onRequestDetails: (entry: BrowserEntry) => void
  onRequestPreview: (entry: BrowserEntry) => void
  onRequestTrash: (entry: BrowserEntry) => void
  emptyAction?: ReactNode
  /** Overrides the default EmptyState entirely - MyFilesPage passes its
   * permanent drop-target box instead (see EmptyFolderDropZone). */
  emptyState?: ReactNode
}

/** Grid counterpart to FileTable - same data, same actions, tiled layout via EntryGridTile. */
export function EntryGrid({
  isLoading,
  isError,
  onRetry,
  folders,
  files,
  selected,
  onSelectEntry,
  onClearSelection,
  onRequestRename,
  onRequestMove,
  onRequestVersionHistory,
  onRequestShare,
  onRequestDetails,
  onRequestPreview,
  onRequestTrash,
  emptyAction,
  emptyState,
}: EntryGridProps) {
  if (isLoading) return <ListSkeleton />
  if (isError) {
    return <ErrorState description="Couldn't load this folder." onRetry={onRetry} />
  }
  if (folders.length === 0 && files.length === 0) {
    return (
      emptyState ?? (
        <EmptyState
          icon={FolderIcon}
          title="This folder is empty"
          description="Files and folders you add will show up here."
          action={emptyAction}
        />
      )
    )
  }

  return (
    <div
      role="table"
      aria-label="Files and folders"
      className="grid grid-cols-2 gap-4 p-6 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6"
    >
      {folders.map((folder) => (
        <EntryGridTile
          key={folder.id}
          entry={toBrowserEntry(folder)}
          selected={selected.has(folder.id)}
          onSelect={onSelectEntry}
          onClearSelection={onClearSelection}
          onRequestRename={onRequestRename}
          onRequestMove={onRequestMove}
          onRequestVersionHistory={onRequestVersionHistory}
          onRequestShare={onRequestShare}
          onRequestDetails={onRequestDetails}
          onRequestPreview={onRequestPreview}
          onRequestTrash={onRequestTrash}
        />
      ))}
      {files.map((file) => (
        <EntryGridTile
          key={file.id}
          entry={toBrowserEntry(file)}
          selected={selected.has(file.id)}
          onSelect={onSelectEntry}
          onClearSelection={onClearSelection}
          onRequestRename={onRequestRename}
          onRequestMove={onRequestMove}
          onRequestVersionHistory={onRequestVersionHistory}
          onRequestShare={onRequestShare}
          onRequestDetails={onRequestDetails}
          onRequestPreview={onRequestPreview}
          onRequestTrash={onRequestTrash}
        />
      ))}
    </div>
  )
}
