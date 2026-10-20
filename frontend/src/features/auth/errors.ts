import { ApiError } from '@/shared/api/client'

export function authErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return '이메일 또는 비밀번호가 올바르지 않습니다.'
    if (error.status === 429) {
      return error.retryAfterSeconds
        ? `요청이 너무 많습니다. ${error.retryAfterSeconds}초 후 다시 시도해 주세요.`
        : '요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.'
    }
    return error.message
  }
  return '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
}
