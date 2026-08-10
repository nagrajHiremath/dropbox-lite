import { type FormEvent, useEffect, useState } from 'react'
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
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useRenameFolder } from '@/hooks/useFolderMutations'
import { useRenameFile } from '@/hooks/useFileMutations'
import { ApiError } from '@/api/client'
import type { BrowserEntry } from '@/lib/fileIcons'

interface RenameDialogProps {
  entry: BrowserEntry | null
  onOpenChange: (open: boolean) => void
}

export function RenameDialog({ entry, onOpenChange }: RenameDialogProps) {
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const renameFolder = useRenameFolder()
  const renameFile = useRenameFile()
  const isPending = renameFolder.isPending || renameFile.isPending

  useEffect(() => {
    if (entry) {
      setName(entry.type === 'folder' ? entry.folder.name : entry.file.name)
      setError(null)
    }
  }, [entry])

  function handleOpenChange(next: boolean) {
    if (!next) onOpenChange(false)
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!entry) return
    setError(null)

    const onSuccess = () => {
      toast.success('Renamed')
      onOpenChange(false)
    }
    const onError = (err: unknown) => {
      setError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    }

    if (entry.type === 'folder') {
      renameFolder.mutate(
        { id: entry.folder.id, name, parentId: entry.folder.parentId ?? undefined },
        { onSuccess, onError },
      )
    } else {
      renameFile.mutate(
        { id: entry.file.id, name, folderId: entry.file.folderId ?? undefined },
        { onSuccess, onError },
      )
    }
  }

  return (
    <Dialog open={entry !== null} onOpenChange={handleOpenChange}>
      <DialogContent>
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Rename {entry?.type === 'folder' ? 'folder' : 'file'}</DialogTitle>
            <DialogDescription>Enter a new name.</DialogDescription>
          </DialogHeader>

          <div className="space-y-2 py-4">
            <Label htmlFor="rename-name">Name</Label>
            <Input
              id="rename-name"
              autoFocus
              required
              maxLength={255}
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
            {error && <p className="text-destructive text-sm">{error}</p>}
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={isPending}>
              {isPending ? 'Saving…' : 'Save'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
