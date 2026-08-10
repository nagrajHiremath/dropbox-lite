import { UploadIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

interface EmptyFolderDropZoneProps {
  isDragging: boolean
  onUploadClick: () => void
}

/**
 * DROPBOX_UI.md-style "real Dropbox" empty folder - a permanent (not just
 * on-drag) dashed drop target rather than plain icon+text, since MyFilesPage
 * is the only view files can actually be dropped/uploaded into (Recent/
 * Photos/Videos/Trash keep the plain EmptyState). Drop handling itself
 * belongs to the ancestor UploadDropZone the whole page is wrapped in - the
 * drop event just bubbles there - this only needs its own click-to-upload
 * and the intensified styling while isDragging (passed down from that same
 * UploadDropZone via its render-prop children).
 */
export function EmptyFolderDropZone({ isDragging, onUploadClick }: EmptyFolderDropZoneProps) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-16">
      <button
        type="button"
        onClick={onUploadClick}
        className={cn(
          'flex w-full max-w-md cursor-pointer flex-col items-center gap-3 rounded-xl border-2 border-dashed p-10 text-center transition-colors',
          isDragging
            ? 'border-primary bg-primary/5'
            : 'border-muted-foreground/30 hover:border-muted-foreground/50 hover:bg-muted/40',
        )}
      >
        <UploadIcon
          className={cn('size-8', isDragging ? 'text-primary' : 'text-muted-foreground')}
          strokeWidth={1.5}
          aria-hidden="true"
        />
        <div className="space-y-1">
          <p className="text-sm font-medium">Drag files here or click to upload</p>
          <p className="text-muted-foreground text-sm">
            Files and folders you add will show up here.
          </p>
        </div>
      </button>
    </div>
  )
}
