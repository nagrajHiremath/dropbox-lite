import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export interface AuthSession {
  accessToken: string
  refreshToken: string
  userId: string
  email: string
}

interface AuthState extends Partial<AuthSession> {
  isAuthenticated: boolean
  setSession: (session: AuthSession) => void
  clear: () => void
}

/**
 * UI-01: persisted via zustand's own localStorage middleware (no manual
 * mirroring needed) so a page refresh doesn't force re-login. accessToken is
 * short-lived (1h, account-service jwt.expiration-ms); refreshToken is what
 * the API client (see api/client.ts) uses to silently mint a new pair on a
 * 401 before ever forcing a hard redirect to /login.
 */
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: undefined,
      refreshToken: undefined,
      userId: undefined,
      email: undefined,
      isAuthenticated: false,
      setSession: (session) => set({ ...session, isAuthenticated: true }),
      clear: () =>
        set({
          accessToken: undefined,
          refreshToken: undefined,
          userId: undefined,
          email: undefined,
          isAuthenticated: false,
        }),
    }),
    { name: 'dropbox-auth' },
  ),
)

/** Non-hook accessor for use outside React components (e.g. api/client.ts's interceptor). */
export function getAuthState() {
  return useAuthStore.getState()
}
