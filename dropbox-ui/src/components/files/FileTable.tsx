import { FolderIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { ListSkeleton } from '@/components/common/ListSkeleton'
import { EntryRow } from './EntryRow'
import { SelectAllCheckbox } from './SelectAllCheckbox'
import { toBrowserEntry, type BrowserEntry } from '@/lib/fileIcons'
import type { Folder } from '@/api/folders'
import type { FileEntry } from '@/api/files'

interface FileTableProps {
  isLoading: boolean
  isError: boolean
  onRetry?: () => void
  folders: Folder[]
  files: FileEntry[]
  selected: Set<string>
  onSelectEntry: (id: string, options: { toggle?: boolean; range?: boolean }) => void
  onClearSelection: () => void
  onSelectAll: () => void
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

export function FileTable({
  isLoading,
  isError,
  onRetry,
  folders,
  files,
  selected,
  onSelectEntry,
  onClearSelection,
  onSelectAll,
  onRequestRename,
  onRequestMove,
  onRequestVersionHistory,
  onRequestShare,
  onRequestDetails,
  onRequestPreview,
  onRequestTrash,
  emptyAction,
  emptyState,
}: FileTableProps) {
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
    <div role="table" aria-label="Files and folders">
      <div className="text-muted-foreground flex items-center gap-3 border-b px-6 py-2 text-xs font-medium">
        <SelectAllCheckbox
          total={folders.length + files.length}
          selectedCount={selected.size}
          onSelectAll={onSelectAll}
          onClear={onClearSelection}
        />
        <span className="min-w-0 flex-1">Name</span>
        <span className="w-28 shrink-0 text-right">Modified</span>
        <span className="w-16 shrink-0 text-right">Size</span>
        <span className="w-7 shrink-0" />
      </div>

      {folders.map((folder) => (
        <EntryRow
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
        <EntryRow
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
