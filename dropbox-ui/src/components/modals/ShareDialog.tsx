import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { CopyIcon, LinkIcon, Trash2Icon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
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
import { ScrollArea } from '@/components/ui/scroll-area'
import { listShares, type SharePermission } from '@/api/shares'
import { ApiError } from '@/api/client'
import { sharesKey } from '@/hooks/queryKeys'
import { useCreateShare, useRevokeShare } from '@/hooks/useShareMutations'
import { copyText } from '@/lib/clipboard'
import type { FileEntry } from '@/api/files'

interface ShareDialogProps {
  file: FileEntry | null
  onOpenChange: (open: boolean) => void
}

type ExpiryOption = 'never' | '1d' | '7d' | '30d'

const EXPIRY_DAYS: Record<Exclude<ExpiryOption, 'never'>, number> = { '1d': 1, '7d': 7, '30d': 30 }

function expiresAtFor(option: ExpiryOption): string | null {
  if (option === 'never') return null
  const days = EXPIRY_DAYS[option]
  return new Date(Date.now() + days * 24 * 60 * 60 * 1000).toISOString()
}

function formatDateTime(iso: string) {
  return new Date(iso).toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  })
}

function permissionLabel(permission: string) {
  return permission === 'DOWNLOAD' ? 'Can download' : 'Can view'
}

function errorMessage(err: unknown) {
  return err instanceof ApiError ? err.message : 'Something went wrong. Please try again.'
}

const selectClassName =
  'h-8 w-full min-w-0 rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30'

export function ShareDialog({ file, onOpenChange }: ShareDialogProps) {
  const [permission, setPermission] = useState<SharePermission>('VIEW')
  const [expiryOption, setExpiryOption] = useState<ExpiryOption>('7d')
  const [createdLink, setCreatedLink] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const createShareMutation = useCreateShare()
  const revokeShareMutation = useRevokeShare()

  useEffect(() => {
    if (file) {
      setPermission('VIEW')
      setExpiryOption('7d')
      setCreatedLink(null)
      setError(null)
    }
  }, [file])

  const { data, isLoading, isError } = useQuery({
    queryKey: sharesKey(file?.id ?? ''),
    queryFn: () => listShares(file!.id),
    enabled: file !== null,
  })

  const shares = [...(data ?? [])].sort((a, b) => b.createdAt.localeCompare(a.createdAt))

  function handleOpenChange(next: boolean) {
    if (!next) onOpenChange(false)
  }

  function handleCreate() {
    if (!file) return
    setError(null)
    createShareMutation.mutate(
      { fileId: file.id, request: { permission, expiresAt: expiresAtFor(expiryOption) } },
      {
        onSuccess: (created) => {
          setCreatedLink(`${window.location.origin}/s/${created.token}`)
        },
        onError: (err) => setError(errorMessage(err)),
      },
    )
  }

  function handleCopy(link: string) {
    void copyText(link).then((ok) =>
      ok ? toast.success('Link copied') : toast.error('Could not copy link'),
    )
  }

  function handleRevoke(shareId: string) {
    if (!file) return
    revokeShareMutation.mutate(
      { shareId, fileId: file.id },
      {
        onSuccess: () => toast.success('Share revoked'),
        onError: (err) => toast.error(errorMessage(err)),
      },
    )
  }

  return (
    <Dialog open={file !== null} onOpenChange={handleOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Share</DialogTitle>
          <DialogDescription>{file?.name}</DialogDescription>
        </DialogHeader>

        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="share-permission">Permission</Label>
            <select
              id="share-permission"
              className={selectClassName}
              value={permission}
              onChange={(e) => setPermission(e.target.value as SharePermission)}
            >
              <option value="VIEW">Can view</option>
              <option value="DOWNLOAD">Can download</option>
            </select>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="share-expiry">Expires</Label>
            <select
              id="share-expiry"
              className={selectClassName}
              value={expiryOption}
              onChange={(e) => setExpiryOption(e.target.value as ExpiryOption)}
            >
              <option value="1d">In 1 day</option>
              <option value="7d">In 7 days</option>
              <option value="30d">In 30 days</option>
              <option value="never">Never</option>
            </select>
          </div>
        </div>

        {error && <p className="text-destructive text-sm">{error}</p>}

        <Button
          type="button"
          onClick={handleCreate}
          disabled={createShareMutation.isPending}
          className="w-full"
        >
          <LinkIcon className="size-4" />
          {createShareMutation.isPending ? 'Creating link…' : 'Create link'}
        </Button>

        {createdLink && (
          <div className="bg-muted space-y-1.5 rounded-lg border p-3">
            <p className="text-xs font-medium">
              Copy this link now — you won't be able to see it again.
            </p>
            <div className="flex gap-2">
              <Input readOnly value={createdLink} onFocus={(e) => e.target.select()} />
              <Button
                type="button"
                variant="outline"
                size="icon"
                onClick={() => handleCopy(createdLink)}
                aria-label="Copy link"
              >
                <CopyIcon className="size-4" />
              </Button>
            </div>
          </div>
        )}

        <div className="space-y-1.5">
          <Label>Existing links</Label>
          <ScrollArea className="h-40 rounded-md border">
            {isLoading ? (
              <p className="text-muted-foreground p-4 text-sm">Loading…</p>
            ) : isError ? (
              <p className="text-destructive p-4 text-sm">Couldn't load shares.</p>
            ) : shares.length === 0 ? (
              <p className="text-muted-foreground p-4 text-sm">No share links yet.</p>
            ) : (
              <div className="divide-y">
                {shares.map((share) => {
                  const isRevoked = share.status === 'REVOKED'
                  const isExpired =
                    !isRevoked && !!share.expiresAt && new Date(share.expiresAt) < new Date()
                  return (
                    <div key={share.id} className="flex items-center gap-3 px-3 py-2 text-sm">
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <span className="font-medium">{permissionLabel(share.permission)}</span>
                          {isRevoked && <Badge variant="destructive">Revoked</Badge>}
                          {isExpired && <Badge variant="secondary">Expired</Badge>}
                        </div>
                        <p className="text-muted-foreground">
                          {share.expiresAt
                            ? `${isExpired ? 'Expired' : 'Expires'} ${formatDateTime(share.expiresAt)}`
                            : 'No expiry'}
                        </p>
                      </div>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        disabled={isRevoked || isExpired || revokeShareMutation.isPending}
                        onClick={() => handleRevoke(share.id)}
                        aria-label="Revoke link"
                      >
                        <Trash2Icon className="size-4" />
                      </Button>
                    </div>
                  )
                })}
              </div>
            )}
          </ScrollArea>
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
            Close
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
