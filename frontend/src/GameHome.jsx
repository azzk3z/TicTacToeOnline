import { useEffect, useRef, useState } from 'react'
import { api, ApiError, backendUnavailable } from './api'
import { BoardArt, Brand } from './Brand'
import './GameHome.css'

const roomCodePattern = /^[A-HJ-NP-Z2-9]{8}$/

function failureMessage(error) {
  return error instanceof ApiError ? error.fields.code || error.message : backendUnavailable
}

function HomeActions({ onCreate, onJoin, busy, error }) {
  const [code, setCode] = useState('')

  function submit(event) {
    event.preventDefault()
    onJoin(code.trim().toUpperCase())
  }

  return (
    <>
      <section className="game-hero" aria-labelledby="home-title">
        <div className="game-hero-copy">
          <h1 id="home-title">Chơi Tic Tac Toe<br /><span>cùng bạn bè</span></h1>
          <p>Tạo phòng riêng hoặc nhập mã phòng để vào cùng bạn bè.</p>
        </div>
        <BoardArt />
      </section>
      {error && error.action !== 'join' && <p className="feedback feedback-error" role="alert">{error.message}</p>}
      <div className="room-actions">
        <section className="room-card" aria-labelledby="create-title">
          <span className="room-action-icon" aria-hidden="true">+</span>
          <h2 id="create-title">Tạo phòng</h2>
          <p>Nhận một mã riêng để mời người chơi thứ hai.</p>
          <button type="button" className="primary-button room-create-button" disabled={!!busy} onClick={onCreate}>
            <span>{busy === 'create' ? 'Đang tạo phòng…' : 'Tạo phòng mới'}</span><span aria-hidden="true">→</span>
          </button>
        </section>
        <section className="room-card" aria-labelledby="join-title">
          <span className="room-action-icon room-action-icon-join" aria-hidden="true">↳</span>
          <h2 id="join-title">Tham gia phòng</h2>
          <p>Nhập mã 8 ký tự người tạo phòng đã gửi cho bạn.</p>
          <form className="room-join-form" onSubmit={submit}>
            <label className="game-sr-only" htmlFor="join-code">Mã phòng</label>
            <input id="join-code" value={code} onChange={(event) => setCode(event.target.value.toUpperCase())}
              placeholder="6HK9MQ2R" autoComplete="off" autoCapitalize="characters" spellCheck={false}
              aria-invalid={error?.action === 'join' ? true : undefined}
              aria-describedby={error?.action === 'join' ? 'join-code-error' : undefined} required />
            <button type="submit" className="room-secondary-button" disabled={!!busy}>
              {busy === 'join' ? 'Đang vào…' : 'Vào phòng'} <span aria-hidden="true">→</span>
            </button>
            {error?.action === 'join' && <span className="field-error" id="join-code-error" role="alert">{error.message}</span>}
          </form>
        </section>
      </div>
    </>
  )
}

function RoomSeat({ player, isYou, host }) {
  return (
    <li className={'room-seat' + (player ? '' : ' room-seat-empty')}>
      <span className={'room-seat-mark ' + (host ? 'room-seat-x' : 'room-seat-o')} aria-hidden="true">{host ? 'X' : 'O'}</span>
      <div className="room-seat-copy">
        <strong>{player ? player.displayName : 'Đang chờ người chơi'}</strong>
        <span>{host ? 'Chủ phòng' : 'Người chơi thứ hai'}</span>
      </div>
      {isYou && <span className="room-you">Bạn</span>}
    </li>
  )
}

function WaitingRoom({ room, userId, onLeave, busy, error, syncError }) {
  const codeField = useRef(null)
  const [copyStatus, setCopyStatus] = useState('')
  const full = !!room.guest
  const isHost = room.host.id === userId

  async function copyCode() {
    try {
      await navigator.clipboard.writeText(room.code)
      setCopyStatus('Đã sao chép mã phòng.')
    } catch {
      codeField.current?.focus()
      codeField.current?.select()
      setCopyStatus('Mã đã được chọn. Bạn có thể sao chép thủ công.')
    }
  }

  return (
    <section aria-labelledby="room-title">
      <div className="room-heading">
        <div>
          <h1 id="room-title">Phòng chờ</h1>
          <p role="status">{full ? 'Phòng đã đủ hai người chơi.' : 'Gửi mã phòng cho bạn bè để họ tham gia.'}</p>
        </div>
        <span className={'room-count' + (full ? ' room-count-full' : '')}>
          <span aria-hidden="true" /> {full ? '2 / 2' : '1 / 2'} người chơi
        </span>
      </div>
      {syncError && <p className="feedback feedback-error" role="status">{syncError} Đang thử lại…</p>}
      {error && <p className="feedback feedback-error" role="alert">{error.message}</p>}
      <div className="room-lobby">
        <div className="room-card room-players">
          <h2>Người chơi</h2>
          <ul className="room-seats">
            <RoomSeat player={room.host} isYou={isHost} host />
            <RoomSeat player={room.guest} isYou={room.guest?.id === userId} />
          </ul>
        </div>
        <div className="room-card room-invite">
          <h2><label htmlFor="room-invite-code">Mã phòng</label></h2>
          <input id="room-invite-code" ref={codeField} className="room-code" value={room.code} readOnly
            onFocus={(event) => event.currentTarget.select()} />
          <button type="button" className="room-secondary-button" onClick={copyCode}>Sao chép mã</button>
          <p className="room-copy-status" role="status">{copyStatus}</p>
        </div>
      </div>
      <div className="room-leave-row">
        <button type="button" className="room-secondary-button" disabled={!!busy} onClick={onLeave}>
          <span aria-hidden="true">←</span> {busy === 'leave' ? 'Đang rời phòng…' : 'Rời phòng'}
        </button>
        {isHost && <p>Phòng sẽ đóng khi chủ phòng rời đi.</p>}
      </div>
    </section>
  )
}

