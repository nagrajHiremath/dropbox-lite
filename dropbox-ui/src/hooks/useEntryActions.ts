import type { DragEvent, MouseEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { getEntryIcon, type BrowserEntry } from '@/lib/fileIcons'
import { formatBytes } from '@/lib/format'
import { useDragStore } from '@/store/dragStore'
import { useDownloadQueueStore } from '@/store/downloadQueueStore'
import { useDropTarget } from '@/hooks/useDropTarget'
import { useDragMove } from '@/hooks/useDragMove'
import { getMenuItems } from '@/components/files/entryMenu'

interface UseEntryActionsArgs {
  entry: BrowserEntry
  selected: boolean
  onSelect: (id: string, options: { toggle?: boolean; range?: boolean }) => void
  onClearSelection: () => void
  onRequestRename: (entry: BrowserEntry) => void
  onRequestMove: (entry: BrowserEntry) => void
  onRequestVersionHistory: (entry: BrowserEntry) => void
  onRequestShare: (entry: BrowserEntry) => void
  onRequestDetails: (entry: BrowserEntry) => void
  onRequestPreview: (entry: BrowserEntry) => void
  onRequestTrash: (entry: BrowserEntry) => void
}

/**
 * Click/drag/context-menu behavior for a single file or folder entry -
 * shared by EntryRow (list) and EntryGridTile (grid) so both layouts stay
 * behaviorally identical (same selection rules, same drag-to-move, same
 * actions menu) without duplicating the logic per layout.
 */
export function useEntryActions({
  entry,
  selected,
  onSelect,
  onClearSelection,
  onRequestRename,
  onRequestMove,
  onRequestVersionHistory,
  onRequestShare,
  onRequestDetails,
  onRequestPreview,
  onRequestTrash,
}: UseEntryActionsArgs) {
  const navigate = useNavigate()
  const setDragged = useDragStore((s) => s.setDragged)
  const clearDragged = useDragStore((s) => s.clearDragged)
  const moveDraggedTo = useDragMove()
  const enqueueDownload = useDownloadQueueStore((s) => s.enqueue)

  const id = entry.type === 'folder' ? entry.folder.id : entry.file.id
  const name = entry.type === 'folder' ? entry.folder.name : entry.file.name
  const updatedAt = entry.type === 'folder' ? entry.folder.updatedAt : entry.file.updatedAt
  const sizeLabel =
    entry.type === 'folder'
      ? '—'
      : entry.file.sizeBytes != null
        ? formatBytes(entry.file.sizeBytes)
        : '—'
  const currentParentId =
    entry.type === 'folder'
      ? (entry.folder.parentId ?? undefined)
      : (entry.file.folderId ?? undefined)
  const Icon = getEntryIcon(entry)

  // Only folders are drop targets (UI-07 §5) - can't drop onto itself or its
  // own current parent (a no-op, so not shown as a valid/glowing target).
  const { isOver, dropHandlers } = useDropTarget(
    (dragged) => entry.type === 'folder' && dragged.id !== id && dragged.currentParentId !== id,
    (dragged) => moveDraggedTo(dragged, id, name),
  )

  function open() {
    if (entry.type === 'folder') {
      navigate(`/files/${entry.folder.id}`)
    }
  }

  // Folders navigate; files open their preview (image preview, or the
  // no-preview + Download fallback for everything else - see UI-12).
  function handleDoubleClick() {
    if (entry.type === 'folder') {
      open()
    } else {
      onRequestPreview(entry)
    }
  }

  function handleDownload() {
    if (entry.type === 'folder' || entry.file.sizeBytes == null) return
    enqueueDownload({
      fileId: entry.file.id,
      fileName: entry.file.name,
      mimeType: entry.file.mimeType,
      totalBytes: entry.file.sizeBytes,
    })
  }

  // Plain click no longer selects - it just clears any active selection (a
  // no-op if nothing was selected), so browsing folders doesn't fight with
  // picking files. Ctrl/Cmd+click and Shift+click still work as before; the
  // hover checkbox is the discoverable multi-select entry point (mirrors
  // Drive/Dropbox).
  function handleClick(e: MouseEvent) {
    if (e.ctrlKey || e.metaKey) {
      onSelect(id, { toggle: true })
    } else if (e.shiftKey) {
      onSelect(id, { range: true })
    } else {
      onClearSelection()
    }
  }

  function handleCheckboxClick(e: MouseEvent) {
    e.stopPropagation() // don't also trigger the clear-on-click above
    onSelect(id, { toggle: true })
  }

  function handleContextMenu() {
    if (!selected) onSelect(id, {})
  }

  function handleMenuTriggerClick(e: MouseEvent) {
    e.stopPropagation()
    if (!selected) onSelect(id, {})
  }

  function handleDragStart(e: DragEvent<HTMLDivElement>) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', name)
    e.dataTransfer.setDragImage(e.currentTarget, 12, 12)
    setDragged({ id, type: entry.type, currentParentId })
  }

  const menuItems = getMenuItems(entry, {
    onOpen: open,
    onPreview: () => onRequestPreview(entry),
    onDetails: () => onRequestDetails(entry),
    onDownload: handleDownload,
    onRename: () => onRequestRename(entry),
    onMove: () => onRequestMove(entry),
    onVersionHistory: () => onRequestVersionHistory(entry),
    onShare: () => onRequestShare(entry),
    onTrash: () => onRequestTrash(entry),
  })

  return {
    id,
    name,
    updatedAt,
    sizeLabel,
    Icon,
    isOver,
    dropHandlers,
    menuItems,
    handleClick,
    handleCheckboxClick,
    handleDoubleClick,
    handleContextMenu,
    handleMenuTriggerClick,
    handleDragStart,
    handleDragEnd: clearDragged,
  }
}
