import { useEffect } from 'react'

/**
 * Ctrl/Cmd+A selects everything in the current view - covers Grid view too,
 * which has no header checkbox to click (see SelectAllCheckbox's doc
 * comment). Ignored while focus is inside a text input/textarea/
 * contenteditable, or anywhere within an open dialog (Radix traps focus
 * into dialog content on open, so this catches dialogs with no input too,
 * e.g. DetailsDialog/FilePreviewDialog) - otherwise it'd hijack normal text
 * selection or fire invisibly behind an open modal.
 */
export function useSelectAllShortcut(selectAll: () => void) {
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key.toLowerCase() !== 'a' || !(e.ctrlKey || e.metaKey)) return

      const active = document.activeElement as HTMLElement | null
      if (active?.closest('input, textarea, [contenteditable="true"], [role="dialog"]')) return

      e.preventDefault()
      selectAll()
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [selectAll])
}
