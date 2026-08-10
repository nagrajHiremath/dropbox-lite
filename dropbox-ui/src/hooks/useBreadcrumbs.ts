import { useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { getFolder, type Folder } from '@/api/folders'

const MAX_HOPS = 1000 // matches metadata-service's FolderService.MAX_ANCESTOR_HOPS guard

async function resolveChain(queryClient: QueryClient, folderId: string): Promise<Folder[]> {
  const chain: Folder[] = []
  let currentId: string | null = folderId
  let hops = 0

  while (currentId && hops < MAX_HOPS) {
    const folder: Folder = await queryClient.fetchQuery({
      queryKey: ['folder', currentId],
      queryFn: () => getFolder(currentId!),
      staleTime: 60_000,
    })
    chain.unshift(folder)
    currentId = folder.parentId
    hops++
  }

  return chain
}

/**
 * Resolves the ancestor chain (root-first, current folder last) for the
 * breadcrumb trail. A single query whose queryFn walks parentId upward via
 * queryClient.fetchQuery per hop - each hop checks the Query cache first
 * (the common case, since the user usually navigated down through these
 * folders already) and only falls back to a real GET /folders/{id} for a
 * cold/deep-linked URL. folderId undefined (root) short-circuits to [].
 */
export function useBreadcrumbs(folderId: string | undefined) {
  const queryClient = useQueryClient()

  return useQuery({
    queryKey: ['breadcrumbs', folderId],
    queryFn: () => resolveChain(queryClient, folderId!),
    enabled: !!folderId,
  })
}
