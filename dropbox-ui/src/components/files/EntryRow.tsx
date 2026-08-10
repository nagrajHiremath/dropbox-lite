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
import { formatDate } from '@/lib/format'
import type { BrowserEntry } from '@/lib/fileIcons'

interface EntryRowProps {
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

export function EntryRow(props: EntryRowProps) {
  const { selected } = props
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
            'group flex cursor-pointer select-none items-center gap-3 border-b px-6 py-2 text-sm transition-colors',
            selected ? 'bg-accent' : 'hover:bg-accent/50',
            isOver && 'bg-primary/10 ring-primary ring-2 ring-inset',
          )}
        >
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
          <Icon className="text-muted-foreground size-4.5 shrink-0" aria-hidden="true" />
          <span className="min-w-0 flex-1 truncate font-medium">{name}</span>
          <span className="text-muted-foreground w-28 shrink-0 text-right">
            {formatDate(updatedAt)}
          </span>
          <span className="text-muted-foreground w-16 shrink-0 text-right">{sizeLabel}</span>

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
