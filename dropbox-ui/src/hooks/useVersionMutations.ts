import { useMutation, useQueryClient } from '@tanstack/react-query'
import { restoreVersion } from '@/api/versions'
import { versionsKey } from './queryKeys'

/** Restore is non-destructive on the backend - it creates a new version
 * pointing at the restored content rather than deleting history, so both
 * the version list and the file's own currentVersionId/size/updatedAt
 * change. Invalidates the broad ['files'] prefix (not just filesKey(folderId))
 * for the same reason useFileMutations does - UI-13's Recent view sorts by
 * updatedAt, so a restore needs to refresh it too. */
export function useRestoreVersion() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      fileId,
      versionId,
    }: {
      fileId: string
      versionId: string
      folderId: string | undefined
    }) => restoreVersion(fileId, versionId),
    onSuccess: (_version, { fileId }) => {
      void queryClient.invalidateQueries({ queryKey: versionsKey(fileId) })
      void queryClient.invalidateQueries({ queryKey: ['files'] })
    },
  })
}
