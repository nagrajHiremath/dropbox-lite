import { useMutation, useQueryClient } from '@tanstack/react-query'
import { moveFile, permanentlyDeleteFile, renameFile, restoreFile, trashFile } from '@/api/files'

// UI-13: broad ['files'] prefix rather than filesKey(folderId) specifically -
// Recent/Photos/Videos are separate queries (['files','recent'],
// ['files','type',...]) over the same underlying files, so a folder-scoped
// invalidation alone would leave those views stale after a rename/move/
// trash/restore. Same "broad is simpler/safer than precise" precedent as
// uploadQueueStore's UI-08 fix.
const FILES_PREFIX = ['files']

export function useRenameFile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, name }: { id: string; name: string; folderId: string | undefined }) =>
      renameFile(id, name),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: FILES_PREFIX })
    },
  })
}

export function useMoveFile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      id,
      newFolderId,
    }: {
      id: string
      oldFolderId: string | undefined
      newFolderId: string | null
    }) => moveFile(id, newFolderId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: FILES_PREFIX })
    },
  })
}

export function useTrashFile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id }: { id: string; folderId: string | undefined }) => trashFile(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: FILES_PREFIX })
    },
  })
}

export function useRestoreFile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id }: { id: string; folderId: string | undefined }) => restoreFile(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: FILES_PREFIX })
    },
  })
}

export function usePermanentlyDeleteFile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id }: { id: string }) => permanentlyDeleteFile(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: FILES_PREFIX })
    },
  })
}
