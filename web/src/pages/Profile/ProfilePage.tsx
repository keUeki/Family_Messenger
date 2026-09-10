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
      toast.error(e instanceof ApiError ? e.message : 'Failed to load')
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
      toast.success('Avatar updated')
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Upload failed, check the MinIO CORS settings')
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <span className={styles.eyebrow}>YOUR SPACE</span>
        <h2>Account overview</h2>
        <p>Manage your profile, security settings and realtime connection.</p>
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
          <span>Change avatar</span>
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
          <h3>{profile.nickname || 'InfiniteChat user'}</h3>
          <p>@{profile.account || userId}</p>
          <p className={styles.desc}>{profile.description || 'No bio yet'}</p>
        </div>
        <div className={styles.accountState}>
          <span>Account status</span>
          <strong><i /> Active</strong>
          <small>ID {userId}</small>
        </div>
      </section>

      <div className={styles.contentGrid}>

      <section className={`${styles.panel} card-lift`}>
        <div className={styles.panelTitle}><span>Security</span><h4>Change password</h4><p>Confirm with a verification code to update it</p></div>
        <div className={styles.form}>
          <Input
            label="New password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <Input
            label="Confirm password"
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
          />
          <div className={styles.row}>
            <Input label="Verification code" value={code} onChange={(e) => setCode(e.target.value)} />
            <Button
              variant="secondary"
              type="button"
              onClick={async () => {
                if (!profile.account) {
                  toast.warning('Your account details are incomplete')
                  return
                }
                try {
                  await userApi.sendCaptcha(profile.account)
                  toast.success('A verification code has been sent, please check your inbox')
                } catch (e) {
                  toast.error(e instanceof ApiError ? e.message : 'Failed to send')
                }
              }}
            >
              Get a code
            </Button>
          </div>
          <Button
            loading={savingPwd}
            onClick={async () => {
              if (!password || !confirmPassword || !code) {
                toast.warning('Fill in both the password and the verification code')
                return
              }
              if (password !== confirmPassword) {
                toast.error('The two passwords do not match')
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
                toast.success('Password updated')
                setPassword('')
                setConfirmPassword('')
                setCode('')
              } catch (e) {
                toast.error(e instanceof ApiError ? e.message : 'Failed to update')
              } finally {
                setSavingPwd(false)
              }
            }}
          >
            Save password
          </Button>
        </div>
      </section>

      <section className={`${styles.panel} card-lift`}>
        <div className={styles.panelTitle}><span>Realtime</span><h4>Connection</h4><p>Reassign the node if messages start lagging</p></div>
        <Button
          variant="secondary"
          onClick={async () => {
            try {
              const uri = await userApi.refreshUri(userId)
              if (uri) {
                setWsServerUri(uri)
                window.setTimeout(() => connect(), 50)
                toast.success('Realtime node refreshed and reconnected')
              } else {
                toast.warning('No realtime node was assigned')
              }
            } catch (e) {
              toast.error(e instanceof ApiError ? e.message : 'Failed to refresh the node')
            }
          }}
        >
          Reconnect the realtime channel
        </Button>
      </section>
      </div>
    </div>
  )
}
