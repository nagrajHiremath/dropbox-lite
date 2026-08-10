import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  createFolder,
  moveFolder,
  permanentlyDeleteFolder,
  renameFolder,
  restoreFolder,
  trashFolder,
} from '@/api/folders'
import { foldersKey } from './queryKeys'

// UI-14: broad ['folders'] prefix (not just foldersKey(parentId)) for the
// trash-related mutations below - the trashed-folders view is a separate
// query (['folders','trashed']) over the same folders, so a parent-scoped
// invalidation alone would leave it stale. Same fix as UI-13's ['files']
// prefix broadening in useFileMutations.
const FOLDERS_PREFIX = ['folders']

export function useCreateFolder() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ name, parentId }: { name: string; parentId: string | undefined }) =>
      createFolder(name, parentId),
    onSuccess: (_folder, { parentId }) => {
      void queryClient.invalidateQueries({ queryKey: foldersKey(parentId) })
    },
  })
}

export function useRenameFolder() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, name }: { id: string; name: string; parentId: string | undefined }) =>
      renameFolder(id, name),
    onSuccess: (_folder, { id, parentId }) => {
      void queryClient.invalidateQueries({ queryKey: foldersKey(parentId) })
      void queryClient.invalidateQueries({ queryKey: ['folder', id] })
      void queryClient.invalidateQueries({ queryKey: ['breadcrumbs'] })
    },
  })
}

export function useMoveFolder() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      id,
      newParentId,
    }: {
      id: string
      oldParentId: string | undefined
      newParentId: string
    }) => moveFolder(id, newParentId),
    onSuccess: (_folder, { id, oldParentId, newParentId }) => {
      void queryClient.invalidateQueries({ queryKey: foldersKey(oldParentId) })
      void queryClient.invalidateQueries({ queryKey: foldersKey(newParentId) })
      void queryClient.invalidateQueries({ queryKey: ['folder', id] })
      void queryClient.invalidateQueries({ queryKey: ['breadcrumbs'] })
    },
  })
}

export function useTrashFolder() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id }: { id: string; parentId: string | undefined }) => trashFolder(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: FOLDERS_PREFIX })
    },
  })
}

export function useRestoreFolder() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id }: { id: string; parentId: string | undefined }) => restoreFolder(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: FOLDERS_PREFIX })
    },
  })
}

export function usePermanentlyDeleteFolder() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id }: { id: string }) => permanentlyDeleteFolder(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: FOLDERS_PREFIX })
    },
  })
}
