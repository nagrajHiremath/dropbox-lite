import {
  FileIcon,
  FileTextIcon,
  FolderIcon,
  ImageIcon,
  MusicIcon,
  VideoIcon,
  type LucideIcon,
} from 'lucide-react'
import type { Folder } from '@/api/folders'
import type { FileEntry } from '@/api/files'

export type BrowserEntry = { type: 'folder'; folder: Folder } | { type: 'file'; file: FileEntry }

/** Reused by getEntryIcon below and by PublicSharePage (UI-10), which has no
 * BrowserEntry to build - just a bare mimeType string from the public share
 * response. */
export function getIconForMimeType(mimeType: string | null): LucideIcon {
  const type = mimeType ?? ''
  if (type.startsWith('image/')) return ImageIcon
  if (type.startsWith('video/')) return VideoIcon
  if (type.startsWith('audio/')) return MusicIcon
  if (type === 'application/pdf' || type.startsWith('text/')) return FileTextIcon
  return FileIcon
}

/** Reused by EntryRow now, and by Photos/Videos/Recent later (UI-13). */
export function getEntryIcon(entry: BrowserEntry): LucideIcon {
  if (entry.type === 'folder') return FolderIcon
  return getIconForMimeType(entry.file.mimeType)
}

export function toBrowserEntry(folder: Folder): BrowserEntry
export function toBrowserEntry(file: FileEntry): BrowserEntry
export function toBrowserEntry(value: Folder | FileEntry): BrowserEntry {
  return 'parentId' in value ? { type: 'folder', folder: value } : { type: 'file', file: value }
}
