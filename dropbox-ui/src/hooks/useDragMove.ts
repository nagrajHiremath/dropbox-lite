import { toast } from 'sonner'
import { useMoveFolder } from './useFolderMutations'
import { useMoveFile } from './useFileMutations'
import type { DraggedEntry } from '@/store/dragStore'

function errorMessage(err: unknown) {
  return err instanceof Error ? err.message : 'Something went wrong. Please try again.'
}

/**
 * Shared by every drop target (EntryRow folder rows, Sidebar's "My Files",
 * Breadcrumbs items) so the move-on-drop + Undo-toast logic lives in one
 * place instead of being duplicated three times - each target only needs to
 * know its own folder id/label, not re-implement the mutation dispatch.
 * targetFolderId undefined means root (only ever reachable for files - a
 * folder-type drag targeting root is already filtered out by each target's
 * own computeValidity before this ever runs).
 */
export function useDragMove() {
  const moveFolder = useMoveFolder()
  const moveFile = useMoveFile()

  return (dragged: DraggedEntry, targetFolderId: string | undefined, targetLabel: string) => {
    if (dragged.type === 'folder') {
      if (!targetFolderId) return // guarded by validity upstream, keeps this safe regardless
      const originalParentId = dragged.currentParentId
      moveFolder.mutate(
        { id: dragged.id, oldParentId: originalParentId, newParentId: targetFolderId },
        {
          onSuccess: () => {
            // No Undo offered if the folder came from root - there's no API
            // way to move a folder back to root (UI-04's root-move finding),
            // so an Undo button here would be unable to actually undo it.
            toast.success(
              `Moved to ${targetLabel}`,
              originalParentId
                ? {
                    action: {
                      label: 'Undo',
                      onClick: () =>
                        moveFolder.mutate({
                          id: dragged.id,
                          oldParentId: targetFolderId,
                          newParentId: originalParentId,
                        }),
                    },
                  }
                : undefined,
            )
          },
          onError: (err) => toast.error(errorMessage(err)),
        },
      )
    } else {
      const originalFolderId = dragged.currentParentId
      moveFile.mutate(
        { id: dragged.id, oldFolderId: originalFolderId, newFolderId: targetFolderId ?? null },
        {
          onSuccess: () => {
            toast.success(`Moved to ${targetLabel}`, {
              action: {
                label: 'Undo',
                onClick: () =>
                  moveFile.mutate({
                    id: dragged.id,
                    oldFolderId: targetFolderId,
                    newFolderId: originalFolderId ?? null,
                  }),
              },
            })
          },
          onError: (err) => toast.error(errorMessage(err)),
        },
      )
    }
  }
}
