import type { LucideIcon } from 'lucide-react'
import { Home, LayoutGrid, List, Settings, Share2 } from 'lucide-react'

export type NavigationItem = {
  label: string
  href: string
  icon: LucideIcon
  badge?: string
}

export const mainNavigation: NavigationItem[] = [
  { label: '홈·검색', href: '/', icon: Home },
  { label: '공간', href: '/space', icon: LayoutGrid },
  { label: '재고', href: '/inventory', icon: List },
  { label: '공유', href: '/sharing', icon: Share2 },
  { label: '설정', href: '/settings', icon: Settings },
]

export function activeNavigationHref(pathname: string): string {
  if (pathname === '/') return '/'
  if (pathname.startsWith('/items')) return '/inventory'
  return (
    mainNavigation.find((item) => item.href !== '/' && pathname.startsWith(item.href))?.href ?? '/'
  )
}
