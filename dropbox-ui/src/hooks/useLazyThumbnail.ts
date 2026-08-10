import { useEffect, useRef, useState } from 'react'
import { fetchFileBlob } from '@/lib/download'

/**
 * Loads an image file's content as an object URL only once its tile actually
 * scrolls into view (IntersectionObserver, 200px lookahead so it's ready
 * just before it's visible), and only once - the observer disconnects after
 * the first intersection rather than re-fetching on every scroll in/out.
 * Object URL is revoked on unmount so navigating away/re-filtering the grid
 * doesn't leak blobs. Pass null for non-image entries to skip loading
 * entirely (grid tiles for folders/other file types just show their icon).
 */
export function useLazyThumbnail(fileId: string | null) {
  const ref = useRef<HTMLDivElement>(null)
  const [url, setUrl] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    setUrl(null)
    setFailed(false)
    if (!fileId || !ref.current) return

    let cancelled = false
    let objectUrl: string | null = null

    const observer = new IntersectionObserver(
      (entries) => {
        if (!entries[0]?.isIntersecting) return
        observer.disconnect()
        fetchFileBlob(fileId)
          .then((blob) => {
            if (cancelled) return
            objectUrl = URL.createObjectURL(blob)
            setUrl(objectUrl)
          })
          .catch(() => {
            if (!cancelled) setFailed(true)
          })
      },
      { rootMargin: '200px' },
    )
    observer.observe(ref.current)

    return () => {
      cancelled = true
      observer.disconnect()
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [fileId])

  return { ref, url, failed }
}
