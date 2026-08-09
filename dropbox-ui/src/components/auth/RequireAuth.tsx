import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'

/**
 * UI-01: guards the authenticated app (AppShell) route group. Preserves the
 * originally-requested location in nav state so LoginPage can send the user
 * back to where they meant to go, rather than always landing on /files.
 */
export default function RequireAuth() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  return <Outlet />
}
