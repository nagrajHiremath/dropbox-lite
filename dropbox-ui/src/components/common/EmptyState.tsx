import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'

interface EmptyStateProps {
  icon?: LucideIcon
  title: string
  description?: string
  action?: ReactNode
}

/** DROPBOX_UI.md: "Never leave the user looking at a blank screen." Used for
 * empty folders, empty Trash, and empty Recent/Photos/Videos views - each
 * page supplies its own copy and (where applicable) an action slot. */
export function EmptyState({ icon: Icon, title, description, action }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-16 text-center">
      {Icon && (
        <div className="bg-muted flex size-16 items-center justify-center rounded-full">
          <Icon className="text-muted-foreground size-7" strokeWidth={1.5} aria-hidden="true" />
        </div>
      )}
      <div className="space-y-1">
        <p className="text-base font-semibold">{title}</p>
        {description && <p className="text-muted-foreground text-sm">{description}</p>}
      </div>
      {action}
    </div>
  )
}
