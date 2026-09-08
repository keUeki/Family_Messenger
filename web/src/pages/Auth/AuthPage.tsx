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
    title: '用密码继续',
    subtitle: '登录后即可同步会话与实时消息。',
    action: '进入 InfiniteChat',
  },
  code: {
    title: '用验证码继续',
    subtitle: '无需记住密码，也能安全进入。',
    action: '验证并进入',
  },
  register: {
    title: '创建你的空间',
    subtitle: '只需一分钟，开始你的第一段对话。',
    action: '注册并进入',
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
      showError('请先输入手机号或邮箱')
      return
    }
    setError('')
    setCaptchaLoading(true)
    try {
      await userApi.sendCaptcha(account)
      setCaptchaWait(60)
      toast.success('验证码已生成，请注意查收')
    } catch (e) {
      showError(e instanceof ApiError ? e.message : '发送失败')
    } finally {
      setCaptchaLoading(false)
    }
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (!account.trim()) {
      showError('请输入账号')
      return
    }
    if (mode !== 'password' && !code.trim()) {
      showError('请输入验证码')
      return
    }
    if (mode === 'register' && password !== confirmPassword) {
      showError('两次密码不一致')
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
        toast.warning('登录成功，但未分配实时节点')
      } else {
        toast.success(mode === 'register' ? '欢迎加入 InfiniteChat' : '欢迎回来')
      }
      navigate('/app/chat', { replace: true })
    } catch (err) {
      showError(err instanceof ApiError ? err.message : '操作失败')
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
            每一句重要的话，<br />
            <em>都值得清晰抵达。</em>
          </h1>
          <p className={styles.lead}>一个更专注、更可靠的即时沟通空间。少一点干扰，多一点真正的连接。</p>
          <div className={styles.featureRow} aria-label="产品能力">
            <span>实时送达</span>
            <span>多端同步</span>
            <span>安全连接</span>
          </div>
        </motion.div>
        <div className={styles.conversationPreview} aria-hidden="true">
          <div className={styles.previewHeader}>
            <span className={styles.previewAvatar}>M</span>
            <span><strong>产品小组</strong><small>5 位成员在线</small></span>
            <i />
          </div>
          <div className={styles.previewBody}>
            <div className={styles.messageOther}>新版已经准备好了，今晚一起看看？</div>
            <div className={styles.messageMine}>好，重点体验一下消息和移动端 ✨</div>
            <div className={styles.messageOther}>收到，20:00 见。</div>
          </div>
          <div className={styles.previewComposer}><span>输入消息…</span><b>↑</b></div>
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
            ariaLabel="登录方式"
            value={mode}
            onChange={(v) => {
              setMode(v)
              setError('')
              setCode('')
            }}
            options={[
              { value: 'password', label: '密码' },
              { value: 'code', label: '验证码' },
              { value: 'register', label: '注册' },
            ]}
          />

          <form className={styles.form} onSubmit={onSubmit}>
            <Input
              label="账号"
              value={account}
              onChange={(e) => {
                setAccount(e.target.value)
                if (error) setError('')
              }}
              placeholder="手机号或邮箱"
              autoComplete="username"
              required
            />
            {mode === 'register' ? (
              <Input
                label="昵称"
                value={nickname}
                onChange={(e) => setNickname(e.target.value)}
                placeholder="别人会看到的名字"
                required
              />
            ) : null}
            {mode !== 'code' ? (
              <Input
                label="密码"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
                required
              />
            ) : null}
            {mode === 'register' ? (
              <Input
                label="确认密码"
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
                  label="验证码"
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
                  {captchaWait > 0 ? `${captchaWait}s 后重试` : '获取验证码'}
                </Button>
              </div>
            ) : null}

            <div className={styles.feedback} aria-live="polite">
              {error ? <p className={styles.error} role="alert">{error}</p> : <span>信息仅用于验证身份</span>}
            </div>

            <Button type="submit" block loading={loading}>
              {modeCopy[mode].action}
            </Button>
          </form>
          <p className={styles.legal}>继续即表示你同意以安全、友善的方式使用 InfiniteChat。</p>
        </motion.div>
      </section>
    </div>
  )
}
