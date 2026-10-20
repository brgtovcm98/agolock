import { apiFetch } from './client'

export type AuthUser = {
  id: string
  email: string
  displayName: string
  status: 'ACTIVE' | 'LOCKED' | 'DELETED'
}

export type AuthSpace = {
  id: string
  name: string
  ownerUserId: string
}

export type AuthMe = {
  user: AuthUser
  space: AuthSpace
}

export type AuthConfig = {
  inviteRequired: boolean
  passwordMinLength: number
  passwordMaxLength: number
}

export type SignupInput = {
  email: string
  password: string
  displayName: string
  inviteCode?: string
}

export type LoginInput = {
  email: string
  password: string
}

export type UpdateProfileInput = {
  displayName: string
}

export const authApi = {
  config: () => apiFetch<AuthConfig>('/auth/config'),
  csrf: () => apiFetch<{ token: string }>('/auth/csrf'),
  me: () => apiFetch<AuthMe>('/auth/me'),
  login: (input: LoginInput) =>
    apiFetch<AuthMe>('/auth/login', {
      method: 'POST',
      body: JSON.stringify(input),
    }),
  signup: (input: SignupInput) =>
    apiFetch<AuthMe>('/auth/signup', {
      method: 'POST',
      body: JSON.stringify(input),
    }),
  logout: () => apiFetch<void>('/auth/logout', { method: 'POST' }),
  updateMe: (input: UpdateProfileInput) =>
    apiFetch<AuthUser>('/auth/me', {
      method: 'PATCH',
      body: JSON.stringify(input),
    }),
  updateSpace: (spaceId: string, name: string) =>
    apiFetch<AuthSpace>(`/spaces/${spaceId}`, {
      method: 'PATCH',
      body: JSON.stringify({ name }),
    }),
  requestPasswordReset: (email: string) =>
    apiFetch<void>('/auth/password-reset/request', {
      method: 'POST',
      body: JSON.stringify({ email }),
    }),
  confirmPasswordReset: (token: string, newPassword: string) =>
    apiFetch<void>('/auth/password-reset/confirm', {
      method: 'POST',
      body: JSON.stringify({ token, newPassword }),
    }),
}
