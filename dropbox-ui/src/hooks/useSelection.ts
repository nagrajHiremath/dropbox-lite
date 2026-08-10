import { useCallback, useRef, useState } from 'react'

interface SelectOptions {
  /** ctrl/cmd+click - toggle this one item in/out of the current selection. */
  toggle?: boolean
  /** shift+click - select the contiguous range (in orderedIds order) from the last-clicked anchor. */
  range?: boolean
}

/**
 * Click/ctrl-click/shift-click selection model per DROPBOX_UI.md's file
 * browser UX section. Local to whichever page owns it (folder-scoped - the
 * caller clears it on navigation) rather than a global store; see
 * UI_IMPLEMENTATION_PLAN.md's UI-03 design notes for why.
 */
export function useSelection(orderedIds: string[]) {
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const anchorRef = useRef<string | null>(null)

  const select = useCallback(
    (id: string, options: SelectOptions = {}) => {
      if (options.range && anchorRef.current) {
        const from = orderedIds.indexOf(anchorRef.current)
        const to = orderedIds.indexOf(id)
        if (from !== -1 && to !== -1) {
          const [start, end] = from < to ? [from, to] : [to, from]
          setSelected(new Set(orderedIds.slice(start, end + 1)))
          return
        }
      }

      if (options.toggle) {
        setSelected((prev) => {
          const next = new Set(prev)
          if (next.has(id)) {
            next.delete(id)
          } else {
            next.add(id)
          }
          return next
        })
      } else {
        setSelected(new Set([id]))
      }

      anchorRef.current = id
    },
    [orderedIds],
  )

  const clear = useCallback(() => {
    setSelected(new Set())
    anchorRef.current = null
  }, [])

  const selectAll = useCallback(() => {
    setSelected(new Set(orderedIds))
    anchorRef.current = orderedIds[orderedIds.length - 1] ?? null
  }, [orderedIds])

  const isSelected = useCallback((id: string) => selected.has(id), [selected])

  return { selected, isSelected, select, clear, selectAll }
}
