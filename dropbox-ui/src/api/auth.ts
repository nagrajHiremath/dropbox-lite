import { apiFetch } from './client'
import type { AuthSession } from '@/store/authStore'

export interface RegisterRequest {
  email: string
  password: string
  displayName?: string
}

export interface RegisterResponse {
  id: string
  email: string
  displayName: string | null
  createdAt: string
}

export interface LoginRequest {
  email: string
  password: string
}

/** Matches account-service's LoginResponse - returned by both /login and /refresh. */
export interface AuthResponse extends AuthSession {
  tokenType: string
  expiresInMs: number
}

export function register(request: RegisterRequest) {
  return apiFetch<RegisterResponse>('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify(request),
    auth: false,
  })
}

export function login(request: LoginRequest) {
  return apiFetch<AuthResponse>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify(request),
    auth: false,
  })
}
