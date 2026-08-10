import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createShare, revokeShare, type CreateShareRequest } from '@/api/shares'
import { sharesKey } from './queryKeys'

export function useCreateShare() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ fileId, request }: { fileId: string; request: CreateShareRequest }) =>
      createShare(fileId, request),
    onSuccess: (_share, { fileId }) => {
      void queryClient.invalidateQueries({ queryKey: sharesKey(fileId) })
    },
  })
}

export function useRevokeShare() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ shareId }: { shareId: string; fileId: string }) => revokeShare(shareId),
    onSuccess: (_void, { fileId }) => {
      void queryClient.invalidateQueries({ queryKey: sharesKey(fileId) })
    },
  })
}
