import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { DownloadIcon, PackageOpenIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ErrorState } from '@/components/common/ErrorState'
import { getPublicShare, publicShareContentUrl } from '@/api/publicShares'
import { ApiError } from '@/api/client'
import { getIconForMimeType } from '@/lib/fileIcons'
import { formatBytes } from '@/lib/format'

export default function PublicSharePage() {
  const { token } = useParams<{ token: string }>()

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['public-share', token],
    queryFn: () => getPublicShare(token!),
    enabled: !!token,
    retry: false,
  })

  const notFound = error instanceof ApiError && error.status === 404
  const Icon = data ? getIconForMimeType(data.mimeType) : PackageOpenIcon

  return (
    <div className="flex min-h-svh items-center justify-center p-6">
      <div className="w-full max-w-sm space-y-6">
        <h1 className="text-center text-lg font-semibold">Dropbox Lite</h1>

        <div className="rounded-xl border p-6">
          {isLoading ? (
            <div className="flex flex-col items-center gap-3 py-4">
              <Skeleton className="size-10 rounded-full" />
              <Skeleton className="h-4 w-40" />
              <Skeleton className="h-3 w-24" />
            </div>
          ) : isError ? (
            <ErrorState
              title={notFound ? 'Link not found' : 'Something went wrong'}
              description={
                notFound
                  ? 'This share link is invalid, has expired, or was revoked.'
                  : 'Please try again in a moment.'
              }
            />
          ) : (
            data && (
              <div className="flex flex-col items-center gap-3 py-4 text-center">
                <Icon className="text-muted-foreground size-10" strokeWidth={1.5} />
                <div>
                  <p className="font-medium break-all">{data.fileName}</p>
                  {data.sizeBytes != null && (
                    <p className="text-muted-foreground text-sm">{formatBytes(data.sizeBytes)}</p>
                  )}
                </div>
                {data.permission === 'DOWNLOAD' ? (
                  <Button asChild className="w-full">
                    <a href={publicShareContentUrl(token!)}>
                      <DownloadIcon className="size-4" />
                      Download
                    </a>
                  </Button>
                ) : (
                  <p className="text-muted-foreground text-xs">
                    The owner has made this file view-only.
                  </p>
                )}
              </div>
            )
          )}
        </div>
      </div>
    </div>
  )
}
