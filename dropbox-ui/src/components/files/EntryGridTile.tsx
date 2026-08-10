import { CheckIcon, MoreHorizontalIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { ContextMenu, ContextMenuContent, ContextMenuTrigger } from '@/components/ui/context-menu'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { cn } from '@/lib/utils'
import { MenuEntry } from './entryMenu'
import { useEntryActions } from '@/hooks/useEntryActions'
import { useLazyThumbnail } from '@/hooks/useLazyThumbnail'
import { formatDate } from '@/lib/format'
import type { BrowserEntry } from '@/lib/fileIcons'

interface EntryGridTileProps {
  entry: BrowserEntry
  selected: boolean
  onSelect: (id: string, options: { toggle?: boolean; range?: boolean }) => void
  onClearSelection: () => void
  onRequestRename: (entry: BrowserEntry) => void
  onRequestMove: (entry: BrowserEntry) => void
  onRequestVersionHistory: (entry: BrowserEntry) => void
  onRequestShare: (entry: BrowserEntry) => void
  onRequestDetails: (entry: BrowserEntry) => void
  onRequestPreview: (entry: BrowserEntry) => void
  onRequestTrash: (entry: BrowserEntry) => void
}

/**
 * Same interactions as EntryRow (via useEntryActions) laid out as a tile
 * instead of a row - stage 3 (real image thumbnails) will swap the plain
 * icon for a lazy-loaded preview inside the same thumbnail slot.
 */
export function EntryGridTile(props: EntryGridTileProps) {
  const { entry, selected } = props
  const {
    name,
    updatedAt,
    sizeLabel,
    Icon,
    isOver,
    dropHandlers,
    menuItems,
    handleClick,
    handleCheckboxClick,
    handleDoubleClick,
    handleContextMenu,
    handleMenuTriggerClick,
    handleDragStart,
    handleDragEnd,
  } = useEntryActions(props)

  const isImage = entry.type === 'file' && (entry.file.mimeType ?? '').startsWith('image/')
  const {
    ref: thumbnailRef,
    url: thumbnailUrl,
    failed: thumbnailFailed,
  } = useLazyThumbnail(isImage ? entry.file.id : null)

  const caption =
    sizeLabel === '—' ? formatDate(updatedAt) : `${sizeLabel} · ${formatDate(updatedAt)}`

  return (
    <ContextMenu>
      <ContextMenuTrigger asChild>
        <div
          role="row"
          aria-selected={selected}
          draggable
          onDragStart={handleDragStart}
          onDragEnd={handleDragEnd}
          {...dropHandlers}
          onClick={handleClick}
          onDoubleClick={handleDoubleClick}
          onContextMenu={handleContextMenu}
          className={cn(
            'group relative flex cursor-pointer select-none flex-col gap-2 rounded-lg border p-2.5 shadow-sm transition-all',
            selected
              ? 'bg-accent border-accent-foreground/20 shadow-md'
              : 'hover:border-border hover:shadow-md hover:bg-accent/50',
            isOver && 'bg-primary/10 ring-primary ring-2 ring-inset',
          )}
        >
          <div className="flex items-center justify-between">
            <button
              type="button"
              role="checkbox"
              aria-checked={selected}
              aria-label={selected ? `Deselect ${name}` : `Select ${name}`}
              onClick={handleCheckboxClick}
              className={cn(
                'flex size-4.5 shrink-0 cursor-pointer items-center justify-center rounded-sm border transition-colors',
                selected
                  ? 'bg-primary border-primary text-primary-foreground'
                  : 'border-muted-foreground/40 bg-background opacity-0 group-hover:opacity-100 group-focus-within:opacity-100',
              )}
            >
              {selected && <CheckIcon className="size-3" aria-hidden="true" />}
            </button>

            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon"
                  className={cn(
                    'size-7 shrink-0 opacity-0 focus-visible:opacity-100 group-hover:opacity-100',
                    selected && 'opacity-100',
                  )}
                  onClick={handleMenuTriggerClick}
                  aria-label={`Actions for ${name}`}
                >
                  <MoreHorizontalIcon className="size-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                {menuItems.map((item, i) => (
                  <MenuEntry
                    key={item.label}
                    item={item}
                    showSeparatorBefore={i === menuItems.length - 1}
                    kind="dropdown"
                  />
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
          </div>

          <div
            ref={thumbnailRef}
            className="bg-muted flex aspect-square items-center justify-center overflow-hidden rounded-md"
          >
            {thumbnailUrl && !thumbnailFailed ? (
              <img src={thumbnailUrl} alt="" className="h-full w-full object-cover" />
            ) : (
              <Icon
                className="text-muted-foreground size-10"
                strokeWidth={1.5}
                aria-hidden="true"
              />
            )}
          </div>

          <div className="min-w-0">
            <p className="truncate text-sm font-medium">{name}</p>
            <p className="text-muted-foreground truncate text-xs">{caption}</p>
          </div>
        </div>
      </ContextMenuTrigger>
      <ContextMenuContent>
        {menuItems.map((item, i) => (
          <MenuEntry
            key={item.label}
            item={item}
            showSeparatorBefore={i === menuItems.length - 1}
            kind="context"
          />
        ))}
      </ContextMenuContent>
    </ContextMenu>
  )
}
