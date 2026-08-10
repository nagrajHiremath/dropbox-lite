import { create } from 'zustand'

export interface DraggedEntry {
  id: string
  type: 'folder' | 'file'
  /** The item's current parent/folder id, undefined for root - used by drop
   * targets to skip highlighting a no-op (dropping something where it
   * already lives). */
  currentParentId: string | undefined
}

interface DragState {
  dragged: DraggedEntry | null
  setDragged: (entry: DraggedEntry) => void
  clearDragged: () => void
}

/**
 * UI-07: HTML5 DnD's dataTransfer.getData() is only readable on the actual
 * `drop` event, not during dragover/dragenter (a real browser restriction) -
 * so live valid/invalid target styling *during* a drag needs the dragged
 * item's identity from somewhere other than dataTransfer. Set once at
 * dragstart (EntryRow), read by every potential drop target while dragging,
 * cleared at dragend.
 */
export const useDragStore = create<DragState>()((set) => ({
  dragged: null,
  setDragged: (entry) => set({ dragged: entry }),
  clearDragged: () => set({ dragged: null }),
}))
