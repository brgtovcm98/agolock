export type ApiFieldError = {
  field: string
  message: string
}

export type ProblemDetail = {
  type?: string
  title: string
  status: number
  detail?: string
  instance?: string
  code: string
  fieldErrors?: ApiFieldError[]
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: ApiFieldError[]
  readonly problem: ProblemDetail

  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title)
    this.name = 'ApiError'
    this.status = problem.status
    this.code = problem.code
    this.fieldErrors = problem.fieldErrors ?? []
    this.problem = problem
  }
}

export type ApiErrorAction = 'LOGIN' | 'RELOAD' | 'RETRY' | 'SHOW_FIELD_ERRORS' | 'SHOW_MESSAGE'

const actionByStatus: Record<number, ApiErrorAction> = {
  401: 'LOGIN',
  403: 'SHOW_MESSAGE',
  404: 'SHOW_MESSAGE',
  409: 'RELOAD',
  422: 'SHOW_FIELD_ERRORS',
  429: 'RETRY',
  503: 'RETRY',
}

export function actionFor(error: ApiError): ApiErrorAction {
  return actionByStatus[error.status] ?? 'SHOW_MESSAGE'
}

export function isProblemDetail(value: unknown): value is ProblemDetail {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return (
    typeof candidate.title === 'string' &&
    typeof candidate.status === 'number' &&
    typeof candidate.code === 'string'
  )
}

export async function parseProblemResponse(response: Response): Promise<ProblemDetail> {
  const fallback: ProblemDetail = {
    title: '요청을 처리하지 못했습니다',
    status: response.status,
    code: 'NETWORK_OR_SERVER_ERROR',
    detail: '잠시 후 다시 시도해 주세요.',
  }

  try {
    const body: unknown = await response.json()
    if (isProblemDetail(body)) {
      return body
    }
  } catch {
    // JSON이 아닌 오류 응답은 기본 problem으로 변환합니다.
  }

  return fallback
}

function readCookie(name: string): string | null {
  if (typeof document === 'undefined') return null
  const prefix = `${name}=`
  const cookie = document.cookie.split('; ').find((item) => item.startsWith(prefix))
  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)

  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const csrfToken = readCookie('XSRF-TOKEN')
  if (csrfToken && method !== 'GET' && method !== 'HEAD' && method !== 'OPTIONS') {
    headers.set('X-XSRF-TOKEN', csrfToken)
  }

  const response = await fetch(path, {
    ...init,
    headers,
    credentials: 'include',
  })

  if (!response.ok) {
    throw new ApiError(await parseProblemResponse(response))
  }

  if (response.status === 204) {
    return undefined as T
  }

  const contentType = response.headers.get('content-type') ?? ''
  return (contentType.includes('json') ? await response.json() : await response.text()) as T
}
