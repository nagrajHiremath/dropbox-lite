import { useCallback, useState, type DragEvent } from 'react'
import { useDragStore, type DraggedEntry } from '@/store/dragStore'

interface UseDropTargetResult {
  /** True only while a valid drag is hovering this target - safe to use directly for highlight styling. */
  isOver: boolean
  dropHandlers: {
    onDragEnter: (e: DragEvent) => void
    onDragLeave: (e: DragEvent) => void
    onDragOver: (e: DragEvent) => void
    onDrop: (e: DragEvent) => void
  }
}

/**
 * Reusable drop-target wiring shared by folder rows (EntryRow), the Sidebar's
 * "My Files" item, and Breadcrumbs items (UI-07). Uses a counter (not a
 * boolean) for enter/leave tracking - those events fire on every descendant
 * boundary crossing, not just the target element itself, same reason
 * UploadDropZone (UI-06) does the same thing. Reads live drag identity from
 * dragStore (not dataTransfer, which is opaque until the actual drop).
 */
export function useDropTarget(
  computeValidity: (dragged: DraggedEntry) => boolean,
  onDrop: (dragged: DraggedEntry) => void,
): UseDropTargetResult {
  const dragged = useDragStore((s) => s.dragged)
  const [dragCount, setDragCount] = useState(0)
  const isValid = dragged !== null && computeValidity(dragged)

  const handleDragEnter = useCallback(
    (e: DragEvent) => {
      if (!dragged) return
      e.preventDefault()
      setDragCount((c) => c + 1)
    },
    [dragged],
  )

  const handleDragLeave = useCallback((e: DragEvent) => {
    e.preventDefault()
    setDragCount((c) => Math.max(0, c - 1))
  }, [])

  const handleDragOver = useCallback(
    (e: DragEvent) => {
      // Not calling preventDefault leaves the drop disallowed - the browser
      // shows its own "not-allowed" cursor for free, no extra styling needed.
      if (dragged && isValid) e.preventDefault()
    },
    [dragged, isValid],
  )

  const handleDrop = useCallback(
    (e: DragEvent) => {
      e.preventDefault()
      setDragCount(0)
      if (dragged && isValid) onDrop(dragged)
    },
    [dragged, isValid, onDrop],
  )

  return {
    isOver: dragCount > 0 && isValid,
    dropHandlers: {
      onDragEnter: handleDragEnter,
      onDragLeave: handleDragLeave,
      onDragOver: handleDragOver,
      onDrop: handleDrop,
    },
  }
}
