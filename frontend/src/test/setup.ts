import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// jsdom의 AbortSignal과 Node undici의 Request가 섞이면 React Router 데이터 라우터의
// 내부 이동이 실패합니다. 테스트에서는 signal을 제거한 Request로 대체합니다.
const OriginalRequest = globalThis.Request
if (OriginalRequest) {
  class TestRequest extends OriginalRequest {
    constructor(input: RequestInfo | URL, init?: RequestInit) {
      if (init?.signal) {
        const nextInit = { ...init }
        delete nextInit.signal
        super(input, nextInit)
      } else {
        super(input, init)
      }
    }
  }
  globalThis.Request = TestRequest as typeof Request
}

afterEach(() => {
  cleanup()
  window.localStorage.clear()
})

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => undefined,
    removeListener: () => undefined,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    dispatchEvent: () => false,
  }),
})
