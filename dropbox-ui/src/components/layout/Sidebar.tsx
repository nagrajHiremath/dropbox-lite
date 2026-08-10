import {
  ClockIcon,
  FolderIcon,
  ImageIcon,
  PackageIcon,
  Trash2Icon,
  VideoIcon,
  type LucideIcon,
} from 'lucide-react'
import { NavLink } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { useDropTarget } from '@/hooks/useDropTarget'
import { useDragMove } from '@/hooks/useDragMove'

interface NavItem {
  to: string
  label: string
  icon: LucideIcon
}

const NAV_ITEMS: NavItem[] = [
  { to: '/files', label: 'My Files', icon: FolderIcon },
  { to: '/recent', label: 'Recent', icon: ClockIcon },
  { to: '/photos', label: 'Photos', icon: ImageIcon },
  { to: '/videos', label: 'Videos', icon: VideoIcon },
  { to: '/trash', label: 'Trash', icon: Trash2Icon },
]

export default function Sidebar() {
  const moveDraggedTo = useDragMove()

  // Only "My Files" (root) is a drop target - Recent/Photos/Videos are
  // computed views, not real folders, and Trash is deliberately excluded
  // (UI-07 §5: blurring move/trash semantics via drag risks an accidental
  // destructive action). Files-only per the UI-04 root-move finding (folders
  // can't move to root in this MVP).
  const { isOver, dropHandlers } = useDropTarget(
    (dragged) => dragged.type === 'file' && dragged.currentParentId !== undefined,
    (dragged) => moveDraggedTo(dragged, undefined, 'My Files'),
  )

  return (
    <aside className="bg-sidebar text-sidebar-foreground flex w-60 shrink-0 flex-col border-r">
      <div className="flex h-14 items-center gap-2 border-b px-4 text-lg font-semibold">
        <div className="bg-primary text-primary-foreground flex size-7 shrink-0 items-center justify-center rounded-lg">
          <PackageIcon className="size-4" aria-hidden="true" />
        </div>
        Dropbox Lite
      </div>

      <nav className="flex flex-col gap-0.5 px-3 py-3" aria-label="Main">
        {NAV_ITEMS.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            {...(to === '/files' ? dropHandlers : {})}
            className={({ isActive }) =>
              cn(
                'relative flex items-center gap-2.5 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                'before:absolute before:inset-y-1.5 before:left-0 before:w-0.5 before:rounded-full before:bg-primary before:transition-opacity before:content-[""]',
                isActive
                  ? 'bg-sidebar-accent text-sidebar-accent-foreground font-semibold before:opacity-100'
                  : 'text-sidebar-foreground/80 hover:bg-sidebar-accent/50 hover:text-sidebar-accent-foreground before:opacity-0',
                to === '/files' && isOver && 'ring-primary bg-primary/10 ring-2',
              )
            }
          >
            <Icon className="size-4 shrink-0" aria-hidden="true" />
            {label}
          </NavLink>
        ))}
      </nav>
    </aside>
  )
}
