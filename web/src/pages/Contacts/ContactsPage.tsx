import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Avatar } from '@/components/Avatar'
import { Button } from '@/components/Button'
import { Input, TextArea } from '@/components/Input'
import { Modal } from '@/components/Modal'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { SkeletonList } from '@/components/Skeleton'
import { EmptyState } from '@/components/EmptyState'
import { SegmentedControl } from '@/components/SegmentedControl'
import { contactApi } from '@/api/contact'
import { useAuthStore } from '@/stores/authStore'
import { useChatStore } from '@/stores/chatStore'
import { toast } from '@/stores/toastStore'
import type { ApplyFriendDTO, FriendDetail, FriendDTO } from '@/types'
import { AI_USER_ID } from '@/types'
import { ApiError } from '@/api/client'
import styles from './ContactsPage.module.css'

type ConfirmKind = 'block' | 'delete' | 'reject' | null

export function ContactsPage() {
  const navigate = useNavigate()
  const userId = useAuthStore((s) => s.userId)!
  const upsertSession = useChatStore((s) => s.upsertSession)
  const setActiveSession = useChatStore((s) => s.setActiveSession)

  const [friends, setFriends] = useState<FriendDTO[]>([])
  const [applies, setApplies] = useState<ApplyFriendDTO[]>([])
  const [applyCount, setApplyCount] = useState(0)
  const [keyword, setKeyword] = useState('')
  const [searchKey, setSearchKey] = useState('')
  const [searchResult, setSearchResult] = useState<FriendDetail | null>(null)
  const [requestMsg, setRequestMsg] = useState('你好，交个朋友吧')
  const [detail, setDetail] = useState<FriendDetail | null>(null)
  const [tab, setTab] = useState<'friends' | 'applies'>('friends')
  const [loading, setLoading] = useState(false)
  const [searching, setSearching] = useState(false)
  const [confirmKind, setConfirmKind] = useState<ConfirmKind>(null)
  const [confirmTarget, setConfirmTarget] = useState<string | null>(null)
  const [confirmLoading, setConfirmLoading] = useState(false)

  const reload = async () => {
    setLoading(true)
    try {
      const [f, a, c] = await Promise.all([
        contactApi.getFriends(userId, 1, 100, keyword),
        contactApi.getApplyList(userId),
        contactApi.getUnreadApplyCount(userId),
      ])
      setFriends(f?.list || [])
      setApplies(a?.list || [])
      setApplyCount(typeof c === 'number' ? c : Number(c) || 0)
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    reload().catch(() => undefined)
  }, [userId]) // eslint-disable-line react-hooks/exhaustive-deps

  const openChat = (friend: FriendDTO | FriendDetail) => {
    const sessionId = String(friend.sessionId || '')
    if (!/^\d+$/.test(sessionId) || sessionId === '0') {
      toast.warning('暂无会话，请先成为好友')
      return
    }
    upsertSession({
      sessionId,
      name: friend.nickname,
      avatar: friend.avatar,
      sessionType: friend.userId === AI_USER_ID ? 2 : 0,
      senderId: friend.userId,
      peerId: friend.userId,
      lastMsgContent: '',
      lastMsgTime: new Date().toISOString(),
      count: 0,
      type: 0,
    })
    setActiveSession(sessionId)
    navigate(`/app/chat/${sessionId}`)
  }

  const runConfirm = async () => {
    if (!confirmKind || !confirmTarget) return
    setConfirmLoading(true)
    try {
      if (confirmKind === 'block') {
        await contactApi.blockFriend(userId, confirmTarget)
        toast.info('已拉黑')
        setDetail(null)
      } else if (confirmKind === 'delete') {
        await contactApi.deleteFriend(userId, confirmTarget)
        toast.info('已删除')
        setDetail(null)
      } else if (confirmKind === 'reject') {
        await contactApi.modifyApplicationStatus(userId, '2', [confirmTarget])
        toast.info('已拒绝')
      }
      setConfirmKind(null)
      setConfirmTarget(null)
      await reload()
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '操作失败')
    } finally {
      setConfirmLoading(false)
    }
  }

  const searchUser = async () => {
    const value = searchKey.trim()
    if (!value) {
      toast.warning('请输入手机号或邮箱')
      return
    }
    setSearching(true)
    try {
      setSearchResult(await contactApi.searchUser(userId, value))
    } catch (e) {
      setSearchResult(null)
      toast.error(e instanceof ApiError ? e.message : '未找到用户')
    } finally {
      setSearching(false)
    }
  }

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <div>
          <span className={styles.eyebrow}>MY NETWORK</span>
          <h2>你的联系人</h2>
          <p>找到熟悉的人，也认识新的伙伴。</p>
        </div>
        <SegmentedControl
          ariaLabel="通讯录分类"
          value={tab}
          onChange={setTab}
          options={[
            { value: 'friends', label: '好友' },
            {
              value: 'applies',
              label: applyCount > 0 ? `申请 (${applyCount})` : '申请',
            },
          ]}
        />
      </div>

      <div className={styles.searchPanel}>
        <div className={styles.searchIntro}>
          <span>＋</span>
          <div><strong>添加新朋友</strong><p>输入对方绑定的手机号或邮箱</p></div>
        </div>
        <div className={styles.searchBar}>
          <Input
            placeholder="手机号或邮箱"
            value={searchKey}
            onChange={(e) => setSearchKey(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') void searchUser()
            }}
          />
          <Button loading={searching} onClick={() => void searchUser()}>查找用户</Button>
        </div>
      </div>

      {tab === 'friends' ? (
        <>
          <div className={styles.sectionHeading}>
            <div><h3>好友</h3><span>{friends.length} 位联系人</span></div>
            <div className={styles.filter}>
              <Input
                placeholder="筛选好友昵称"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') reload()
                }}
              />
              <Button variant="secondary" onClick={reload}>筛选</Button>
            </div>
          </div>
          {loading ? <SkeletonList rows={4} /> : null}
          <div className={styles.list}>
            {friends.map((f) => (
              <div key={f.userId} className={styles.row}>
                <Avatar src={f.avatar} name={f.nickname} size={44} />
                <div className={styles.meta}>
                  <strong>{f.nickname}</strong>
                  <span>{f.signature || '这个人很懒，什么都没写'}</span>
                </div>
                <div className={styles.actions}>
                  <Button variant="secondary" onClick={() => openChat(f)}>
                    发消息
                  </Button>
                  <Button
                    variant="ghost"
                    onClick={async () => {
                      try {
                        setDetail(await contactApi.getFriendDetail(userId, f.userId))
                      } catch (e) {
                        toast.error(e instanceof ApiError ? e.message : '加载详情失败')
                      }
                    }}
                  >
                    详情
                  </Button>
                </div>
              </div>
            ))}
            {!friends.length && !loading ? (
              <EmptyState title="还没有好友" description="搜索账号，发出第一份好友申请吧。" />
            ) : null}
          </div>
        </>
      ) : (
        <div className={styles.listBlock}>
          <div className={styles.sectionHeading}>
            <div><h3>好友申请</h3><span>{applies.length} 条记录</span></div>
          </div>
          <div className={styles.list}>
          {applies.map((a) => (
            <div key={`${a.userId}-${a.time}`} className={styles.row}>
              <Avatar src={a.avatar} name={a.nickname} size={44} />
              <div className={styles.meta}>
                <strong>{a.nickname}</strong>
                <span>{a.msg}</span>
                <em>{a.time}</em>
              </div>
              {a.isReceiver === 1 ? (
                <div className={styles.actions}>
                  <Button
                    onClick={async () => {
                      try {
                        await contactApi.modifyApplicationStatus(userId, '1', [a.userId])
                        toast.success('已同意好友申请')
                        await reload()
                      } catch (e) {
                        toast.error(e instanceof ApiError ? e.message : '操作失败')
                      }
                    }}
                  >
                    同意
                  </Button>
                  <Button
                    variant="danger"
                    onClick={() => {
                      setConfirmKind('reject')
                      setConfirmTarget(a.userId)
                    }}
                  >
                    拒绝
                  </Button>
                </div>
              ) : (
                <span className={styles.muted}>等待对方处理</span>
              )}
            </div>
          ))}
          {!applies.length ? <EmptyState title="暂无好友申请" /> : null}
          </div>
        </div>
      )}

      <Modal
        open={Boolean(searchResult)}
        title="找到用户"
        onClose={() => setSearchResult(null)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setSearchResult(null)}>
              取消
            </Button>
            <Button
              onClick={async () => {
                if (!searchResult) return
                await contactApi.sendFriendRequest(userId, searchResult.userId, requestMsg)
                toast.success('申请已发送')
                setSearchResult(null)
                await reload()
              }}
            >
              发送申请
            </Button>
          </>
        }
      >
        {searchResult ? (
          <div className={styles.detail}>
            <Avatar src={searchResult.avatar} name={searchResult.nickname} size={64} />
            <h3>{searchResult.nickname}</h3>
            <p>{searchResult.signature || '暂无签名'}</p>
            <TextArea
              label="申请留言"
              value={requestMsg}
              onChange={(e) => setRequestMsg(e.target.value)}
            />
          </div>
        ) : null}
      </Modal>

      <Modal open={Boolean(detail)} title="好友详情" onClose={() => setDetail(null)} width={420}>
        {detail ? (
          <div className={styles.detail}>
            <Avatar src={detail.avatar} name={detail.nickname} size={72} />
            <h3>{detail.nickname}</h3>
            <p>{detail.signature || '暂无签名'}</p>
            <div className={styles.actions}>
              <Button onClick={() => openChat(detail)}>发消息</Button>
              <Button
                variant="secondary"
                onClick={() => {
                  setConfirmKind('block')
                  setConfirmTarget(detail.userId)
                }}
              >
                拉黑
              </Button>
              <Button
                variant="danger"
                onClick={() => {
                  setConfirmKind('delete')
                  setConfirmTarget(detail.userId)
                }}
              >
                删除
              </Button>
            </div>
          </div>
        ) : null}
      </Modal>

      <ConfirmDialog
        open={Boolean(confirmKind)}
        title={
          confirmKind === 'block'
            ? '确认拉黑？'
            : confirmKind === 'delete'
              ? '确认删除好友？'
              : '确认拒绝申请？'
        }
        description={
          confirmKind === 'block'
            ? '拉黑后将无法互相发消息。'
            : confirmKind === 'delete'
              ? '删除后会话可能仍保留，需重新加好友。'
              : '拒绝后对方将收到处理结果。'
        }
        danger
        loading={confirmLoading}
        onCancel={() => {
          setConfirmKind(null)
          setConfirmTarget(null)
        }}
        onConfirm={runConfirm}
      />
    </div>
  )
}
