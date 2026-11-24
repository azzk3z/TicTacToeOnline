import { useCallback, useEffect, useState } from 'react'
import { api, ApiError, backendUnavailable } from './api'
import { BoardArt, Brand } from './Brand'
import GameHome from './GameHome'
import './App.css'

const screens = {
  login: {
    title: 'Đăng nhập',
    action: 'Đăng nhập',
  },
  register: {
    title: 'Tạo tài khoản',
    action: 'Tạo tài khoản',
  },
  forgot: {
    title: 'Quên mật khẩu?',
    description: 'Nhập email đã đăng ký để nhận liên kết đặt lại mật khẩu.',
    action: 'Gửi liên kết',
  },
  reset: {
    title: 'Đặt mật khẩu mới',
    description: 'Chọn mật khẩu mới có ít nhất 8 ký tự.',
    action: 'Lưu mật khẩu',
  },
}

const authPaths = {
  login: 'login',
  register: 'register',
  forgot: 'forgot-password',
  reset: 'reset-password',
}

function readResetToken() {
  return new URLSearchParams(window.location.hash.slice(1)).get('reset')
}

function Field({ label, name, errors, ...input }) {
  const error = errors[name]

  return (
    <div className="field">
      <label htmlFor={name}>{label}</label>
      <input
        id={name}
        name={name}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? name + '-error' : undefined}
        {...input}
      />
      {error && <span className="field-error" id={name + '-error'}>{error}</span>}
    </div>
  )
}

