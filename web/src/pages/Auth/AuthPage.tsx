import { useEffect, useState, type FormEvent } from 'react'
import { motion, AnimatePresence, useReducedMotion } from 'framer-motion'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/Button'
import { Input } from '@/components/Input'
import { SegmentedControl } from '@/components/SegmentedControl'
import { userApi } from '@/api/user'
import { useAuthStore } from '@/stores/authStore'
import { ApiError } from '@/api/client'
import { toast } from '@/stores/toastStore'
import styles from './AuthPage.module.css'

type Mode = 'password' | 'code' | 'register'

const modeCopy: Record<Mode, { title: string; subtitle: string; action: string }> = {
  password: {
    title: 'Continue with a password',
    subtitle: 'Sign in to sync your conversations and realtime messages.',
    action: 'Enter InfiniteChat',
  },
  code: {
    title: 'Continue with a code',
    subtitle: 'Get in securely without remembering a password.',
    action: 'Verify and enter',
  },
  register: {
    title: 'Create your space',
    subtitle: 'It takes a minute to start your first conversation.',
    action: 'Sign up and enter',
  },
}

export function AuthPage() {
  const navigate = useNavigate()
  const reduceMotion = useReducedMotion()
  const setFromLogin = useAuthStore((s) => s.setFromLogin)
  const [mode, setMode] = useState<Mode>('password')
  const [account, setAccount] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [code, setCode] = useState('')
  const [nickname, setNickname] = useState('')
  const [loading, setLoading] = useState(false)
  const [captchaLoading, setCaptchaLoading] = useState(false)
  const [captchaWait, setCaptchaWait] = useState(0)
  const [error, setError] = useState('')
  const [shake, setShake] = useState(false)

  const showError = (msg: string) => {
    setError(msg)
    setShake(true)
    window.setTimeout(() => setShake(false), 420)
  }

  useEffect(() => {
    if (captchaWait <= 0) return
    const timer = window.setInterval(() => setCaptchaWait((value) => Math.max(0, value - 1)), 1000)
    return () => window.clearInterval(timer)
  }, [captchaWait])

  const onSendCaptcha = async () => {
    if (!account.trim()) {
      showError('Enter a phone number or email address first')
      return
    }
    setError('')
    setCaptchaLoading(true)
    try {
      await userApi.sendCaptcha(account)
      setCaptchaWait(60)
      toast.success('A verification code has been sent, please check your inbox')
    } catch (e) {
      showError(e instanceof ApiError ? e.message : 'Failed to send')
    } finally {
      setCaptchaLoading(false)
    }
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (!account.trim()) {
      showError('Enter your account')
      return
    }
    if (mode !== 'password' && !code.trim()) {
      showError('Enter the verification code')
      return
    }
    if (mode === 'register' && password !== confirmPassword) {
      showError('The two passwords do not match')
      return
    }
    setLoading(true)
    setError('')
    try {
      let data
      if (mode === 'password') {
        data = await userApi.loginPassword(account, password)
      } else if (mode === 'code') {
        data = await userApi.loginCode(account, code)
      } else {
        data = await userApi.register({
          account,
          password,
          confirmPassword,
          code,
          nickname,
        })
      }
      setFromLogin(data)
      if (!data.wsServerUri) {
        toast.warning('Signed in, but no realtime node was assigned')
      } else {
        toast.success(mode === 'register' ? 'Welcome to InfiniteChat' : 'Welcome back')
      }
      navigate('/app/chat', { replace: true })
    } catch (err) {
      showError(err instanceof ApiError ? err.message : 'The operation failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className={styles.page}>
      <section className={styles.hero} aria-label="InfiniteChat">
        <div className={styles.heroAtmosphere}>
          <span className={styles.orbA} />
          <span className={styles.orbB} />
          <span className={styles.grain} />
        </div>
        <motion.div
          className={styles.heroCopy}
          initial={reduceMotion ? false : { opacity: 0, y: 18 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
        >
          <div className={styles.brandLockup}>
            <span className={styles.brandMark}>∞</span>
            <p className={styles.brand}>InfiniteChat</p>
          </div>
          <p className={styles.eyebrow}>CONVERSATIONS, BEAUTIFULLY DELIVERED</p>
          <h1 className={styles.headline}>
            Every word that matters<br />
            <em>deserves to arrive clearly.</em>
          </h1>
          <p className={styles.lead}>A calmer, more dependable place to talk in real time. Less noise, more genuine connection.</p>
          <div className={styles.featureRow} aria-label="What it does">
            <span>Realtime delivery</span>
            <span>Synced everywhere</span>
            <span>Secure connection</span>
          </div>
        </motion.div>
        <div className={styles.conversationPreview} aria-hidden="true">
          <div className={styles.previewHeader}>
            <span className={styles.previewAvatar}>M</span>
            <span><strong>Product team</strong><small>5 members online</small></span>
            <i />
          </div>
          <div className={styles.previewBody}>
            <div className={styles.messageOther}>The new build is ready. Shall we take a look tonight?</div>
            <div className={styles.messageMine}>Sure, let's focus on messaging and mobile ✨</div>
            <div className={styles.messageOther}>Got it, see you at 20:00.</div>
          </div>
          <div className={styles.previewComposer}><span>Type a message...</span><b>↑</b></div>
        </div>
        <div className={styles.heroFooter}>
          <span>PRIVATE CHAT</span><i /> <span>GROUPS</span><i /> <span>REAL-TIME</span>
        </div>
      </section>

      <section className={styles.panel}>
        <motion.div
          className={[styles.panelInner, shake ? styles.shake : ''].join(' ')}
          initial={reduceMotion ? false : { opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.55, delay: 0.08, ease: [0.22, 1, 0.36, 1] }}
        >
          <div className={styles.mobileBrand}>
            <span>∞</span>
            <strong>InfiniteChat</strong>
          </div>
          <AnimatePresence mode="wait">
            <motion.div
              key={mode}
              initial={reduceMotion ? false : { opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={reduceMotion ? undefined : { opacity: 0, y: -6 }}
              transition={{ duration: 0.22 }}
            >
              <h2 className={styles.title}>{modeCopy[mode].title}</h2>
              <p className={styles.subtitle}>{modeCopy[mode].subtitle}</p>
            </motion.div>
          </AnimatePresence>

          <SegmentedControl
            ariaLabel="Sign-in method"
            value={mode}
            onChange={(v) => {
              setMode(v)
              setError('')
              setCode('')
            }}
            options={[
              { value: 'password', label: 'Password' },
              { value: 'code', label: 'Code' },
              { value: 'register', label: 'Sign up' },
            ]}
          />

          <form className={styles.form} onSubmit={onSubmit}>
            <Input
              label="Account"
              value={account}
              onChange={(e) => {
                setAccount(e.target.value)
                if (error) setError('')
              }}
              placeholder="Phone number or email"
              autoComplete="username"
              required
            />
            {mode === 'register' ? (
              <Input
                label="Nickname"
                value={nickname}
                onChange={(e) => setNickname(e.target.value)}
                placeholder="The name others will see"
                required
              />
            ) : null}
            {mode !== 'code' ? (
              <Input
                label="Password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
                required
              />
            ) : null}
            {mode === 'register' ? (
              <Input
                label="Confirm password"
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                autoComplete="new-password"
                required
              />
            ) : null}
            {mode !== 'password' ? (
              <div className={styles.captchaRow}>
                <Input
                  label="Verification code"
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                  required
                />
                <Button
                  type="button"
                  variant="secondary"
                  loading={captchaLoading}
                  onClick={onSendCaptcha}
                  disabled={captchaWait > 0 || !account.trim()}
                >
                  {captchaWait > 0 ? `Retry in ${captchaWait}s` : 'Get a code'}
                </Button>
              </div>
            ) : null}

            <div className={styles.feedback} aria-live="polite">
              {error ? <p className={styles.error} role="alert">{error}</p> : <span>Your details are only used to verify who you are</span>}
            </div>

            <Button type="submit" block loading={loading}>
              {modeCopy[mode].action}
            </Button>
          </form>
          <p className={styles.legal}>By continuing you agree to use InfiniteChat safely and respectfully.</p>
        </motion.div>
      </section>
    </div>
  )
}
