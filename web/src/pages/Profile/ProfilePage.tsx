import { useEffect, useRef, useState } from 'react'
import { Avatar } from '@/components/Avatar'
import { Button } from '@/components/Button'
import { Input } from '@/components/Input'
import { Spinner } from '@/components/Spinner'
import { userApi } from '@/api/user'
import { useAuthStore } from '@/stores/authStore'
import { useWs } from '@/hooks/useWs'
import { toast } from '@/stores/toastStore'
import { uploadFile } from '@/utils'
import { ApiError } from '@/api/client'
import styles from './ProfilePage.module.css'

export function ProfilePage() {
  const userId = useAuthStore((s) => s.userId)!
  const profile = useAuthStore((s) => s.profile)
  const updateProfile = useAuthStore((s) => s.updateProfile)
  const refreshProfile = useAuthStore((s) => s.refreshProfile)
  const setWsServerUri = useAuthStore((s) => s.setWsServerUri)
  const { connect } = useWs()

  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [code, setCode] = useState('')
  const [uploading, setUploading] = useState(false)
  const [savingPwd, setSavingPwd] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)

  const load = async () => {
    try {
      await refreshProfile()
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '加载失败')
    }
  }

  useEffect(() => {
    load().catch(() => undefined)
  }, [userId]) // eslint-disable-line react-hooks/exhaustive-deps

  const onAvatar = async (file: File) => {
    setUploading(true)
    try {
      const url = await uploadFile(file, (name) => userApi.getUploadUrl(name))
      await userApi.updateAvatar(userId, url)
      updateProfile({ avatar: url })
      toast.success('头像已更新')
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '上传失败，请检查 MinIO CORS')
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <span className={styles.eyebrow}>YOUR SPACE</span>
        <h2>账户概览</h2>
        <p>管理个人资料、安全设置与实时连接。</p>
      </div>

      <section className={styles.hero}>
        <button
          type="button"
          className={styles.avatarBtn}
          onClick={() => fileRef.current?.click()}
          disabled={uploading}
        >
          <span className={styles.avatarWrap}>
            <Avatar src={profile.avatar} name={profile.nickname} size={88} />
            {uploading ? (
              <span className={styles.progress}>
                <Spinner size={22} />
              </span>
            ) : null}
          </span>
          <span>更换头像</span>
        </button>
        <input
          ref={fileRef}
          type="file"
          accept="image/*"
          hidden
          onChange={(e) => {
            const f = e.target.files?.[0]
            if (f) onAvatar(f)
            e.target.value = ''
          }}
        />
        <div>
          <span className={styles.profileLabel}>INFINITECHAT MEMBER</span>
          <h3>{profile.nickname || 'InfiniteChat 用户'}</h3>
          <p>@{profile.account || userId}</p>
          <p className={styles.desc}>{profile.description || '暂无简介'}</p>
        </div>
        <div className={styles.accountState}>
          <span>账户状态</span>
          <strong><i /> 正常</strong>
          <small>ID {userId}</small>
        </div>
      </section>

      <div className={styles.contentGrid}>

      <section className={`${styles.panel} card-lift`}>
        <div className={styles.panelTitle}><span>安全</span><h4>修改密码</h4><p>验证码确认后即可更新</p></div>
        <div className={styles.form}>
          <Input
            label="新密码"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <Input
            label="确认密码"
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
          />
          <div className={styles.row}>
            <Input label="验证码" value={code} onChange={(e) => setCode(e.target.value)} />
            <Button
              variant="secondary"
              type="button"
              onClick={async () => {
                if (!profile.account) {
                  toast.warning('当前账户信息不完整')
                  return
                }
                try {
                  await userApi.sendCaptcha(profile.account)
                  toast.success('验证码已生成，请注意查收')
                } catch (e) {
                  toast.error(e instanceof ApiError ? e.message : '发送失败')
                }
              }}
            >
              获取验证码
            </Button>
          </div>
          <Button
            loading={savingPwd}
            onClick={async () => {
              if (!password || !confirmPassword || !code) {
                toast.warning('请完整填写密码和验证码')
                return
              }
              if (password !== confirmPassword) {
                toast.error('两次密码不一致')
                return
              }
              setSavingPwd(true)
              try {
                await userApi.updatePassword({
                  account: profile.account || '',
                  password,
                  confirmPassword,
                  code,
                })
                toast.success('密码已更新')
                setPassword('')
                setConfirmPassword('')
                setCode('')
              } catch (e) {
                toast.error(e instanceof ApiError ? e.message : '修改失败')
              } finally {
                setSavingPwd(false)
              }
            }}
          >
            保存密码
          </Button>
        </div>
      </section>

      <section className={`${styles.panel} card-lift`}>
        <div className={styles.panelTitle}><span>实时</span><h4>连接管理</h4><p>遇到消息延迟时可重新分配节点</p></div>
        <Button
          variant="secondary"
          onClick={async () => {
            try {
              const uri = await userApi.refreshUri(userId)
              if (uri) {
                setWsServerUri(uri)
                window.setTimeout(() => connect(), 50)
                toast.success('实时节点已刷新并重新连接')
              } else {
                toast.warning('未分配到实时节点')
              }
            } catch (e) {
              toast.error(e instanceof ApiError ? e.message : '刷新节点失败')
            }
          }}
        >
          重新连接实时通道
        </Button>
      </section>
      </div>
    </div>
  )
}
