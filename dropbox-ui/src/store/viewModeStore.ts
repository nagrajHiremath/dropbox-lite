import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export type ViewMode = 'list' | 'grid'

interface ViewModeState {
  mode: ViewMode
  setMode: (mode: ViewMode) => void
}

/** Shared List/Grid toggle across MyFilesPage and FilteredFilesView (Recent/Photos/Videos) -
 * persisted so the choice sticks across navigation and reloads, same pattern as authStore. */
export const useViewModeStore = create<ViewModeState>()(
  persist((set) => ({ mode: 'grid', setMode: (mode) => set({ mode }) }), {
    name: 'dropbox-view-mode',
  }),
)
