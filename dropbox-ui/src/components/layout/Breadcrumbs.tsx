import { ChevronRightIcon } from 'lucide-react'
import { Link } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { useDropTarget } from '@/hooks/useDropTarget'
import { useDragMove } from '@/hooks/useDragMove'

export interface BreadcrumbItem {
  label: string
  /** Omit on the current/last crumb - it renders as plain (non-clickable) text. */
  to?: string
  /** UI-07: true for real folder crumbs (My Files + resolved chain), eligible
   * as a drop target (except the last/current one). Omitted for non-folder
   * section labels (Recent, Trash, etc) which aren't drop targets at all. */
  isFolderCrumb?: boolean
  /** The folder this crumb represents - undefined means root. Only meaningful when isFolderCrumb is true. */
  folderId?: string
}

/**
 * Purely presentational for the label/link rendering (UI-02/03); UI-07 adds
 * drop-target behavior to non-last folder crumbs, reusing the same
 * useDropTarget/useDragMove every other drop target (EntryRow, Sidebar) uses.
 */
export function Breadcrumbs({ items }: { items: BreadcrumbItem[] }) {
  return (
    <nav aria-label="Breadcrumb" className="flex min-w-0 items-center gap-1 text-sm">
      {items.map((item, i) => (
        <Crumb
          key={`${item.label}-${i}`}
          item={item}
          isLast={i === items.length - 1}
          isFirst={i === 0}
        />
      ))}
    </nav>
  )
}

function Crumb({
  item,
  isLast,
  isFirst,
}: {
  item: BreadcrumbItem
  isLast: boolean
  isFirst: boolean
}) {
  const moveDraggedTo = useDragMove()
  const canBeDropTarget = item.isFolderCrumb && !isLast

  const { isOver, dropHandlers } = useDropTarget(
    (dragged) => {
      if (!canBeDropTarget) return false
      if (dragged.id === item.folderId) return false // can't drop a folder onto itself
      if (dragged.currentParentId === item.folderId) return false // already here, no-op
      if (!item.folderId && dragged.type === 'folder') return false // folders can't move to root (UI-04 finding)
      return true
    },
    (dragged) => moveDraggedTo(dragged, item.folderId, item.label),
  )

  return (
    <span className="flex min-w-0 items-center gap-1">
      {!isFirst && (
        <ChevronRightIcon className="text-muted-foreground size-3.5 shrink-0" aria-hidden="true" />
      )}
      {item.to && !isLast ? (
        <Link
          to={item.to}
          {...(canBeDropTarget ? dropHandlers : {})}
          className={cn(
            'text-muted-foreground hover:text-foreground -mx-1 truncate rounded px-1 transition-colors',
            isOver && 'ring-primary bg-primary/10 text-foreground ring-2',
          )}
        >
          {item.label}
        </Link>
      ) : (
        <span className={isLast ? 'truncate font-medium' : 'text-muted-foreground truncate'}>
          {item.label}
        </span>
      )}
    </span>
  )
}
