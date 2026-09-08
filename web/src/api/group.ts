import { http, unwrap } from './client'
import type {
  CreateGroupResponse,
  GroupMember,
  PageResponse,
  UserGroup,
  EntityId,
} from '@/types'

export const groupApi = {
  createGroup(creatorId: EntityId, memberIds: EntityId[]) {
    return unwrap<CreateGroupResponse>(
      http.post('/api/group/', { creatorId, memberIds }),
    )
  },
  inviteGroup(sessionId: EntityId, inviterId: EntityId, inviteeIds: EntityId[]) {
    return unwrap<{ successIds: EntityId[]; failedIds: EntityId[] }>(
      http.post('/api/group/invite', { sessionId, inviterId, inviteeIds }),
    )
  },
  kickMembers(sessionId: EntityId, operatorId: EntityId, memberIds: EntityId[]) {
    return unwrap<{ successIds: EntityId[] }>(
      http.post('/api/group/kick', { sessionId, operatorId, memberIds }),
    )
  },
  exitGroup(sessionId: EntityId, userId: EntityId) {
    return unwrap<boolean>(http.post('/api/group/exit', { sessionId, userId }))
  },
  getMembers(sessionId: EntityId, pageNum = 1, pageSize = 50) {
    return unwrap<PageResponse<GroupMember>>(
      http.get(`/api/group/${sessionId}/members`, { params: { pageNum, pageSize } }),
    )
  },
  getUserGroups(userId: EntityId, pageNum = 1, pageSize = 50) {
    return unwrap<PageResponse<UserGroup>>(
      http.get(`/api/group/user/${userId}`, { params: { pageNum, pageSize } }),
    )
  },
  getMemberCount(sessionId: EntityId) {
    return unwrap<{ memberCount: number }>(http.get(`/api/group/${sessionId}/count`))
  },
}
