import { VideoIcon } from 'lucide-react'
import { FilteredFilesView } from '@/components/files/FilteredFilesView'
import { filesByTypeKey } from '@/hooks/queryKeys'
import { listFilesByType } from '@/api/files'

export default function VideosPage() {
  return (
    <FilteredFilesView
      title="Videos"
      queryKey={filesByTypeKey('video')}
      queryFn={() => listFilesByType('video')}
      emptyIcon={VideoIcon}
      emptyTitle="No videos yet"
      emptyDescription="Video files you upload will show up here."
    />
  )
}
