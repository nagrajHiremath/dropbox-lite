import { ImageIcon } from 'lucide-react'
import { FilteredFilesView } from '@/components/files/FilteredFilesView'
import { filesByTypeKey } from '@/hooks/queryKeys'
import { listFilesByType } from '@/api/files'

export default function PhotosPage() {
  return (
    <FilteredFilesView
      title="Photos"
      queryKey={filesByTypeKey('image')}
      queryFn={() => listFilesByType('image')}
      emptyIcon={ImageIcon}
      emptyTitle="No photos yet"
      emptyDescription="Image files you upload will show up here."
    />
  )
}
