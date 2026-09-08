import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Avatar } from '@/components/Avatar'
import { Button } from '@/components/Button'
import { Modal } from '@/components/Modal'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { SkeletonList } from '@/components/Skeleton'
import { EmptyState } from '@/components/EmptyState'
import { groupApi } from '@/api/group'
import { contactApi } from '@/api/contact'
import { useAuthStore } from '@/stores/authStore'
import { useChatStore } from '@/stores/chatStore'
import { toast } from '@/stores/toastStore'
import type { FriendDTO, GroupMember, UserGroup } from '@/types'
import { ApiError } from '@/api/client'
import styles from './GroupsPage.module.css'

export function GroupsPage() {
  const navigate = useNavigate()
  const userId = useAuthStore((s) => s.userId)!
  const upsertSession = useChatStore((s) => s.upsertSession)
  const setActiveSession = useChatStore((s) => s.setActiveSession)

  const [groups, setGroups] = useState<UserGroup[]>([])
  const [friends, setFriends] = useState<FriendDTO[]>([])
  const [createOpen, setCreateOpen] = useState(false)
  const [selected, setSelected] = useState<string[]>([])
  const [inviteSelected, setInviteSelected] = useState<string[]>([])
  const [kickSelected, setKickSelected] = useState<string[]>([])
  const [activeGroup, setActiveGroup] = useState<UserGroup | null>(null)
  const [members, setMembers] = useState<GroupMember[]>([])
  const [loading, setLoading] = useState(false)
  const [confirmExit, setConfirmExit] = useState(false)
  const [confirmKick, setConfirmKick] = useState(false)
  const [busy, setBusy] = useState(false)

  const memberIds = useMemo(() => new Set(members.map((m) => String(m.userId))), [members])
  const inviteCandidates = friends.filter((f) => !memberIds.has(String(f.userId)))

  const reload = async () => {
    setLoading(true)
    try {
      const [g, f] = await Promise.all([
        groupApi.getUserGroups(userId),
        contactApi.getFriends(userId, 1, 100),
      ])
      setGroups(g?.list || [])
      setFriends(f?.list || [])
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    reload().catch(() => undefined)
  }, [userId]) // eslint-disable-line react-hooks/exhaustive-deps

  const toggle = (list: string[], id: string, setter: (v: string[]) => void) => {
    setter(list.includes(id) ? list.filter((x) => x !== id) : [...list, id])
  }

  const onCreate = async () => {
    const memberIds = selected.filter((id) => /^\d+$/.test(id) && id !== '0')
    if (!memberIds.length) {
      toast.warning('请至少选择一位成员')
      return
    }
    setBusy(true)
    try {
      const res = await groupApi.createGroup(userId, memberIds)
      setCreateOpen(false)
      setSelected([])
      await reload()
      const sessionId = res.sessionId
      upsertSession({
        sessionId,
        name: res.sessionName,
        avatar: res.avatar,
        sessionType: 1,
        senderId: userId,
        count: 0,
        type: 0,
        lastMsgContent: '群聊已创建',
        lastMsgTime: new Date().toISOString(),
      })
      setActiveSession(sessionId)
      toast.success('群聊已创建')
      navigate(`/app/chat/${sessionId}`)
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '创建失败')
    } finally {
      setBusy(false)
    }
  }

  const openGroup = async (g: UserGroup) => {
    setActiveGroup(g)
    setInviteSelected([])
    setKickSelected([])
    try {
      const page = await groupApi.getMembers(g.sessionId)
      setMembers(page?.list || [])
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : '加载成员失败')
    }
  }

  const enterChat = (g: UserGroup) => {
    const sessionId = g.sessionId
    upsertSession({
      sessionId,
      name: g.sessionName,
      avatar: g.avatar,
      sessionType: 1,
      senderId: userId,
      count: 0,
      type: 0,
      lastMsgContent: '',
      lastMsgTime: new Date().toISOString(),
    })
    setActiveSession(sessionId)
    navigate(`/app/chat/${sessionId}`)
  }

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <div>
          <span className={styles.eyebrow}>YOUR COMMUNITIES</span>
          <h2>我的群组</h2>
          <p>让共同关注的事情，有一个持续发生的地方。</p>
        </div>
        <Button onClick={() => setCreateOpen(true)}>＋ 创建群聊</Button>
      </div>
      <div className={styles.summary}>
        <div><span>已加入</span><strong>{groups.length}</strong><small>个群组</small></div>
        <div><span>可邀请</span><strong>{friends.length}</strong><small>位好友</small></div>
        <div><span>共同连接</span><strong>{groups.reduce((sum, group) => sum + (group.memberCount || 0), 0)}</strong><small>人次</small></div>
      </div>
      {loading ? <SkeletonList rows={3} /> : null}

      <div className={styles.listHeading}><h3>全部群组</h3><span>{groups.length} 个</span></div>
      <div className={styles.list}>
        {groups.map((g) => (
          <div key={g.sessionId} className={styles.row}>
            <Avatar src={g.avatar} name={g.sessionName} size={52} />
            <div className={styles.meta}>
              <strong>{g.sessionName}</strong>
              <span>
                {g.memberCount} 人 · 角色 {g.role === 0 ? '群主' : g.role === 1 ? '管理员' : '成员'}
              </span>
            </div>
            <div className={styles.actions}>
              <Button variant="secondary" onClick={() => enterChat(g)}>
                进入聊天
              </Button>
              <Button variant="ghost" onClick={() => openGroup(g)}>
                管理
              </Button>
            </div>
          </div>
        ))}
        {!groups.length && !loading ? (
          <EmptyState
            title="还没有群聊"
            description="邀请好友一起创建一个群吧。"
            actionLabel="创建群聊"
            onAction={() => setCreateOpen(true)}
          />
        ) : null}
      </div>

      <Modal
        open={createOpen}
        title="创建群聊"
        onClose={() => setCreateOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setCreateOpen(false)}>
              取消
            </Button>
            <Button loading={busy} onClick={onCreate}>创建</Button>
          </>
        }
        width={480}
      >
        <p className={styles.hint}>选择要邀请的好友</p>
        <div className={styles.pickList}>
          {friends.map((f) => (
            <label key={f.userId} className={styles.pickItem}>
              <input
                type="checkbox"
                checked={selected.includes(f.userId)}
                onChange={() => toggle(selected, f.userId, setSelected)}
              />
              <Avatar src={f.avatar} name={f.nickname} size={32} />
              <span>{f.nickname}</span>
            </label>
          ))}
        </div>
      </Modal>

      <Modal
        open={Boolean(activeGroup)}
        title={activeGroup?.sessionName || '群管理'}
        onClose={() => setActiveGroup(null)}
        width={520}
      >
        {activeGroup ? (
          <div className={styles.manage}>
            <p className={styles.muted}>成员列表</p>
            <div className={styles.memberList}>
              {members.map((m) => (
                <div key={m.userId} className={styles.member}>
                  <Avatar src={m.avatar} name={m.nickname} size={36} />
                  <div>
                    <strong>{m.nickname}</strong>
                    <span>{m.role === 0 ? '群主' : m.role === 1 ? '管理员' : '成员'}</span>
                  </div>
                </div>
              ))}
            </div>

            <p className={styles.hint}>邀请好友入群</p>
            <div className={styles.pickList}>
              {inviteCandidates.map((f) => (
                <label key={f.userId} className={styles.pickItem}>
                  <input
                    type="checkbox"
                    checked={inviteSelected.includes(f.userId)}
                    onChange={() => toggle(inviteSelected, f.userId, setInviteSelected)}
                  />
                  <Avatar src={f.avatar} name={f.nickname} size={32} />
                  <span>{f.nickname}</span>
                </label>
              ))}
              {!inviteCandidates.length ? <p className={styles.muted}>暂无可邀请好友</p> : null}
            </div>
            <Button
              loading={busy}
              onClick={async () => {
                const ids = inviteSelected.filter((id) => /^\d+$/.test(id) && id !== '0')
                if (!ids.length) {
                  toast.warning('请选择要邀请的好友')
                  return
                }
                setBusy(true)
                try {
                  await groupApi.inviteGroup(activeGroup.sessionId, userId, ids)
                  setInviteSelected([])
                  toast.success('已发送邀请')
                  await openGroup(activeGroup)
                } catch (e) {
                  toast.error(e instanceof ApiError ? e.message : '邀请失败')
                } finally {
                  setBusy(false)
                }
              }}
            >
              邀请入群
            </Button>

            {(activeGroup.role === 0 || activeGroup.role === 1) ? (
              <>
                <p className={styles.hint}>选择要踢出的成员</p>
                <div className={styles.pickList}>
                  {members
                    .filter((m) => m.userId !== userId && m.role !== 0)
                    .map((m) => (
                      <label key={m.userId} className={styles.pickItem}>
                        <input
                          type="checkbox"
                          checked={kickSelected.includes(String(m.userId))}
                          onChange={() =>
                            toggle(kickSelected, String(m.userId), setKickSelected)
                          }
                        />
                        <Avatar src={m.avatar} name={m.nickname} size={32} />
                        <span>{m.nickname}</span>
                      </label>
                    ))}
                </div>
                <Button variant="secondary" onClick={() => setConfirmKick(true)}>
                  踢出选中成员
                </Button>
              </>
            ) : null}

            <Button variant="danger" onClick={() => setConfirmExit(true)}>
              退出群聊
            </Button>
          </div>
        ) : null}
      </Modal>

      <ConfirmDialog
        open={confirmKick}
        title="确认踢出成员？"
        description={`将踢出 ${kickSelected.length} 位成员。`}
        danger
        loading={busy}
        onCancel={() => setConfirmKick(false)}
        onConfirm={async () => {
          if (!activeGroup) return
          const ids = kickSelected.filter((id) => /^\d+$/.test(id) && id !== '0')
          if (!ids.length) {
            toast.warning('请先选择成员')
            setConfirmKick(false)
            return
          }
          setBusy(true)
          try {
            await groupApi.kickMembers(activeGroup.sessionId, userId, ids)
            setKickSelected([])
            setConfirmKick(false)
            toast.success('已踢出')
            await openGroup(activeGroup)
          } catch (e) {
            toast.error(e instanceof ApiError ? e.message : '操作失败')
          } finally {
            setBusy(false)
          }
        }}
      />

      <ConfirmDialog
        open={confirmExit}
        title="确认退出群聊？"
        description="退出后需要被再次邀请才能加入。"
        danger
        confirmLabel="退出"
        loading={busy}
        onCancel={() => setConfirmExit(false)}
        onConfirm={async () => {
          if (!activeGroup) return
          setBusy(true)
          try {
            await groupApi.exitGroup(activeGroup.sessionId, userId)
            setConfirmExit(false)
            setActiveGroup(null)
            toast.info('已退出群聊')
            await reload()
          } catch (e) {
            toast.error(e instanceof ApiError ? e.message : '操作失败')
          } finally {
            setBusy(false)
          }
        }}
      />
    </div>
  )
}
