import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import App from '../App'
import { csrf, mockApi } from './mockApi'

const account = {
  id: 1,
  username: 'player_one',
  email: 'player@example.test',
  displayName: 'Player One',
}
const password = 'password123'
const newPassword = 'password-new'
const resetToken = 't'.repeat(43)

function openApp(routes = {}, fragment = '') {
  window.history.replaceState(null, '', '/' + fragment)
  const requests = mockApi(routes)
  const user = userEvent.setup()
  render(<App />)
  return { user, requests }
}

async function openRegister(user) {
  await user.click(await screen.findByRole('button', { name: 'Đăng ký' }))
}

async function fillRegister(user, values = {}) {
  await user.type(screen.getByLabelText('Tên đăng nhập'), account.username)
  await user.type(screen.getByLabelText('Tên hiển thị (không bắt buộc)'), account.displayName)
  await user.type(screen.getByLabelText('Email'), values.email ?? account.email)
  await user.type(screen.getByLabelText('Mật khẩu', { exact: true }), password)
  await user.type(screen.getByLabelText('Nhập lại mật khẩu'), values.confirmation ?? password)
}

async function fillLogin(user, identifier = account.username) {
  await user.type(await screen.findByLabelText('Tên đăng nhập hoặc email'), identifier)
  await user.type(screen.getByLabelText('Mật khẩu'), password)
}

async function openForgotPassword(user, email) {
  await user.click(await screen.findByRole('button', { name: 'Quên mật khẩu?' }))
  await user.type(screen.getByLabelText('Email'), email)
}

async function fillResetPassword(user, confirmation = newPassword) {
  await user.type(await screen.findByLabelText('Mật khẩu mới', { exact: true }), newPassword)
  await user.type(screen.getByLabelText('Nhập lại mật khẩu'), confirmation)
}

describe('Đăng ký tài khoản', () => {
  it('gửi dữ liệu đăng ký rồi chuyển về màn hình đăng nhập với tên đã điền', async () => {
    const { user, requests } = openApp({ 'POST /api/auth/register': [201, account] })
    await openRegister(user)
    await fillRegister(user)

    await user.click(screen.getByRole('button', { name: 'Tạo tài khoản' }))

    expect(await screen.findByText(/Tạo tài khoản thành công/)).toBeVisible()
    expect(screen.getByLabelText('Tên đăng nhập hoặc email')).toHaveValue(account.username)
    const [request] = requests('POST', '/api/auth/register')
    expect(JSON.parse(request.body)).toEqual({
      username: account.username,
      displayName: account.displayName,
      email: account.email,
      password,
    })
    expect(requests('GET', '/api/auth/csrf')).toHaveLength(0)
  })

  it.each([
    ['username', 'Tên đăng nhập', 'Username đã được sử dụng'],
    ['email', 'Email', 'Email đã được sử dụng'],
  ])('hiển thị lỗi trùng %s tại đúng ô', async (field, label, message) => {
    const { user } = openApp({
      'POST /api/auth/register': [409, { message, fieldErrors: { [field]: message } }],
    })
    await openRegister(user)
    await fillRegister(user)

    await user.click(screen.getByRole('button', { name: 'Tạo tài khoản' }))

    expect(await screen.findByText(message)).toBeVisible()
    expect(screen.getByLabelText(label, { exact: true })).toHaveAccessibleDescription(message)
  })

  it('không gửi yêu cầu khi hai mật khẩu khác nhau', async () => {
    const { user, requests } = openApp()
    await openRegister(user)
    await fillRegister(user, { confirmation: 'different-password' })

    await user.click(screen.getByRole('button', { name: 'Tạo tài khoản' }))

    expect(screen.getByLabelText('Nhập lại mật khẩu')).toHaveAccessibleDescription('Hai mật khẩu chưa khớp')
    expect(requests('POST', '/api/auth/register')).toHaveLength(0)
  })

  it('chặn email sai định dạng trước khi gọi API', async () => {
    const { user, requests } = openApp()
    await openRegister(user)
    await fillRegister(user, { email: 'invalid-email' })

    await user.click(screen.getByRole('button', { name: 'Tạo tài khoản' }))

    expect(screen.getByLabelText('Email')).toBeInvalid()
    expect(requests('POST', '/api/auth/register')).toHaveLength(0)
  })
})

