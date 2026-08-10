export interface DropTree {
  /** Directories to create - a path's parent always appears before it, so
   * creating them in this order never needs a not-yet-created folder. */
  folderPaths: string[][]
  /** Files paired with the directory path (relative to wherever the drop
   * landed) they belong under - an empty path means "goes directly into the
   * drop target itself," same as a plain flat-file drop today. */
  files: { file: File; folderPath: string[] }[]
}

/**
 * Must be called synchronously inside the drop event handler - some browsers
 * invalidate DataTransferItem once the handler returns, so entries can't be
 * extracted lazily. The FileSystemEntry objects webkitGetAsEntry() returns
 * do stay valid afterwards, which is what makes the async walk in
 * buildDropTree possible. Despite the "webkit" name this is supported in
 * every evergreen browser (Chrome, Edge, Firefox, Safari) - no library
 * needed for folder-aware drag and drop.
 */
export function extractDroppedEntries(items: DataTransferItemList): FileSystemEntry[] {
  const entries: FileSystemEntry[] = []
  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    if (item.kind !== 'file') continue
    const entry = item.webkitGetAsEntry?.()
    if (entry) entries.push(entry)
  }
  return entries
}

function readDirectoryBatch(reader: FileSystemDirectoryReader): Promise<FileSystemEntry[]> {
  return new Promise((resolve, reject) => reader.readEntries(resolve, reject))
}

/** readEntries() only returns up to ~100 entries per call (a documented
 * browser quirk, not a bug) - it must be called repeatedly until it returns
 * an empty array to get a directory's full contents. */
async function readAllDirectoryEntries(dir: FileSystemDirectoryEntry): Promise<FileSystemEntry[]> {
  const reader = dir.createReader()
  const all: FileSystemEntry[] = []
  for (;;) {
    const batch = await readDirectoryBatch(reader)
    if (batch.length === 0) break
    all.push(...batch)
  }
  return all
}

function readFile(entry: FileSystemFileEntry): Promise<File> {
  return new Promise((resolve, reject) => entry.file(resolve, reject))
}

async function walk(entry: FileSystemEntry, ancestorPath: string[], tree: DropTree): Promise<void> {
  if (entry.isFile) {
    const file = await readFile(entry as FileSystemFileEntry)
    tree.files.push({ file, folderPath: ancestorPath })
    return
  }
  if (!entry.isDirectory) return // neither file nor directory - ignore

  const dirPath = [...ancestorPath, entry.name]
  tree.folderPaths.push(dirPath)
  const children = await readAllDirectoryEntries(entry as FileSystemDirectoryEntry)
  for (const child of children) {
    await walk(child, dirPath, tree)
  }
}

/**
 * Walks dropped entries (files and, recursively, folders) into a flat
 * upload plan. See MyFilesPage's handleDroppedEntries for how folderPaths
 * gets turned into real folders (via the existing createFolder API, one
 * per path, top-down) before files are enqueued against the resolved ids -
 * this module only reads the drag payload, it doesn't call any API.
 */
export async function buildDropTree(topLevelEntries: FileSystemEntry[]): Promise<DropTree> {
  const tree: DropTree = { folderPaths: [], files: [] }
  for (const entry of topLevelEntries) {
    await walk(entry, [], tree)
  }
  return tree
}
