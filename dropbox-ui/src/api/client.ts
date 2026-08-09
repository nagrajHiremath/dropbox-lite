import { type AuthSession, useAuthStore } from '@/store/authStore'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL

interface ErrorResponseBody {
  code?: string
  message?: string
  fieldErrors?: Record<string, string> | null
}

export class ApiError extends Error {
  status: number
  code: string
  fieldErrors?: Record<string, string>

  constructor(status: number, code: string, message: string, fieldErrors?: Record<string, string>) {
    super(message)
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

async function toApiError(res: Response): Promise<ApiError> {
  try {
    const body = (await res.json()) as ErrorResponseBody
    return new ApiError(
      res.status,
      body.code ?? 'UNKNOWN_ERROR',
      body.message ?? res.statusText,
      body.fieldErrors ?? undefined,
    )
  } catch {
    return new ApiError(res.status, 'UNKNOWN_ERROR', res.statusText)
  }
}

export interface ApiFetchOptions extends RequestInit {
  /**
   * false for login/register/refresh themselves - skips attaching
   * Authorization and skips the refresh-on-401 interceptor below (otherwise
   * a failed login's own 401 would trigger a pointless refresh attempt).
   */
  auth?: boolean
}

/**
 * UI-01: single-flight guard so N concurrent 401s only trigger one refresh
 * call - the rest await the same in-flight promise instead of each racing
 * their own refresh (which would rotate the refresh token out from under
 * each other, per account-service's single-use rotation).
 */
let refreshPromise: Promise<boolean> | null = null

function refreshSession(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = doRefresh().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

async function doRefresh(): Promise<boolean> {
  const { refreshToken } = useAuthStore.getState()
  if (!refreshToken) return false

  try {
    const session = await apiFetch<AuthSession>('/api/v1/auth/refresh', {
      method: 'POST',
      body: JSON.stringify({ refreshToken }),
      auth: false,
    })
    useAuthStore.getState().setSession(session)
    return true
  } catch {
    return false
  }
}

export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const { auth = true, headers, ...rest } = options

  const doFetch = () => {
    const finalHeaders = new Headers(headers)
    if (rest.body && !finalHeaders.has('Content-Type')) {
      finalHeaders.set('Content-Type', 'application/json')
    }
    if (auth) {
      const { accessToken } = useAuthStore.getState()
      if (accessToken) finalHeaders.set('Authorization', `Bearer ${accessToken}`)
    }
    return fetch(`${API_BASE_URL}${path}`, { ...rest, headers: finalHeaders })
  }

  let res = await doFetch()

  if (res.status === 401 && auth) {
    const refreshed = await refreshSession()
    if (!refreshed) {
      useAuthStore.getState().clear()
      window.location.assign('/login')
      throw new ApiError(401, 'UNAUTHORIZED', 'Session expired')
    }
    res = await doFetch()
  }

  if (!res.ok) {
    throw await toApiError(res)
  }

  if (res.status === 204) {
    return undefined as T
  }

  return (await res.json()) as T
}
