import { useParams } from 'react-router-dom'

export default function PublicSharePage() {
  const { token } = useParams<{ token: string }>()

  return (
    <div className="flex min-h-svh items-center justify-center p-6">
      <h1 className="text-lg font-medium">Shared file ({token})</h1>
    </div>
  )
}
