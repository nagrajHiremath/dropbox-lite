import { Skeleton } from '@/components/ui/skeleton'

interface ListSkeletonProps {
  rows?: number
}

/** Shown while the first page of a list query is loading (DROPBOX_UI.md's
 * "Skeleton loading for main file lists" - never a blank screen). */
export function ListSkeleton({ rows = 6 }: ListSkeletonProps) {
  return (
    <div className="space-y-2 p-6">
      {Array.from({ length: rows }).map((_, i) => (
        <Skeleton key={i} className="h-10 w-full" />
      ))}
    </div>
  )
}
