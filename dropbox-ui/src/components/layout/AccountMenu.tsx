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
 */
export function AccountMenu() {
  const navigate = useNavigate()
  const email = useAuthStore((s) => s.email)
  const clear = useAuthStore((s) => s.clear)

  function handleLogout() {
    clear()
    navigate('/login', { replace: true })
  }

  const initial = email ? email.charAt(0).toUpperCase() : '?'

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          className="bg-primary text-primary-foreground flex size-8 shrink-0 cursor-pointer items-center justify-center rounded-full text-sm font-medium outline-none transition-opacity hover:opacity-90 focus-visible:ring-3 focus-visible:ring-ring/50"
          aria-label="Account menu"
        >
          {initial}
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuLabel className="text-muted-foreground truncate font-normal">
          {email ?? 'Signed in'}
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
