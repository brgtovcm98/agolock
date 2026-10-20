import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  actionFor,
  ApiError,
  apiFetch,
  parseProblemResponse,
  type ProblemDetail,
} from '@/shared/api/client'

const forbidden: ProblemDetail = {
  title: '권한이 없습니다',
  status: 403,
  detail: '이 재고를 수정할 권한이 없습니다.',
  code: 'PERMISSION_DENIED',
  fieldErrors: [{ field: 'inventoryId', message: '권한이 없습니다.' }],
}

describe('API client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('problem+json을 ApiError로 변환한다', async () => {
    const response = new Response(JSON.stringify(forbidden), {
      status: 403,
      headers: { 'content-type': 'application/problem+json' },
    })

    await expect(parseProblemResponse(response)).resolves.toEqual(forbidden)
  })

  it('상태별 공통 동작을 매핑한다', () => {
    expect(actionFor(new ApiError(forbidden))).toBe('SHOW_MESSAGE')
    expect(actionFor(new ApiError({ title: '만료', status: 401, code: 'UNAUTHENTICATED' }))).toBe(
      'LOGIN',
    )
    expect(actionFor(new ApiError({ title: '충돌', status: 409, code: 'VERSION_CONFLICT' }))).toBe(
      'RELOAD',
    )
    expect(actionFor(new ApiError({ title: '검증', status: 422, code: 'VALIDATION_FAILED' }))).toBe(
      'SHOW_FIELD_ERRORS',
    )
  })

  it('성공 응답 JSON을 반환한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(
        async () =>
          new Response(JSON.stringify({ name: 'agolock' }), {
            status: 200,
            headers: { 'content-type': 'application/json' },
          }),
      ),
    )

    await expect(apiFetch<{ name: string }>('/api/v1/info')).resolves.toEqual({ name: 'agolock' })
  })

  it('204 응답은 undefined를 반환한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(null, { status: 204 })),
    )

    await expect(apiFetch<void>('/api/v1/things/1', { method: 'DELETE' })).resolves.toBeUndefined()
  })

  it('오류 응답은 ApiError로 던진다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(
        async () =>
          new Response(JSON.stringify(forbidden), {
            status: 403,
            headers: { 'content-type': 'application/problem+json' },
          }),
      ),
    )

    await expect(apiFetch('/api/v1/inventories/1')).rejects.toMatchObject({
      status: 403,
      code: 'PERMISSION_DENIED',
    })
  })
})
