import type { ReactNode } from 'react'
import { CheckSquareIcon, Trash2Icon, XIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { Breadcrumbs, type BreadcrumbItem } from '@/components/layout/Breadcrumbs'

interface PageHeaderProps {
  /** Replaces a plain title - the current/last crumb already reads as the
   * page's title (see Breadcrumbs' isLast styling), and navigation context
   * makes a separately-shown folder name redundant. */
  breadcrumbs: BreadcrumbItem[]
  children?: ReactNode
  selection?: {
    count: number
    onClear: () => void
    onSelectAll: () => void
    onTrashSelected: () => void
  }
}

/**
 * Fixed-height (h-16), relatively-positioned slot whose two possible
 * contents (normal breadcrumbs/actions vs. the bulk-selection toolbar) are
 * absolutely stacked and swapped via opacity, not mounted/unmounted as
 * siblings. Previously SelectionToolbar was a separate element inserted
 * above the file list, so selecting the first item added a whole new row of
 * height and shoved every row below it down - "layout shift on selection."
 * This way the slot's box height never changes, so nothing below it ever
 * moves regardless of selection state.
 *
 * This is also the app's only top bar now - it used to sit below a separate
 * breadcrumbs-only TopBar; the two were merged into one row since navigation
 * (breadcrumbs) already conveys the current location without a second,
 * redundant title above it.
 */
export function PageHeader({ breadcrumbs, children, selection }: PageHeaderProps) {
  const showSelection = !!selection && selection.count > 0

  return (
    <div className="relative h-16 shrink-0 bg-muted">
      <div
        className={cn(
          'absolute inset-0 flex items-center justify-between px-6 transition-opacity',
          showSelection && 'pointer-events-none opacity-0',
        )}
        aria-hidden={showSelection}
      >
        <Breadcrumbs items={breadcrumbs} />
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
