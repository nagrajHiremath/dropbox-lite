import { useLocation, useParams } from 'react-router-dom'
import { useBreadcrumbs } from '@/hooks/useBreadcrumbs'
import { Breadcrumbs, type BreadcrumbItem } from './Breadcrumbs'
import { AccountMenu } from './AccountMenu'

const SECTION_LABELS: Record<string, string> = {
  '/files': 'My Files',
  '/recent': 'Recent',
  '/photos': 'Photos',
  '/videos': 'Videos',
  '/trash': 'Trash',
}

/**
 * UI-03: for the /files* section, reads :folderId off the active nested
 * route (useParams reads from the whole matched route branch, so this works
 * from a layout component even though TopBar isn't the routed element
 * itself) and resolves the real ancestor chain via useBreadcrumbs. Every
 * other section still gets the UI-02 single-label placeholder since they
 * have no folder hierarchy.
 */
export default function TopBar() {
  const location = useLocation()
  const params = useParams<{ folderId?: string }>()
  const topLevelPath = '/' + (location.pathname.split('/')[1] ?? '')
  const isFilesSection = topLevelPath === '/files'

  const { data: chain } = useBreadcrumbs(isFilesSection ? params.folderId : undefined)

  const items: BreadcrumbItem[] = isFilesSection
    ? [
        { label: 'My Files', to: '/files', isFolderCrumb: true, folderId: undefined },
        ...(chain ?? []).map((f) => ({
          label: f.name,
          to: `/files/${f.id}`,
          isFolderCrumb: true,
          folderId: f.id,
        })),
      ]
    : [{ label: SECTION_LABELS[topLevelPath] ?? 'My Files' }]

  return (
    <header className="flex h-14 shrink-0 items-center justify-between border-b px-4">
      <Breadcrumbs items={items} />
      <AccountMenu />
    </header>
  )
}
