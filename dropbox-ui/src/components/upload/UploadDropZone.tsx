import { useCallback, useState, type DragEvent, type ReactNode } from 'react'
import { UploadIcon } from 'lucide-react'
import { extractDroppedEntries } from '@/lib/dropTree'

interface UploadDropZoneProps {
  /** Raw FileSystemEntry list, not flat File[] - callers walk it (via
   * lib/dropTree's buildDropTree) to preserve any dropped folder structure
   * instead of losing it the way dataTransfer.files would. */
  onDropEntries: (entries: FileSystemEntry[]) => void
  /** Render-prop form lets callers react to isDragging too (e.g. intensifying
   * a permanent empty-folder drop target) - see MyFilesPage/EmptyFolderDropZone. */
  children: ReactNode | ((isDragging: boolean) => ReactNode)
}

/**
 * DROPBOX_UI.md: "Dragging files from OS -> clear upload drop zone." Uses a
 * counter (not a boolean) for enter/leave tracking - dragenter/dragleave fire
 * for every descendant boundary crossing too, not just this container, so a
 * naive boolean toggle flickers every time the pointer crosses a child
 * element. Counting nets out correctly since both events bubble here from
 * any descendant.
 */
export function UploadDropZone({ onDropEntries, children }: UploadDropZoneProps) {
  const [dragCount, setDragCount] = useState(0)
  const isDragging = dragCount > 0

  const handleDragEnter = useCallback((e: DragEvent) => {
    e.preventDefault()
    if (e.dataTransfer.types.includes('Files')) {
      setDragCount((c) => c + 1)
    }
  }, [])

  const handleDragLeave = useCallback((e: DragEvent) => {
    e.preventDefault()
    setDragCount((c) => Math.max(0, c - 1))
  }, [])

  const handleDragOver = useCallback((e: DragEvent) => {
    e.preventDefault() // required for onDrop to fire at all
  }, [])

  const handleDrop = useCallback(
    (e: DragEvent) => {
      e.preventDefault()
      setDragCount(0)
      // Extracted synchronously here, not inside the async tree walk that
      // consumes them - see extractDroppedEntries's doc comment.
      const entries = extractDroppedEntries(e.dataTransfer.items)
      if (entries.length > 0) onDropEntries(entries)
    },
    [onDropEntries],
  )

  return (
    <div
      className="relative h-full"
      onDragEnter={handleDragEnter}
      onDragLeave={handleDragLeave}
      onDragOver={handleDragOver}
      onDrop={handleDrop}
    >
      {typeof children === 'function' ? children(isDragging) : children}
      {isDragging && (
        <div className="border-primary bg-primary/5 pointer-events-none absolute inset-2 z-40 flex items-center justify-center rounded-lg border-2 border-dashed">
          <div className="flex flex-col items-center gap-2 text-center">
            <UploadIcon className="text-primary size-8" aria-hidden="true" />
            <p className="text-primary text-sm font-medium">Drop files to upload</p>
          </div>
        </div>
      )}
    </div>
  )
}