function App() {
  const [resetToken, setResetToken] = useState(readResetToken)
  const [screen, setScreen] = useState(() => readResetToken() === null ? 'login' : 'reset')
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [identifier, setIdentifier] = useState('')

  useEffect(() => {
    function openResetLink() {
      const token = readResetToken()
      if (token !== null) {
        setResetToken(token)
        setScreen('reset')
        setError('')
        setNotice('')
        setFieldErrors({})
        window.history.replaceState(window.history.state, '', window.location.pathname + window.location.search)
      }
    }

    openResetLink()
    window.addEventListener('hashchange', openResetLink)
    return () => window.removeEventListener('hashchange', openResetLink)
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    api('auth/me', { signal: controller.signal })
      .then(setUser)
      .catch((failure) => {
        if (!controller.signal.aborted && failure.status !== 401) {
          setError(failure instanceof ApiError ? failure.message : backendUnavailable)
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoading(false)
        }
      })
    return () => controller.abort()
  }, [])

  const changeScreen = useCallback((next, message = '') => {
    setError('')
    setFieldErrors({})
    setNotice(message)
    setScreen(next)
    if (next !== 'reset') {
      setResetToken(null)
    }
  }, [])

  const expireSession = useCallback(() => {
    setUser(null)
    changeScreen('login', 'Phiên đăng nhập đã hết hiệu lực. Hãy đăng nhập lại.')
  }, [changeScreen])

  function showFailure(failure) {
    const message = failure instanceof ApiError ? failure.message : backendUnavailable
    const fields = failure.fields || {}
    setError(Object.keys(fields).length ? '' : message)
    setFieldErrors(fields)
  }

  async function submit(event) {
    event.preventDefault()
    setError('')
    setFieldErrors({})
    setNotice('')

    const { confirmation, ...body } = Object.fromEntries(new FormData(event.currentTarget))
    if (confirmation !== undefined && confirmation !== body.password) {
      setFieldErrors({ confirmation: 'Hai mật khẩu chưa khớp' })
      return
    }
    if (screen === 'reset') {
      body.token = resetToken
    }

    setBusy(true)
    try {
      const account = await api('auth/' + authPaths[screen], { method: 'POST', body })
      if (screen === 'register') {
        setIdentifier(account.username)
        changeScreen('login', 'Tạo tài khoản thành công. Bạn có thể đăng nhập.')
      } else if (screen === 'login') {
        setUser(account)
        setIdentifier(account.username)
      } else if (screen === 'forgot') {
        setNotice('Nếu email này đã được đăng ký, bạn sẽ nhận được liên kết đặt lại mật khẩu. Hãy kiểm tra cả thư rác.')
      } else {
        setUser(null)
        changeScreen('login', 'Đã đổi mật khẩu. Hãy đăng nhập bằng mật khẩu mới.')
      }
    } catch (failure) {
      showFailure(failure)
    } finally {
      setBusy(false)
    }
  }

  async function logout() {
    setBusy(true)
    setError('')
    try {
      await api('auth/logout', { method: 'POST' })
      setUser(null)
      changeScreen('login')
    } catch (failure) {
      showFailure(failure)
    } finally {
      setBusy(false)
    }
  }

  if (!loading && user && screen !== 'reset') {
    return (
      <GameHome key={user.id} user={user} onLogout={logout} signingOut={busy}
        accountError={error} onSessionExpired={expireSession} />
    )
  }

  const active = screens[screen]
  const invalidResetLink = screen === 'reset' && !/^[A-Za-z0-9_-]{43}$/.test(resetToken || '')
  const linkError = invalidResetLink ? 'Liên kết đặt lại mật khẩu không hợp lệ.' : fieldErrors.token

  return (
    <main className="auth-layout">
      <section className="auth-panel" aria-labelledby="screen-title">
        <Brand />
        <div className="auth-content">
          {loading ? (
            <h1 id="screen-title" role="status">Đang tải…</h1>
          ) : (
            <>
              <h1 id="screen-title">{active.title}</h1>
              {active.description && <p className="intro">{active.description}</p>}
              {notice && <p className="feedback feedback-success" role="status">{notice}</p>}
              {linkError || error ? <p className="feedback feedback-error" role="alert">{linkError || error}</p> : null}
              {linkError ? (
                <button className="primary-button" type="button" onClick={() => changeScreen('forgot')}>Yêu cầu liên kết mới</button>
              ) : (
                <form className="auth-form" key={screen} onSubmit={submit}>
                  {screen === 'login' && (
                    <Field label="Tên đăng nhập hoặc email" name="identifier" autoComplete="username" maxLength={100}
                      defaultValue={identifier} errors={fieldErrors} required />
                  )}
                  {screen === 'register' && (
                    <>
                      <Field label="Tên đăng nhập" name="username" autoComplete="username" minLength={3}
                        maxLength={30} pattern="[A-Za-z0-9_]+" title="3–30 ký tự gồm chữ cái, chữ số hoặc dấu gạch dưới"
                        errors={fieldErrors} required />
                      <Field label="Tên hiển thị (không bắt buộc)" name="displayName" autoComplete="nickname"
                        maxLength={50} errors={fieldErrors} />
                    </>
                  )}
                  {(screen === 'register' || screen === 'forgot') && (
                    <Field label="Email" name="email" type="email" autoComplete="email" maxLength={100}
                      errors={fieldErrors} required />
                  )}
                  {screen !== 'forgot' && (
                    <Field label={screen === 'reset' ? 'Mật khẩu mới' : 'Mật khẩu'} name="password" type="password"
                      autoComplete={screen === 'login' ? 'current-password' : 'new-password'}
                      minLength={screen === 'login' ? undefined : 8} maxLength={64} errors={fieldErrors} required />
                  )}
                  {(screen === 'register' || screen === 'reset') && (
                    <Field label="Nhập lại mật khẩu" name="confirmation" type="password" autoComplete="new-password"
                      maxLength={64} errors={fieldErrors} required />
                  )}
                  {screen === 'login' && (
                    <button className="text-button forgot-button" type="button" disabled={busy}
                      onClick={() => changeScreen('forgot')}>Quên mật khẩu?</button>
                  )}
                  <button type="submit" className="primary-button" disabled={busy}>
                    {busy ? 'Đang xử lý…' : active.action}
                  </button>
                </form>
              )}
              <div className="switch-screen">
                {screen === 'login' && <span>Chưa có tài khoản? </span>}
                {screen === 'register' && <span>Đã có tài khoản? </span>}
                <button className="text-button" type="button" disabled={busy}
                  onClick={() => changeScreen(screen === 'login' ? 'register' : 'login')}>
                  {screen === 'login' ? 'Đăng ký' : screen === 'register' ? 'Đăng nhập' : 'Quay lại đăng nhập'}
                </button>
              </div>
            </>
          )}
        </div>
      </section>
      <BoardArt />
    </main>
  )
}

export default App