export default function GameHome({ user, onLogout, signingOut, accountError, onSessionExpired }) {
  const [room, setRoom] = useState(undefined)
  const [busy, setBusy] = useState('')
  const [error, setError] = useState(null)
  const [syncError, setSyncError] = useState('')
  const [notice, setNotice] = useState('')
  const [retry, setRetry] = useState(0)
  const roomCode = room?.code

  useEffect(() => {
    if (busy || signingOut) return

    const controller = new AbortController()
    let timer

    async function refresh() {
      try {
        const current = await api('rooms/current', { signal: controller.signal })
        if (controller.signal.aborted) return
        setSyncError('')
        setRoom(current)
        if (!current && roomCode) {
          setError(null)
          setNotice('Phòng đã đóng. Bạn đã quay về trang chủ.')
        }
      } catch (failure) {
        if (controller.signal.aborted) return
        if (failure.status === 401) {
          controller.abort()
          onSessionExpired()
          return
        }
        setSyncError(failureMessage(failure))
      } finally {
        if (roomCode && !controller.signal.aborted) {
          timer = window.setTimeout(refresh, 2500)
        }
      }
    }

    refresh()
    return () => {
      controller.abort()
      window.clearTimeout(timer)
    }
  }, [roomCode, busy, signingOut, retry, onSessionExpired])

  async function roomAction(action, path, options) {
    setBusy(action)
    setError(null)
    setNotice('')
    try {
      const updatedRoom = await api(path, options)
      setSyncError('')
      setRoom(updatedRoom)
      if (action === 'leave') {
        setNotice('Bạn đã rời phòng.')
      }
    } catch (failure) {
      if (failure.status === 401) {
        onSessionExpired()
      } else {
        setError({ action, message: failureMessage(failure) })
      }
    } finally {
      setBusy('')
    }
  }

  function join(code) {
    if (!roomCodePattern.test(code)) {
      setError({ action: 'join', message: 'Mã phòng phải có 8 ký tự hợp lệ.' })
      return
    }
    roomAction('join', 'rooms/join', { method: 'POST', body: { code } })
  }

  const accountName = user.displayName || user.username
  const controlsBusy = signingOut ? 'logout' : busy

  return (
    <div className="game-shell">
      <header className="game-topbar">
        <div className="game-topbar-inner">
          <Brand />
          <div className="game-account">
            <span className="game-avatar" aria-hidden="true">{Array.from(accountName)[0].toLocaleUpperCase('vi')}</span>
            <div className="game-account-text">
              <strong title={user.username}>{accountName}</strong>
              <span>{user.email}</span>
            </div>
            <button type="button" className="game-logout" disabled={!!controlsBusy} onClick={onLogout}>
              {signingOut ? 'Đang đăng xuất…' : 'Đăng xuất'}
            </button>
          </div>
        </div>
      </header>
      <main className="game-content">
        {accountError && <p className="feedback feedback-error" role="alert">{accountError}</p>}
        {notice && !room && <p className="feedback feedback-success" role="status">{notice}</p>}
        {room === undefined ? (
          <div className="room-card room-loading">
            {syncError ? (
              <>
                <p role="alert">{syncError}</p>
                <button className="room-secondary-button" type="button" disabled={!!controlsBusy}
                  onClick={() => { setSyncError(''); setRetry((count) => count + 1) }}>Thử lại</button>
              </>
            ) : <p role="status">Đang tải trang chủ…</p>}
          </div>
        ) : room ? (
          <WaitingRoom key={room.code} room={room} userId={user.id} busy={controlsBusy} error={error} syncError={syncError}
            onLeave={() => roomAction('leave', 'rooms/current', { method: 'DELETE' })} />
        ) : (
          <HomeActions busy={controlsBusy} error={error} onJoin={join}
            onCreate={() => roomAction('create', 'rooms', { method: 'POST' })} />
        )}
      </main>
    </div>
  )
}
