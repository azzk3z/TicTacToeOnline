export const backendUnavailable = import.meta.env.DEV
  ? 'Không kết nối được với backend ở cổng 8080. Hãy khởi động backend rồi thử lại.'
  : 'Máy chủ tạm thời không khả dụng. Vui lòng thử lại sau.'

const apiErrorMessages = {
  403: 'Yêu cầu đã hết hiệu lực. Vui lòng thử lại.',
  502: backendUnavailable,
}

export class ApiError extends Error {
  constructor(status, payload) {
    super(payload?.message || apiErrorMessages[status] || 'Yêu cầu thất bại. Vui lòng thử lại.')
    this.status = status
    this.fields = payload?.fieldErrors || {}
  }
}

export async function api(path, { method = 'GET', body, signal } = {}) {
  const headers = { Accept: 'application/json' }
  if (method !== 'GET' && path !== 'auth/register') {
    const { headerName, token } = await api('auth/csrf', { signal })
    headers[headerName] = token
  }
  if (body) {
    headers['Content-Type'] = 'application/json'
  }

  const response = await fetch('/api/' + path, {
    method,
    headers,
    signal,
    body: body ? JSON.stringify(body) : undefined,
  })
  const contentType = response.headers.get('content-type')
  const payload = contentType?.includes('application/json') ? await response.json() : null
  if (!response.ok) {
    throw new ApiError(response.status, payload)
  }
  return payload
}
