import { vi } from 'vitest'

export const csrf = { headerName: 'X-XSRF-TOKEN', token: 'test-csrf-token' }

export function mockApi(routes = {}) {
  const responses = {
    'GET /api/auth/me': [401],
    'GET /api/auth/csrf': [200, csrf],
    'GET /api/rooms/current': [204],
    ...routes,
  }

  const fetch = vi.fn(async (url, options) => {
    const key = options.method + ' ' + url
    const route = responses[key]
    if (!route) {
      throw new Error('Unexpected request ' + key)
    }

    const [status, payload] = typeof route === 'function' ? await route(options) : route
    if (payload === undefined) {
      return new Response(null, { status })
    }
    return Response.json(payload, { status })
  })

  vi.stubGlobal('fetch', fetch)
  return (method, url) => fetch.mock.calls
    .filter(([path, options]) => path === url && options.method === method)
    .map(([, options]) => options)
}
