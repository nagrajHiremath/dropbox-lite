import { QueryClient } from '@tanstack/react-query'

/**
 * Shared singleton so non-component code (uploadQueueStore's onStatusChange
 * callback, which fires from UploadEngine outside any React render) can
 * invalidate queries too, not just components via useQueryClient(). See
 * UI-08: upload completion needs to invalidate the file list (and, for a
 * new-version upload, that file's version history) so the UI reflects it
 * without a manual refresh.
 */
export const queryClient = new QueryClient()
