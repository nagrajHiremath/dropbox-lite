import {
  DownloadIcon,
  EyeIcon,
  FolderOpenIcon,
  HistoryIcon,
  InfoIcon,
  MoveIcon,
  PencilIcon,
  Share2Icon,
  Trash2Icon,
  type LucideIcon,
} from 'lucide-react'
import { ContextMenuItem, ContextMenuSeparator } from '@/components/ui/context-menu'
import { DropdownMenuItem, DropdownMenuSeparator } from '@/components/ui/dropdown-menu'
import type { BrowserEntry } from '@/lib/fileIcons'

export interface MenuItemConfig {
  label: string
  icon: LucideIcon
  disabled: boolean
  destructive?: boolean
  onSelect?: () => void
}

export interface MenuHandlers {
  onOpen: () => void
  onPreview: () => void
  onDetails: () => void
  onDownload: () => void
  onRename: () => void
  onMove: () => void
  onVersionHistory: () => void
  onShare: () => void
  onTrash: () => void
}

/**
 * Shared by EntryRow (list) and EntryGridTile (grid) - same actions apply
 * regardless of layout. UI-12: Preview and Download are live for files
 * (folders have no preview/zip-download support, so both stay disabled
 * there - Open remains folder-only). UI-11: Details is live for both types.
 * UI-09: Share is live for files only (no folder-share endpoint). Disabled
 * rather than a "coming soon" toast, so it's honest about what works today
 * without click-noise.
 */
export function getMenuItems(entry: BrowserEntry, handlers: MenuHandlers): MenuItemConfig[] {
  const isFolder = entry.type === 'folder'
  const items: MenuItemConfig[] = [
    {
      label: isFolder ? 'Open' : 'Preview',
      icon: isFolder ? FolderOpenIcon : EyeIcon,
      disabled: false,
      onSelect: isFolder ? handlers.onOpen : handlers.onPreview,
    },
    { label: 'Details', icon: InfoIcon, disabled: false, onSelect: handlers.onDetails },
    {
      label: 'Download',
      icon: DownloadIcon,
      disabled: isFolder,
      onSelect: isFolder ? undefined : handlers.onDownload,
    },
    { label: 'Rename', icon: PencilIcon, disabled: false, onSelect: handlers.onRename },
    { label: 'Move', icon: MoveIcon, disabled: false, onSelect: handlers.onMove },
    {
      label: 'Share',
      icon: Share2Icon,
      disabled: isFolder,
      onSelect: isFolder ? undefined : handlers.onShare,
    },
  ]
  if (!isFolder) {
    items.push({
      label: 'Version history',
      icon: HistoryIcon,
      disabled: false,
      onSelect: handlers.onVersionHistory,
    })
  }
  items.push({
    label: 'Move to Trash',
    icon: Trash2Icon,
    disabled: false,
    destructive: true,
    onSelect: handlers.onTrash,
  })
  return items
}

export function MenuEntry({
  item,
  showSeparatorBefore,
  kind,
}: {
  item: MenuItemConfig
  showSeparatorBefore: boolean
  kind: 'dropdown' | 'context'
}) {
  const Item = kind === 'dropdown' ? DropdownMenuItem : ContextMenuItem
  const Separator = kind === 'dropdown' ? DropdownMenuSeparator : ContextMenuSeparator
  return (
    <>
      {showSeparatorBefore && <Separator />}
      <Item
        disabled={item.disabled}
        variant={item.destructive ? 'destructive' : 'default'}
        onSelect={item.onSelect}
      >
        <item.icon className="size-4" />
        {item.label}
      </Item>
    </>
  )
}
