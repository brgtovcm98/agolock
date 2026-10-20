import { render } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { AppProviders } from '@/app/providers'
import { appRoutes } from '@/app/router'

export function renderApp(initialEntries: string[] = ['/']) {
  const router = createMemoryRouter(appRoutes, { initialEntries })
  return render(
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>,
  )
}
