/** Shared query-key builders so list queries (MyFilesPage) and mutation
 * invalidation (useFolderMutations/useFileMutations) always agree on the
 * exact key shape - drift here would mean a mutation invalidates nothing. */
export const foldersKey = (parentId: string | undefined) => [
  'folders',
  'children',
  parentId ?? 'root',
]
export const filesKey = (folderId: string | undefined) => ['files', 'list', folderId ?? 'root']
export const recentFilesKey = ['files', 'recent']
export const filesByTypeKey = (type: 'image' | 'video') => ['files', 'type', type]
export const trashedFilesKey = ['files', 'trashed']
export const trashedFoldersKey = ['folders', 'trashed']
export const versionsKey = (fileId: string) => ['file-versions', fileId]
export const sharesKey = (fileId: string) => ['file-shares', fileId]
export const storageUsageKey = ['account', 'storage']
