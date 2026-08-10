import type { ReactNode } from 'react'
import { CheckSquareIcon, FolderPlusIcon, UploadIcon } from 'lucide-react'
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuTrigger,
} from '@/components/ui/context-menu'

interface EmptyAreaContextMenuProps {
  children: ReactNode
  onNewFolder: () => void
  onUploadClick: () => void
  /** Omit (or leave hasItems false) when there's nothing to select - the
   * empty-folder case wrapping EmptyFolderDropZone never passes this. */
  onSelectAll?: () => void
  hasItems: boolean
}

/**
 * Right-click-empty-space menu (New folder / Upload / Select all) -
 * MyFilesPage only, wrapped around two deliberately non-nested targets to
 * sidestep an unverified edge case: independent Radix ContextMenu roots
 * where one is an ancestor of another (e.g. wrapping the populated
 * grid/list container itself, whose tiles/rows each have their own
 * ContextMenuTrigger) aren't guaranteed not to conflict without a live
 * browser to test in. Wrapping the empty-state box (zero items = nothing
 * nested inside it) and a plain filler strip rendered *after* the list/grid
 * (a sibling of the rows, never their ancestor) both avoid that nesting
 * entirely while still covering the cases that matter: an empty folder, and
 * right-clicking blank space below a populated one.
 */
export function EmptyAreaContextMenu({
  children,
  onNewFolder,
  onUploadClick,
  onSelectAll,
  hasItems,
}: EmptyAreaContextMenuProps) {
  return (
    <ContextMenu>
      <ContextMenuTrigger asChild>{children}</ContextMenuTrigger>
      <ContextMenuContent>
        <ContextMenuItem onSelect={onNewFolder}>
          <FolderPlusIcon className="size-4" />
          New folder
        </ContextMenuItem>
        <ContextMenuItem onSelect={onUploadClick}>
          <UploadIcon className="size-4" />
          Upload files
        </ContextMenuItem>
        {hasItems && onSelectAll && (
          <ContextMenuItem onSelect={onSelectAll}>
            <CheckSquareIcon className="size-4" />
            Select all
          </ContextMenuItem>
        )}
      </ContextMenuContent>
    </ContextMenu>
  )
}
