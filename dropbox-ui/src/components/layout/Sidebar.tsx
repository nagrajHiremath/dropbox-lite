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
import { useQuery } from '@tanstack/react-query'
import { cn } from '@/lib/utils'
import { useDropTarget } from '@/hooks/useDropTarget'
import { useDragMove } from '@/hooks/useDragMove'
import { Progress } from '@/components/ui/progress'
import { getStorageUsage } from '@/api/account'
import { storageUsageKey } from '@/hooks/queryKeys'
import { formatBytes } from '@/lib/format'
import { AccountMenu } from './AccountMenu'

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
    <aside className="bg-sidebar text-sidebar-foreground flex w-60 shrink-0 flex-col">
      <div className="flex h-16 items-center gap-2 border-b px-4 text-lg font-semibold">
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
                isActive
                  ? 'bg-sidebar-accent text-sidebar-accent-foreground font-semibold'
                  : 'text-sidebar-foreground/80 hover:bg-sidebar-accent/50 hover:text-sidebar-accent-foreground',
                to === '/files' && isOver && 'ring-primary bg-primary/10 ring-2',
              )
            }
          >
            <Icon className="size-4 shrink-0" aria-hidden="true" />
            {label}
          </NavLink>
        ))}
      </nav>

      <div className="mt-auto flex flex-col gap-3 border-t px-3 py-3">
        <StorageUsageSection />
        <AccountMenu />
      </div>
    </aside>
  )
}

/**
 * Upload/delete mutations explicitly invalidate storageUsageKey so this
 * refetches right away, but the number they'll get back can still be stale
 * for a few seconds: account-service only learns about the change once its
 * Kafka consumer processes the corresponding file-lifecycle event (the
 * publishing side polls its outbox every ~2s), which is inherently async
 * relative to the mutation resolving on the frontend. The refetchInterval
 * here is what catches it up automatically after that - not a replacement
 * for the explicit invalidations (which get it moving immediately), a
 * safety net for the propagation delay those alone can't close.
 */
function StorageUsageSection() {
  const { data } = useQuery({
    queryKey: storageUsageKey,
    queryFn: getStorageUsage,
    refetchInterval: 15_000,
  })
  if (!data) return null

  const percent =
    data.maxBytes > 0 ? Math.min(100, Math.round((data.usedBytes / data.maxBytes) * 100)) : 0

  return (
    <div className="px-2">
      <Progress value={percent} className="h-1.5" />
      <p className="text-muted-foreground mt-2 text-xs">
        {formatBytes(data.usedBytes)} of {formatBytes(data.maxBytes)} used
      </p>
    </div>
  )
}
