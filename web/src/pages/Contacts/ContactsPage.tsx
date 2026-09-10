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
  const [requestMsg, setRequestMsg] = useState('Hi, I would like to add you as a friend')
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
      toast.error(e instanceof ApiError ? e.message : 'Failed to load')
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
      toast.warning('There is no conversation yet; become friends first')
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
        toast.info('Blocked')
        setDetail(null)
      } else if (confirmKind === 'delete') {
        await contactApi.deleteFriend(userId, confirmTarget)
        toast.info('Removed')
        setDetail(null)
      } else if (confirmKind === 'reject') {
        await contactApi.modifyApplicationStatus(userId, '2', [confirmTarget])
        toast.info('Declined')
      }
      setConfirmKind(null)
      setConfirmTarget(null)
      await reload()
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'The operation failed')
    } finally {
      setConfirmLoading(false)
    }
  }

  const searchUser = async () => {
    const value = searchKey.trim()
    if (!value) {
      toast.warning('Enter a phone number or email address')
      return
    }
    setSearching(true)
    try {
      setSearchResult(await contactApi.searchUser(userId, value))
    } catch (e) {
      setSearchResult(null)
      toast.error(e instanceof ApiError ? e.message : 'No such user')
    } finally {
      setSearching(false)
    }
  }

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <div>
          <span className={styles.eyebrow}>MY NETWORK</span>
          <h2>Your contacts</h2>
          <p>Find the people you know, and meet new ones.</p>
        </div>
        <SegmentedControl
          ariaLabel="Contact categories"
          value={tab}
          onChange={setTab}
          options={[
            { value: 'friends', label: 'Friends' },
            {
              value: 'applies',
              label: applyCount > 0 ? `Requests (${applyCount})` : 'Requests',
            },
          ]}
        />
      </div>

      <div className={styles.searchPanel}>
        <div className={styles.searchIntro}>
          <span>+</span>
          <div><strong>Add a new friend</strong><p>Enter the phone number or email address they signed up with</p></div>
        </div>
        <div className={styles.searchBar}>
          <Input
            placeholder="Phone number or email"
            value={searchKey}
            onChange={(e) => setSearchKey(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') void searchUser()
            }}
          />
          <Button loading={searching} onClick={() => void searchUser()}>Find user</Button>
        </div>
      </div>

      {tab === 'friends' ? (
        <>
          <div className={styles.sectionHeading}>
            <div><h3>Friends</h3><span>{friends.length} contacts</span></div>
            <div className={styles.filter}>
              <Input
                placeholder="Filter by nickname"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') reload()
                }}
              />
              <Button variant="secondary" onClick={reload}>Filter</Button>
            </div>
          </div>
          {loading ? <SkeletonList rows={4} /> : null}
          <div className={styles.list}>
            {friends.map((f) => (
              <div key={f.userId} className={styles.row}>
                <Avatar src={f.avatar} name={f.nickname} size={44} />
                <div className={styles.meta}>
                  <strong>{f.nickname}</strong>
                  <span>{f.signature || 'This person has not written anything yet'}</span>
                </div>
                <div className={styles.actions}>
                  <Button variant="secondary" onClick={() => openChat(f)}>
                    Message
                  </Button>
                  <Button
                    variant="ghost"
                    onClick={async () => {
                      try {
                        setDetail(await contactApi.getFriendDetail(userId, f.userId))
                      } catch (e) {
                        toast.error(e instanceof ApiError ? e.message : 'Failed to load the details')
                      }
                    }}
                  >
                    Details
                  </Button>
                </div>
              </div>
            ))}
            {!friends.length && !loading ? (
              <EmptyState title="No friends yet" description="Search for an account and send your first friend request." />
            ) : null}
          </div>
        </>
      ) : (
        <div className={styles.listBlock}>
          <div className={styles.sectionHeading}>
            <div><h3>Friend requests</h3><span>{applies.length} records</span></div>
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
                        toast.success('Friend request accepted')
                        await reload()
                      } catch (e) {
                        toast.error(e instanceof ApiError ? e.message : 'The operation failed')
                      }
                    }}
                  >
                    Accept
                  </Button>
                  <Button
                    variant="danger"
                    onClick={() => {
                      setConfirmKind('reject')
                      setConfirmTarget(a.userId)
                    }}
                  >
                    Decline
                  </Button>
                </div>
              ) : (
                <span className={styles.muted}>Waiting for them to respond</span>
              )}
            </div>
          ))}
          {!applies.length ? <EmptyState title="No friend requests" /> : null}
          </div>
        </div>
      )}

      <Modal
        open={Boolean(searchResult)}
        title="User found"
        onClose={() => setSearchResult(null)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setSearchResult(null)}>
              Cancel
            </Button>
            <Button
              onClick={async () => {
                if (!searchResult) return
                await contactApi.sendFriendRequest(userId, searchResult.userId, requestMsg)
                toast.success('Request sent')
                setSearchResult(null)
                await reload()
              }}
            >
              Send request
            </Button>
          </>
        }
      >
        {searchResult ? (
          <div className={styles.detail}>
            <Avatar src={searchResult.avatar} name={searchResult.nickname} size={64} />
            <h3>{searchResult.nickname}</h3>
            <p>{searchResult.signature || 'No bio yet'}</p>
            <TextArea
              label="Request message"
              value={requestMsg}
              onChange={(e) => setRequestMsg(e.target.value)}
            />
          </div>
        ) : null}
      </Modal>

      <Modal open={Boolean(detail)} title="Friend details" onClose={() => setDetail(null)} width={420}>
        {detail ? (
          <div className={styles.detail}>
            <Avatar src={detail.avatar} name={detail.nickname} size={72} />
            <h3>{detail.nickname}</h3>
            <p>{detail.signature || 'No bio yet'}</p>
            <div className={styles.actions}>
              <Button onClick={() => openChat(detail)}>Message</Button>
              <Button
                variant="secondary"
                onClick={() => {
                  setConfirmKind('block')
                  setConfirmTarget(detail.userId)
                }}
              >
                Block
              </Button>
              <Button
                variant="danger"
                onClick={() => {
                  setConfirmKind('delete')
                  setConfirmTarget(detail.userId)
                }}
              >
                Remove
              </Button>
            </div>
          </div>
        ) : null}
      </Modal>

      <ConfirmDialog
        open={Boolean(confirmKind)}
        title={
          confirmKind === 'block'
            ? 'Block this person?'
            : confirmKind === 'delete'
              ? 'Remove this friend?'
              : 'Decline this request?'
        }
        description={
          confirmKind === 'block'
            ? 'Once blocked, neither of you can message the other.'
            : confirmKind === 'delete'
              ? 'The conversation may remain, but you will have to add them again.'
              : 'They will be told that the request was declined.'
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
