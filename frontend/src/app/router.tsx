import { useMemo } from 'react'
import { createBrowserRouter, Outlet, RouterProvider, type RouteObject } from 'react-router-dom'
import { AppShell } from '@/app/app-shell'
import { RouteError } from '@/app/route-error'
import { LoginPage } from '@/features/auth/login-page'
import { PublicOnlyRoute, RequireAuth } from '@/features/auth/protected-route'
import { ResetPasswordConfirmPage } from '@/features/auth/reset-password-confirm-page'
import { ResetPasswordPage } from '@/features/auth/reset-password-page'
import { SignupPage } from '@/features/auth/signup-page'
import { DesignPreviewPage } from '@/features/design-preview/design-preview-page'
import { NotFoundPage } from '@/features/errors/not-found-page'
import { BoxDetailPage } from '@/features/hierarchy/box-detail-page'
import { RoomDetailPage } from '@/features/hierarchy/room-detail-page'
import { ShelfDetailPage } from '@/features/hierarchy/shelf-detail-page'
import { SpaceTreePage } from '@/features/hierarchy/space-tree-page'
import { HomePage } from '@/features/home/home-page'
import { PlaceholderPage } from '@/features/placeholders/placeholder-page'
import { SettingsPage } from '@/features/settings/settings-page'

function AppLayout() {
  return (
    <AppShell>
      <Outlet />
    </AppShell>
  )
}

export const appRoutes: RouteObject[] = [
  {
    path: '/login',
    element: (
      <PublicOnlyRoute>
        <LoginPage />
      </PublicOnlyRoute>
    ),
    errorElement: <RouteError />,
  },
  {
    path: '/signup',
    element: (
      <PublicOnlyRoute>
        <SignupPage />
      </PublicOnlyRoute>
    ),
    errorElement: <RouteError />,
  },
  {
    path: '/reset-password',
    element: <ResetPasswordPage />,
    errorElement: <RouteError />,
  },
  {
    path: '/reset-password/confirm',
    element: <ResetPasswordConfirmPage />,
    errorElement: <RouteError />,
  },
  {
    path: '/',
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    errorElement: <RouteError />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'space', element: <SpaceTreePage /> },
      { path: 'space/rooms/:roomId', element: <RoomDetailPage /> },
      { path: 'space/shelves/:shelfId', element: <ShelfDetailPage /> },
      { path: 'space/boxes/:boxId', element: <BoxDetailPage /> },
      {
        path: 'inventory',
        element: (
          <PlaceholderPage
            title="재고"
            milestone="M3 · INVENTORY"
            description="상태·카테고리·위치 필터와 재고 목록을 제공하는 화면입니다."
          />
        ),
      },
      {
        path: 'sharing',
        element: (
          <PlaceholderPage
            title="공유"
            milestone="M4 · SHARING"
            description="초대 수락·거절, Grant 목록, 권한 변경과 철회를 제공하는 화면입니다."
          />
        ),
      },
      {
        path: 'settings',
        element: <SettingsPage />,
      },
    ],
  },
  {
    path: '/design-preview',
    element: <DesignPreviewPage />,
    errorElement: <RouteError />,
  },
  {
    path: '*',
    element: <NotFoundPage />,
  },
]

export function AppRouter() {
  const router = useMemo(() => createBrowserRouter(appRoutes), [])
  return <RouterProvider router={router} />
}
