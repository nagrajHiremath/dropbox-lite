import { useEffect, useState } from 'react'
import { DownloadIcon, FileIcon, Share2Icon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { fetchFileBlob } from '@/lib/download'
import { formatBytes } from '@/lib/format'
import { useDownloadQueueStore } from '@/store/downloadQueueStore'
import type { FileEntry } from '@/api/files'

interface FilePreviewDialogProps {
  file: FileEntry | null
  onOpenChange: (open: boolean) => void
  /** Optional - only MyFilesPage/FilteredFilesView (which own an ActiveDialog
   * state to transition into) pass this; omit it to hide the Share button
   * rather than render one that does nothing. */
  onRequestShare?: () => void
}

type PreviewKind = 'image' | 'pdf' | 'video' | 'text' | 'none'

// Sanity cap so an accidentally-huge text file doesn't get rendered in full
// into the DOM - this is a preview, not a text editor.
const TEXT_PREVIEW_LIMIT = 200_000

function previewKindFor(mimeType: string | null): PreviewKind {
  const type = mimeType ?? ''
  if (type.startsWith('image/')) return 'image'
  if (type === 'application/pdf') return 'pdf'
  if (type.startsWith('video/')) return 'video'
  if (type.startsWith('text/')) return 'text'
  return 'none'
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

function NoPreview() {
  return (
    <div className="text-muted-foreground flex flex-col items-center gap-2 py-12 text-center">
      <FileIcon className="size-8" strokeWidth={1.5} aria-hidden="true" />
      <p className="text-sm">No preview available for this file type.</p>
    </div>
  )
}

/**
 * Image/PDF/video preview via an authenticated blob fetched into an object
 * URL (see lib/download.ts - a plain <img>/<iframe>/<video src> can't send
 * the Bearer header FileDownloadController requires); text/plain fetches
 * the same blob's text() instead of an object URL. Everything else - and
 * any load failure - falls back to the same NoPreview + Download state
 * (deliberately not a half-built per-type viewer). This is also the surface
 * a double-click / "Preview" menu item opens, so it doubles as the file's
 * metadata view (name/type/size/modified) alongside the preview itself.
 */
export function FilePreviewDialog({ file, onOpenChange, onRequestShare }: FilePreviewDialogProps) {
  const [mediaUrl, setMediaUrl] = useState<string | null>(null)
  const [textContent, setTextContent] = useState<{ text: string; truncated: boolean } | null>(null)
  const [loading, setLoading] = useState(false)
  const [loadError, setLoadError] = useState(false)
  const enqueueDownload = useDownloadQueueStore((s) => s.enqueue)

  const kind = previewKindFor(file?.mimeType ?? null)

  useEffect(() => {
    setMediaUrl(null)
    setTextContent(null)
    setLoadError(false)

    if (!file || kind === 'none') {
      setLoading(false)
      return
    }

    setLoading(true)
    let cancelled = false
    let objectUrl: string | null = null

    fetchFileBlob(file.id)
      .then(async (blob) => {
        if (cancelled) return
        if (kind === 'text') {
          const full = await blob.text()
          const truncated = full.length > TEXT_PREVIEW_LIMIT
          setTextContent({ text: truncated ? full.slice(0, TEXT_PREVIEW_LIMIT) : full, truncated })
        } else {
          objectUrl = URL.createObjectURL(blob)
          setMediaUrl(objectUrl)
        }
      })
      .catch(() => {
        if (!cancelled) setLoadError(true)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [file, kind])

  function handleOpenChange(next: boolean) {
    if (!next) onOpenChange(false)
  }

  function handleDownload() {
    if (!file || file.sizeBytes == null) return
    enqueueDownload({
      fileId: file.id,
      fileName: file.name,
      mimeType: file.mimeType,
      totalBytes: file.sizeBytes,
    })
  }

  const isFullBleed = kind === 'pdf' || kind === 'text'

  return (
    <Dialog open={file !== null} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle className="truncate">{file?.name}</DialogTitle>
        </DialogHeader>

        {file && (
          <p className="text-muted-foreground -mt-2 text-xs">
            {file.mimeType ?? 'Unknown type'}
            {' · '}
            {file.sizeBytes != null ? formatBytes(file.sizeBytes) : 'Unknown size'}
            {' · '}
            Modified {formatDateTime(file.updatedAt)}
          </p>
        )}

        <div
          className={
            isFullBleed
              ? 'overflow-hidden rounded-lg border'
              : 'bg-muted flex min-h-56 items-center justify-center overflow-hidden rounded-lg'
          }
        >
          {kind === 'none' || loadError ? (
            <NoPreview />
          ) : loading ? (
            <Skeleton className="h-56 w-full" />
          ) : kind === 'image' && mediaUrl ? (
            <img
              src={mediaUrl}
              alt={file?.name}
              className="max-h-[60vh] max-w-full object-contain"
            />
          ) : kind === 'pdf' && mediaUrl ? (
            <iframe src={mediaUrl} title={file?.name} className="h-[60vh] w-full" />
          ) : kind === 'video' && mediaUrl ? (
            <video src={mediaUrl} controls className="max-h-[60vh] max-w-full" />
          ) : kind === 'text' && textContent ? (
            <div className="max-h-[60vh] overflow-auto p-3">
              <pre className="text-foreground text-left text-xs break-words whitespace-pre-wrap">
                {textContent.text}
              </pre>
              {textContent.truncated && (
                <p className="text-muted-foreground mt-2 text-xs">
                  Preview truncated — download to see the full file.
                </p>
              )}
            </div>
          ) : (
            <NoPreview />
          )}
        </div>

        <DialogFooter className="sm:justify-between">
          <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
            Close
          </Button>
          <div className="flex gap-2">
            {onRequestShare && (
              <Button type="button" variant="outline" onClick={onRequestShare}>
                <Share2Icon className="size-4" />
                Share
              </Button>
            )}
            <Button type="button" onClick={handleDownload}>
              <DownloadIcon className="size-4" />
              Download
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
