import { useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { usePermanentlyDeleteFile } from '@/hooks/useFileMutations'
import { usePermanentlyDeleteFolder } from '@/hooks/useFolderMutations'
import { ApiError } from '@/api/client'
import type { BrowserEntry } from '@/lib/fileIcons'

interface PermanentDeleteDialogProps {
  entry: BrowserEntry | null
  onOpenChange: (open: boolean) => void
}

function errorMessage(err: unknown) {
  return err instanceof ApiError ? err.message : 'Something went wrong. Please try again.'
}

/** DROPBOX_UI.md: "Permanent Delete must require explicit confirmation" -
 * this is the app's one Highly-destructive-tier action, so unlike Move to
 * Trash (fires immediately, undo-toast as the safety net) this always
 * blocks on a confirm dialog with no undo. */
export function PermanentDeleteDialog({ entry, onOpenChange }: PermanentDeleteDialogProps) {
  const [error, setError] = useState<string | null>(null)
  const deleteFile = usePermanentlyDeleteFile()
  const deleteFolder = usePermanentlyDeleteFolder()
  const isPending = deleteFile.isPending || deleteFolder.isPending

  function handleOpenChange(next: boolean) {
    if (!next) {
      setError(null)
      onOpenChange(false)
    }
  }

  function handleConfirm() {
    if (!entry) return
    setError(null)
    const onSuccess = () => {
      toast.success('Permanently deleted')
      onOpenChange(false)
    }
    const onError = (err: unknown) => setError(errorMessage(err))

    if (entry.type === 'folder') {
      deleteFolder.mutate({ id: entry.folder.id }, { onSuccess, onError })
    } else {
      deleteFile.mutate({ id: entry.file.id }, { onSuccess, onError })
    }
  }

  const name = entry ? (entry.type === 'folder' ? entry.folder.name : entry.file.name) : ''

  return (
    <Dialog open={entry !== null} onOpenChange={handleOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Permanently delete {name}?</DialogTitle>
          <DialogDescription>This can&apos;t be undone.</DialogDescription>
        </DialogHeader>

        {error && <p className="text-destructive text-sm">{error}</p>}

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
            Cancel
          </Button>
          <Button type="button" variant="destructive" onClick={handleConfirm} disabled={isPending}>
            {isPending ? 'Deleting…' : 'Permanently delete'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
