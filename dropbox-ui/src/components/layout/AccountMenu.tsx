import { useNavigate } from 'react-router-dom'
import { LogOutIcon } from 'lucide-react'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useAuthStore } from '@/store/authStore'

/**
 * No Account/Profile page - account-service exposes no profile/me endpoint
 * to back one (only /auth/register,login,refresh), so this stays limited to
 * identity display + Logout rather than linking to a page that doesn't
 * exist. Logout is purely client-side: the backend has no logout/revoke
 * endpoint either, so clearing the persisted auth state (which RequireAuth
 * reacts to) and redirecting is the whole flow.
 *
 * displayName is optional at registration (a user can leave it blank), so
 * it's shown when present and falls back to email otherwise - same fallback
 * both in the trigger row and the dropdown label.
 */
export function AccountMenu() {
  const navigate = useNavigate()
  const email = useAuthStore((s) => s.email)
  const displayName = useAuthStore((s) => s.displayName)
  const clear = useAuthStore((s) => s.clear)

  function handleLogout() {
    clear()
    navigate('/login', { replace: true })
  }

  const primaryLabel = displayName || email || 'Signed in'
  const initial = primaryLabel.charAt(0).toUpperCase()

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          className="hover:bg-accent flex w-full cursor-pointer items-center gap-2.5 rounded-md px-2 py-2 text-left outline-none transition-colors focus-visible:ring-3 focus-visible:ring-ring/50"
          aria-label="Account menu"
        >
          <span className="bg-primary text-primary-foreground flex size-8 shrink-0 items-center justify-center rounded-full text-sm font-medium">
            {initial}
          </span>
          <span className="min-w-0 flex-1">
            <span className="block truncate text-sm font-medium">{primaryLabel}</span>
            {displayName && email && (
              <span className="text-muted-foreground block truncate text-xs">{email}</span>
            )}
          </span>
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuLabel className="font-normal">
          <span className="block truncate text-sm font-medium">{primaryLabel}</span>
          {displayName && email && (
            <span className="text-muted-foreground block truncate text-xs">{email}</span>
          )}
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={handleLogout}>
          <LogOutIcon className="size-4" />
          Log out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
