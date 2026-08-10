import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { ChevronRightIcon, FolderIcon, HomeIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { ScrollArea } from '@/components/ui/scroll-area'
import { listFolders } from '@/api/folders'
import { ApiError } from '@/api/client'
import { useMoveFolder } from '@/hooks/useFolderMutations'
import { useMoveFile } from '@/hooks/useFileMutations'
import { foldersKey } from '@/hooks/queryKeys'
import type { BrowserEntry } from '@/lib/fileIcons'

interface MoveDialogProps {
  entry: BrowserEntry | null
  onOpenChange: (open: boolean) => void
}

interface Crumb {
  id: string | undefined
  name: string
}

const ROOT_CRUMB: Crumb = { id: undefined, name: 'Home' }

export function MoveDialog({ entry, onOpenChange }: MoveDialogProps) {
  const [path, setPath] = useState<Crumb[]>([ROOT_CRUMB])
  const [error, setError] = useState<string | null>(null)
  const target = path[path.length - 1]

  const moveFolder = useMoveFolder()
  const moveFile = useMoveFile()
  const isPending = moveFolder.isPending || moveFile.isPending

  useEffect(() => {
    if (entry) {
      setPath([ROOT_CRUMB])
      setError(null)
    }
  }, [entry])

  const { data, isLoading } = useQuery({
    queryKey: foldersKey(target.id),
    queryFn: () => listFolders(target.id),
    enabled: entry !== null,
  })

  const isMovingFolder = entry?.type === 'folder'
  const currentLocationId = entry
    ? entry.type === 'folder'
      ? (entry.folder.parentId ?? undefined)
      : (entry.file.folderId ?? undefined)
    : undefined
  const movingFolderId = isMovingFolder ? entry.folder.id : undefined

  // Folders can't move to root in this MVP - UpdateFolderRequest's null
  // parentId means "leave unchanged," not "move to root" (see api/folders.ts).
  const rootDisabledForFolder = isMovingFolder && target.id === undefined
  const isSameLocation = target.id === currentLocationId
  const canMoveHere = !rootDisabledForFolder && !isSameLocation

  const subfolders = (data?.content ?? []).filter((f) => f.id !== movingFolderId)

  function handleOpenChange(next: boolean) {
    if (!next) onOpenChange(false)
  }

  function handleMoveHere() {
    if (!entry || !canMoveHere) return
    setError(null)
    const onSuccess = () => {
      toast.success('Moved')
      onOpenChange(false)
    }
    const onError = (err: unknown) => {
      setError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    }

    if (entry.type === 'folder') {
      if (!target.id) return // guarded by canMoveHere, but keeps TS happy
      moveFolder.mutate(
        { id: entry.folder.id, oldParentId: currentLocationId, newParentId: target.id },
        { onSuccess, onError },
      )
    } else {
      moveFile.mutate(
        { id: entry.file.id, oldFolderId: currentLocationId, newFolderId: target.id ?? null },
        { onSuccess, onError },
      )
    }
  }

  return (
    <Dialog open={entry !== null} onOpenChange={handleOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            Move {entry ? (entry.type === 'folder' ? entry.folder.name : entry.file.name) : ''}
          </DialogTitle>
          <DialogDescription>Choose a destination folder.</DialogDescription>
        </DialogHeader>

        <nav aria-label="Destination path" className="flex flex-wrap items-center gap-1 text-sm">
          {path.map((crumb, i) => (
            <span key={crumb.id ?? 'root'} className="flex items-center gap-1">
              {i > 0 && (
                <ChevronRightIcon className="text-muted-foreground size-3.5" aria-hidden="true" />
              )}
              <button
                type="button"
                onClick={() => setPath(path.slice(0, i + 1))}
                className="hover:text-foreground text-muted-foreground cursor-pointer transition-colors disabled:cursor-default disabled:pointer-events-none disabled:text-foreground"
                disabled={i === path.length - 1}
              >
                {i === 0 ? <HomeIcon className="size-4" aria-hidden="true" /> : crumb.name}
              </button>
            </span>
          ))}
        </nav>

        <ScrollArea className="h-56 rounded-md border">
          {isLoading ? (
            <p className="text-muted-foreground p-4 text-sm">Loading…</p>
          ) : subfolders.length === 0 ? (
            <p className="text-muted-foreground p-4 text-sm">No subfolders here.</p>
          ) : (
            <div className="p-1">
              {subfolders.map((folder) => (
                <button
                  key={folder.id}
                  type="button"
                  onClick={() => setPath([...path, { id: folder.id, name: folder.name }])}
                  className="hover:bg-accent flex w-full cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm transition-colors"
                >
                  <FolderIcon
                    className="text-muted-foreground size-4 shrink-0"
                    aria-hidden="true"
                  />
                  <span className="truncate">{folder.name}</span>
                </button>
              ))}
            </div>
          )}
        </ScrollArea>

        {rootDisabledForFolder && (
          <p className="text-muted-foreground text-xs">
            Folders can't be moved to Home in this version.
          </p>
        )}
        {error && <p className="text-destructive text-sm">{error}</p>}

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
            Cancel
          </Button>
          <Button type="button" onClick={handleMoveHere} disabled={!canMoveHere || isPending}>
            {isPending ? 'Moving…' : 'Move here'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
