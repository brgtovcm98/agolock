import { useQuery, useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, type ReactNode } from 'react'
import {
  authApi,
  type AuthConfig,
  type AuthMe,
  type AuthSpace,
  type AuthUser,
  type LoginInput,
  type SignupInput,
} from '@/shared/api/auth'
import { ApiError, setUnauthorizedHandler } from '@/shared/api/client'

type AuthContextValue = {
  user: AuthUser | null
  space: AuthSpace | null
  config: AuthConfig | null
  isLoading: boolean
  isAuthenticated: boolean
  login: (input: LoginInput) => Promise<AuthMe>
  signup: (input: SignupInput) => Promise<AuthMe>
  logout: () => Promise<void>
  updateProfile: (displayName: string) => Promise<AuthUser>
  updateSpace: (name: string) => Promise<AuthSpace>
  refresh: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const meQuery = useQuery<AuthMe | null>({
    queryKey: ['auth', 'me'],
    queryFn: async () => {
      try {
        return await authApi.me()
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) return null
        throw error
      }
    },
    retry: false,
    staleTime: 30_000,
  })
  const configQuery = useQuery({
    queryKey: ['auth', 'config'],
    queryFn: authApi.config,
    retry: 1,
    staleTime: 5 * 60_000,
  })

  useEffect(() => {
    setUnauthorizedHandler(() => {
      queryClient.setQueryData<AuthMe | null>(['auth', 'me'], null)
    })
    return () => setUnauthorizedHandler(null)
  }, [queryClient])

  const login = useCallback(
    async (input: LoginInput) => {
      const result = await authApi.login(input)
      queryClient.setQueryData<AuthMe | null>(['auth', 'me'], result)
      return result
    },
    [queryClient],
  )

  const signup = useCallback(
    async (input: SignupInput) => {
      const result = await authApi.signup(input)
      queryClient.setQueryData<AuthMe | null>(['auth', 'me'], result)
      return result
    },
    [queryClient],
  )

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 401)) throw error
    } finally {
      queryClient.setQueryData<AuthMe | null>(['auth', 'me'], null)
    }
  }, [queryClient])

  const updateProfile = useCallback(
    async (displayName: string) => {
      const user = await authApi.updateMe({ displayName })
      queryClient.setQueryData<AuthMe | null>(['auth', 'me'], (current) =>
        current ? { ...current, user } : current,
      )
      return user
    },
    [queryClient],
  )

  const updateSpace = useCallback(
    async (name: string) => {
      const current = queryClient.getQueryData<AuthMe | null>(['auth', 'me'])
      if (!current) throw new Error('로그인이 필요합니다.')
      const space = await authApi.updateSpace(current.space.id, name)
      queryClient.setQueryData<AuthMe | null>(['auth', 'me'], (data) =>
        data ? { ...data, space } : data,
      )
      return space
    },
    [queryClient],
  )

  const refresh = useCallback(async () => {
    const result = await authApi.me()
    queryClient.setQueryData<AuthMe | null>(['auth', 'me'], result)
  }, [queryClient])

  const value = useMemo<AuthContextValue>(
    () => ({
      user: meQuery.data?.user ?? null,
      space: meQuery.data?.space ?? null,
      config: configQuery.data ?? null,
      isLoading: meQuery.isLoading,
      isAuthenticated: Boolean(meQuery.data),
      login,
      signup,
      logout,
      updateProfile,
      updateSpace,
      refresh,
    }),
    [
      configQuery.data,
      login,
      logout,
      meQuery.data,
      meQuery.isLoading,
      refresh,
      signup,
      updateProfile,
      updateSpace,
    ],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth는 AuthProvider 안에서 사용해야 합니다.')
  }
  return context
}
