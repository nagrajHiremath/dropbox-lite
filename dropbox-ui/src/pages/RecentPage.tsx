import { ClockIcon } from 'lucide-react'
import { FilteredFilesView } from '@/components/files/FilteredFilesView'
import { recentFilesKey } from '@/hooks/queryKeys'
import { listRecentFiles } from '@/api/files'

export default function RecentPage() {
  return (
    <FilteredFilesView
      title="Recent"
      queryKey={recentFilesKey}
      queryFn={() => listRecentFiles()}
      emptyIcon={ClockIcon}
      emptyTitle="No recent files"
      emptyDescription="Files you've recently added or edited will show up here."
    />
  )
}
