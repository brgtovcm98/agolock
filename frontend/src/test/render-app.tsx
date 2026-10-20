import { render } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { AppProviders, queryClient } from '@/app/providers'
import { appRoutes } from '@/app/router'
import type { AuthConfig, AuthMe } from '@/shared/api/auth'

export const testAuth: AuthMe = {
  user: {
    id: '018f0000-0000-7000-8000-000000000001',
    email: 'jimin@example.com',
    displayName: '지민',
    status: 'ACTIVE',
  },
  space: {
    id: '018f0000-0000-7000-8000-000000000002',
    name: '지민의 Space',
    ownerUserId: '018f0000-0000-7000-8000-000000000001',
  },
}

export const testAuthConfig: AuthConfig = {
  inviteRequired: false,
  passwordMinLength: 10,
  passwordMaxLength: 128,
}

export function renderApp(
  initialEntries: string[] = ['/'],
  options: { authenticated?: boolean; auth?: AuthMe; config?: AuthConfig } = {},
) {
  const authenticated = options.authenticated ?? true
  queryClient.setQueryData(['auth', 'me'], authenticated ? (options.auth ?? testAuth) : null)
  queryClient.setQueryData(['auth', 'config'], options.config ?? testAuthConfig)

  const router = createMemoryRouter(appRoutes, { initialEntries })
  return render(
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>,
  )
}