describe('Đăng nhập', () => {
  it.each([account.username, account.email])('đăng nhập bằng %s và mở trang chủ', async (identifier) => {
    const { user, requests } = openApp({ 'POST /api/auth/login': [200, account] })
    await fillLogin(user, identifier)

    await user.click(screen.getByRole('button', { name: 'Đăng nhập', exact: true }))

    expect(await screen.findByRole('heading', { name: /Chơi Tic Tac Toe/ })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Đăng xuất' })).toBeVisible()
    const [request] = requests('POST', '/api/auth/login')
    expect(JSON.parse(request.body)).toEqual({ identifier, password })
    expect(request.headers[csrf.headerName]).toBe(csrf.token)
  })

  it('hiển thị lỗi khi tên đăng nhập hoặc mật khẩu không đúng', async () => {
    const message = 'Tên đăng nhập, email hoặc mật khẩu chưa đúng'
    const { user } = openApp({ 'POST /api/auth/login': [401, { message }] })
    await fillLogin(user)

    await user.click(screen.getByRole('button', { name: 'Đăng nhập', exact: true }))

    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.getByRole('heading', { name: 'Đăng nhập' })).toBeVisible()
  })

  it('chặn biểu mẫu thiếu mật khẩu', async () => {
    const { user, requests } = openApp()
    await user.type(await screen.findByLabelText('Tên đăng nhập hoặc email'), account.username)

    await user.click(screen.getByRole('button', { name: 'Đăng nhập', exact: true }))

    expect(screen.getByLabelText('Mật khẩu')).toBeInvalid()
    expect(requests('POST', '/api/auth/login')).toHaveLength(0)
  })

  it('hiển thị thông báo kết nối khi yêu cầu đăng nhập gặp lỗi mạng', async () => {
    const { user } = openApp({
      'POST /api/auth/login': () => { throw new TypeError('Network unavailable') },
    })
    await fillLogin(user)

    await user.click(screen.getByRole('button', { name: 'Đăng nhập', exact: true }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/Không kết nối được với backend|Máy chủ tạm thời/)
  })
})

describe('Đăng xuất', () => {
  it('gửi CSRF và quay về đăng nhập khi máy chủ trả 204', async () => {
    const { user, requests } = openApp({
      'GET /api/auth/me': [200, account],
      'POST /api/auth/logout': [204],
    })

    await user.click(await screen.findByRole('button', { name: 'Đăng xuất' }))

    expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeVisible()
    const [request] = requests('POST', '/api/auth/logout')
    expect(request.headers[csrf.headerName]).toBe(csrf.token)
    expect(request.body).toBeUndefined()
  })

  it('giữ màn hình hiện tại và báo lỗi nếu máy chủ từ chối đăng xuất', async () => {
    const message = 'Không thể đăng xuất lúc này'
    const { user } = openApp({
      'GET /api/auth/me': [200, account],
      'POST /api/auth/logout': [503, { message }],
    })

    await user.click(await screen.findByRole('button', { name: 'Đăng xuất' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.getByRole('button', { name: 'Đăng xuất' })).toBeEnabled()
  })

  it('cho phép thử lại khi máy chủ từ chối mã CSRF', async () => {
    const { user } = openApp({
      'GET /api/auth/me': [200, account],
      'POST /api/auth/logout': [403],
    })

    await user.click(await screen.findByRole('button', { name: 'Đăng xuất' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Yêu cầu đã hết hiệu lực. Vui lòng thử lại.')
    expect(screen.getByRole('button', { name: 'Đăng xuất' })).toBeEnabled()
  })
})

describe('Quên mật khẩu', () => {
  it('gửi email và hiển thị thông báo chung sau khi yêu cầu được tiếp nhận', async () => {
    const { user, requests } = openApp({ 'POST /api/auth/forgot-password': [202] })
    await openForgotPassword(user, account.email)

    await user.click(screen.getByRole('button', { name: 'Gửi liên kết' }))

    expect(await screen.findByRole('status')).toHaveTextContent(/Nếu email này đã được đăng ký/)
    const [request] = requests('POST', '/api/auth/forgot-password')
    expect(JSON.parse(request.body)).toEqual({ email: account.email })
  })

  it('hiển thị lỗi máy chủ khi chức năng gửi email chưa được cấu hình', async () => {
    const message = 'Chức năng gửi email đặt lại mật khẩu chưa được cấu hình'
    const { user } = openApp({ 'POST /api/auth/forgot-password': [503, { message }] })
    await openForgotPassword(user, account.email)

    await user.click(screen.getByRole('button', { name: 'Gửi liên kết' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(message)
  })

  it('chặn email sai định dạng', async () => {
    const { user, requests } = openApp()
    await openForgotPassword(user, 'invalid-email')

    await user.click(screen.getByRole('button', { name: 'Gửi liên kết' }))

    expect(screen.getByLabelText('Email')).toBeInvalid()
    expect(requests('POST', '/api/auth/forgot-password')).toHaveLength(0)
  })
})

describe('Đặt lại mật khẩu', () => {
  it('lấy mã từ liên kết, đặt mật khẩu mới và quay về đăng nhập', async () => {
    const { user, requests } = openApp({ 'POST /api/auth/reset-password': [204] }, '#reset=' + resetToken)
    await fillResetPassword(user)

    await user.click(screen.getByRole('button', { name: 'Lưu mật khẩu' }))

    expect(await screen.findByRole('status')).toHaveTextContent(/Đã đổi mật khẩu/)
    expect(screen.getByRole('heading', { name: 'Đăng nhập' })).toBeVisible()
    expect(window.location.hash).toBe('')
    const [request] = requests('POST', '/api/auth/reset-password')
    expect(JSON.parse(request.body)).toEqual({ token: resetToken, password: newPassword })
  })

  it('chuyển đến yêu cầu liên kết mới khi mã đặt lại sai định dạng', async () => {
    const { user, requests } = openApp({}, '#reset=invalid')

    expect(await screen.findByRole('alert')).toHaveTextContent(/Liên kết đặt lại mật khẩu không hợp lệ/)
    expect(screen.queryByRole('button', { name: 'Lưu mật khẩu' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Yêu cầu liên kết mới' }))
    expect(screen.getByRole('heading', { name: 'Quên mật khẩu?' })).toBeVisible()
    expect(requests('POST', '/api/auth/reset-password')).toHaveLength(0)
  })

  it('hiển thị lỗi nếu liên kết đã hết hạn hoặc được dùng', async () => {
    const message = 'Liên kết đặt lại mật khẩu đã hết hạn hoặc đã được dùng'
    const { user } = openApp({
      'POST /api/auth/reset-password': [400, { message, fieldErrors: { token: message } }],
    }, '#reset=' + resetToken)
    await fillResetPassword(user)

    await user.click(screen.getByRole('button', { name: 'Lưu mật khẩu' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.getByRole('button', { name: 'Yêu cầu liên kết mới' })).toBeVisible()
  })

  it('không gửi yêu cầu khi mật khẩu xác nhận khác nhau', async () => {
    const { user, requests } = openApp({}, '#reset=' + resetToken)
    await fillResetPassword(user, 'different-password')

    await user.click(screen.getByRole('button', { name: 'Lưu mật khẩu' }))

    expect(screen.getByLabelText('Nhập lại mật khẩu')).toHaveAccessibleDescription('Hai mật khẩu chưa khớp')
    expect(requests('POST', '/api/auth/reset-password')).toHaveLength(0)
  })

  it('chặn biểu mẫu thiếu mật khẩu mới', async () => {
    const { user, requests } = openApp({}, '#reset=' + resetToken)

    await user.click(await screen.findByRole('button', { name: 'Lưu mật khẩu' }))

    expect(screen.getByLabelText('Mật khẩu mới', { exact: true })).toBeInvalid()
    expect(requests('POST', '/api/auth/reset-password')).toHaveLength(0)
  })
})
