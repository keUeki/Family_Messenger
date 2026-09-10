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
      toast.error(e instanceof ApiError ? e.message : 'Failed to load')
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
      toast.warning('Select at least one member')
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
        lastMsgContent: 'Group created',
        lastMsgTime: new Date().toISOString(),
      })
      setActiveSession(sessionId)
      toast.success('Group created')
      navigate(`/app/chat/${sessionId}`)
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Failed to create')
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
      toast.error(e instanceof ApiError ? e.message : 'Failed to load the members')
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
          <h2>My groups</h2>
          <p>Give the things you care about together a place to keep happening.</p>
        </div>
        <Button onClick={() => setCreateOpen(true)}>+ Create group</Button>
      </div>
      <div className={styles.summary}>
        <div><span>Joined</span><strong>{groups.length}</strong><small>groups</small></div>
        <div><span>Can invite</span><strong>{friends.length}</strong><small>friends</small></div>
        <div><span>Shared connections</span><strong>{groups.reduce((sum, group) => sum + (group.memberCount || 0), 0)}</strong><small>memberships</small></div>
      </div>
      {loading ? <SkeletonList rows={3} /> : null}

      <div className={styles.listHeading}><h3>All groups</h3><span>{groups.length} total</span></div>
      <div className={styles.list}>
        {groups.map((g) => (
          <div key={g.sessionId} className={styles.row}>
            <Avatar src={g.avatar} name={g.sessionName} size={52} />
            <div className={styles.meta}>
              <strong>{g.sessionName}</strong>
              <span>
                {g.memberCount} members - role {g.role === 0 ? 'Owner' : g.role === 1 ? 'Admin' : 'Member'}
              </span>
            </div>
            <div className={styles.actions}>
              <Button variant="secondary" onClick={() => enterChat(g)}>
                Open chat
              </Button>
              <Button variant="ghost" onClick={() => openGroup(g)}>
                Manage
              </Button>
            </div>
          </div>
        ))}
        {!groups.length && !loading ? (
          <EmptyState
            title="No groups yet"
            description="Invite some friends and create your first group."
            actionLabel="Create group"
            onAction={() => setCreateOpen(true)}
          />
        ) : null}
      </div>

      <Modal
        open={createOpen}
        title="Create a group"
        onClose={() => setCreateOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setCreateOpen(false)}>
              Cancel
            </Button>
            <Button loading={busy} onClick={onCreate}>Create</Button>
          </>
        }
        width={480}
      >
        <p className={styles.hint}>Choose the friends to invite</p>
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
        title={activeGroup?.sessionName || 'Group management'}
        onClose={() => setActiveGroup(null)}
        width={520}
      >
        {activeGroup ? (
          <div className={styles.manage}>
            <p className={styles.muted}>Members</p>
            <div className={styles.memberList}>
              {members.map((m) => (
                <div key={m.userId} className={styles.member}>
                  <Avatar src={m.avatar} name={m.nickname} size={36} />
                  <div>
                    <strong>{m.nickname}</strong>
                    <span>{m.role === 0 ? 'Owner' : m.role === 1 ? 'Admin' : 'Member'}</span>
                  </div>
                </div>
              ))}
            </div>

            <p className={styles.hint}>Invite friends to the group</p>
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
              {!inviteCandidates.length ? <p className={styles.muted}>No friends available to invite</p> : null}
            </div>
            <Button
              loading={busy}
              onClick={async () => {
                const ids = inviteSelected.filter((id) => /^\d+$/.test(id) && id !== '0')
                if (!ids.length) {
                  toast.warning('Choose the friends to invite')
                  return
                }
                setBusy(true)
                try {
                  await groupApi.inviteGroup(activeGroup.sessionId, userId, ids)
                  setInviteSelected([])
                  toast.success('Invitations sent')
                  await openGroup(activeGroup)
                } catch (e) {
                  toast.error(e instanceof ApiError ? e.message : 'Failed to invite')
                } finally {
                  setBusy(false)
                }
              }}
            >
              Invite to group
            </Button>

            {(activeGroup.role === 0 || activeGroup.role === 1) ? (
              <>
                <p className={styles.hint}>Choose the members to remove</p>
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
                  Remove selected members
                </Button>
              </>
            ) : null}

            <Button variant="danger" onClick={() => setConfirmExit(true)}>
              Leave group
            </Button>
          </div>
        ) : null}
      </Modal>

      <ConfirmDialog
        open={confirmKick}
        title="Remove these members?"
        description={`${kickSelected.length} member(s) will be removed.`}
        danger
        loading={busy}
        onCancel={() => setConfirmKick(false)}
        onConfirm={async () => {
          if (!activeGroup) return
          const ids = kickSelected.filter((id) => /^\d+$/.test(id) && id !== '0')
          if (!ids.length) {
            toast.warning('Select some members first')
            setConfirmKick(false)
            return
          }
          setBusy(true)
          try {
            await groupApi.kickMembers(activeGroup.sessionId, userId, ids)
            setKickSelected([])
            setConfirmKick(false)
            toast.success('Members removed')
            await openGroup(activeGroup)
          } catch (e) {
            toast.error(e instanceof ApiError ? e.message : 'The operation failed')
          } finally {
            setBusy(false)
          }
        }}
      />

      <ConfirmDialog
        open={confirmExit}
        title="Leave this group?"
        description="Once you leave you will need another invitation to rejoin."
        danger
        confirmLabel="Leave"
        loading={busy}
        onCancel={() => setConfirmExit(false)}
        onConfirm={async () => {
          if (!activeGroup) return
          setBusy(true)
          try {
            await groupApi.exitGroup(activeGroup.sessionId, userId)
            setConfirmExit(false)
            setActiveGroup(null)
            toast.info('You left the group')
            await reload()
          } catch (e) {
            toast.error(e instanceof ApiError ? e.message : 'The operation failed')
          } finally {
            setBusy(false)
          }
        }}
      />
    </div>
  )
}
