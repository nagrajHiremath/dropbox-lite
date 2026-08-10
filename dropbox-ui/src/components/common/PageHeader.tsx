import type { ReactNode } from 'react'
import { CheckSquareIcon, Trash2Icon, XIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

interface PageHeaderProps {
  title: string
  children?: ReactNode
  selection?: {
    count: number
    onClear: () => void
    onSelectAll: () => void
    onTrashSelected: () => void
  }
}

/**
 * Fixed-height (h-14), relatively-positioned slot whose two possible
 * contents (normal title/actions vs. the bulk-selection toolbar) are
 * absolutely stacked and swapped via opacity, not mounted/unmounted as
 * siblings. Previously SelectionToolbar was a separate element inserted
 * above the file list, so selecting the first item added a whole new row of
 * height and shoved every row below it down - "layout shift on selection."
 * This way the slot's box height never changes, so nothing below it ever
 * moves regardless of selection state.
 */
export function PageHeader({ title, children, selection }: PageHeaderProps) {
  const showSelection = !!selection && selection.count > 0

  return (
    <div className="relative h-14 shrink-0 border-b">
      <div
        className={cn(
          'absolute inset-0 flex items-center justify-between px-6 transition-opacity',
          showSelection && 'pointer-events-none opacity-0',
        )}
        aria-hidden={showSelection}
      >
        <h1 className="text-lg font-semibold">{title}</h1>
        {children && <div className="flex items-center gap-2">{children}</div>}
      </div>

      {selection && (
        <div
          className={cn(
            'bg-accent/50 absolute inset-0 flex items-center gap-3 px-6 transition-opacity',
            !showSelection && 'pointer-events-none opacity-0',
          )}
          aria-hidden={!showSelection}
        >
          <Button
            variant="ghost"
            size="icon"
            className="size-6"
            onClick={selection.onClear}
            aria-label="Clear selection"
          >
            <XIcon className="size-4" />
          </Button>
          <span className="text-sm font-medium">{selection.count} selected</span>
          <Button variant="ghost" size="sm" onClick={selection.onSelectAll}>
            <CheckSquareIcon className="size-4" />
            Select all
          </Button>
          <Button
            variant="ghost"
            size="sm"
            className="text-destructive hover:text-destructive ml-auto"
            onClick={selection.onTrashSelected}
          >
            <Trash2Icon className="size-4" />
            Move to Trash
          </Button>
        </div>
      )}
    </div>
  )
}
