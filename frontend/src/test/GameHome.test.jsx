import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import GameHome from '../GameHome'
import { csrf, mockApi } from './mockApi'

const account = { id: 1, username: 'guest', email: 'guest@example.test', displayName: 'Guest Player' }
const host = { id: 2, displayName: 'Host Player' }
const player = { id: account.id, displayName: account.displayName }
const code = '6HK9MQ2R'
const createdRoom = { code, host: player, guest: null }
const joinedRoom = { code, host, guest: player }

function openHome(routes = {}) {
  const requests = mockApi(routes)
  const user = userEvent.setup()
  const onSessionExpired = vi.fn()
  render(<GameHome user={account} onLogout={vi.fn()} onSessionExpired={onSessionExpired} />)
  return { user, requests, onSessionExpired }
}

async function enterRoomCode(user, value) {
  await user.type(await screen.findByRole('textbox', { name: 'Mã phòng' }), value)
  await user.click(screen.getByRole('button', { name: 'Vào phòng' }))
}

describe('Tạo phòng chơi', () => {
  it('tạo phòng, mở phòng chờ và hiển thị mã cùng chỗ trống cho người thứ hai', async () => {
    let room
    const { user, requests } = openHome({
      'GET /api/rooms/current': () => room ? [200, room] : [204],
      'POST /api/rooms': () => {
        room = createdRoom
        return [200, room]
      },
    })

    await user.click(await screen.findByRole('button', { name: 'Tạo phòng mới' }))

    expect(await screen.findByRole('heading', { name: 'Phòng chờ' })).toBeVisible()
    expect(screen.getByRole('textbox', { name: 'Mã phòng' })).toHaveValue(code)
    expect(screen.getByText('1 / 2 người chơi')).toBeVisible()
    expect(within(screen.getByRole('list')).getByText('Chủ phòng')).toBeVisible()
    const [request] = requests('POST', '/api/rooms')
    expect(request.headers[csrf.headerName]).toBe(csrf.token)
    expect(request.body).toBeUndefined()
  })

  it('giữ trang chủ và báo lỗi khi không thể tạo phòng', async () => {
    const message = 'Không thể tạo phòng lúc này'
    const { user } = openHome({ 'POST /api/rooms': [500, { message }] })

    await user.click(await screen.findByRole('button', { name: 'Tạo phòng mới' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.getByRole('button', { name: 'Tạo phòng mới' })).toBeEnabled()
    expect(screen.queryByRole('heading', { name: 'Phòng chờ' })).not.toBeInTheDocument()
  })

  it('xử lý phiên đăng nhập hết hạn khi yêu cầu tạo phòng trả 401', async () => {
    const { user, onSessionExpired } = openHome({ 'POST /api/rooms': [401] })

    await user.click(await screen.findByRole('button', { name: 'Tạo phòng mới' }))

    await waitFor(() => expect(onSessionExpired).toHaveBeenCalledOnce())
    expect(screen.queryByRole('heading', { name: 'Phòng chờ' })).not.toBeInTheDocument()
  })

  it('chỉ gửi một yêu cầu khi bấm tạo phòng liên tiếp', async () => {
    let finishCreation
    const pending = new Promise((resolve) => { finishCreation = resolve })
    let room
    const { user, requests } = openHome({
      'GET /api/rooms/current': () => room ? [200, room] : [204],
      'POST /api/rooms': async () => {
        room = await pending
        return [200, room]
      },
    })

    await user.dblClick(await screen.findByRole('button', { name: 'Tạo phòng mới' }))

    expect(screen.getByRole('button', { name: 'Đang tạo phòng…' })).toBeDisabled()
    expect(requests('POST', '/api/rooms')).toHaveLength(1)
    finishCreation(createdRoom)
    expect(await screen.findByRole('heading', { name: 'Phòng chờ' })).toBeVisible()
  })
})

describe('Tham gia phòng chơi', () => {
  it('chuẩn hóa mã trước khi gửi và hiển thị hai người trong phòng chờ', async () => {
    let room
    const { user, requests } = openHome({
      'GET /api/rooms/current': () => room ? [200, room] : [204],
      'POST /api/rooms/join': () => {
        room = joinedRoom
        return [200, room]
      },
    })

    await enterRoomCode(user, ' 6hk9mq2r ')

    expect(await screen.findByRole('heading', { name: 'Phòng chờ' })).toBeVisible()
    expect(screen.getByText('2 / 2 người chơi')).toBeVisible()
    const seats = within(screen.getByRole('list'))
    expect(seats.getByText(host.displayName)).toBeVisible()
    expect(seats.getByText(player.displayName)).toBeVisible()
    const [request] = requests('POST', '/api/rooms/join')
    expect(JSON.parse(request.body)).toEqual({ code })
  })

  it.each([
    [404, 'Không tìm thấy phòng với mã này'],
    [409, 'Phòng đã đủ hai người chơi'],
    [409, 'Hãy rời phòng hiện tại trước khi tham gia phòng khác'],
  ])('hiển thị lỗi %i ở ô mã phòng khi API trả về "%s"', async (status, message) => {
    const { user } = openHome({ 'POST /api/rooms/join': [status, { message }] })

    await enterRoomCode(user, code)

    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.getByRole('textbox', { name: 'Mã phòng' })).toHaveAccessibleDescription(message)
    expect(screen.queryByRole('heading', { name: 'Phòng chờ' })).not.toBeInTheDocument()
  })

  it('không gọi API với mã sai định dạng', async () => {
    const { user, requests } = openHome()

    await enterRoomCode(user, 'I0ABCD23')

    expect(await screen.findByRole('alert')).toHaveTextContent('Mã phòng phải có 8 ký tự hợp lệ.')
    expect(requests('POST', '/api/rooms/join')).toHaveLength(0)
  })

  it('chặn biểu mẫu khi chưa nhập mã phòng', async () => {
    const { user, requests } = openHome()

    await user.click(await screen.findByRole('button', { name: 'Vào phòng' }))

    expect(screen.getByRole('textbox', { name: 'Mã phòng' })).toBeInvalid()
    expect(requests('POST', '/api/rooms/join')).toHaveLength(0)
  })
})
