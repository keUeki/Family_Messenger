import { http, unwrap } from './client'
import { asCount } from '@/utils'
import type { ApplyFriendDTO, FriendDetail, FriendDTO, PageResponse } from '@/types'

export const contactApi = {
  searchUser(userId: string | number, keyword: string) {
    return unwrap<FriendDetail>(
      http.get(`/api/contact/${userId}/user/search`, { params: { keyword } }),
    )
  },
  getFriends(userId: string | number, pageNum = 1, pageSize = 50, key = '') {
    return unwrap<PageResponse<FriendDTO>>(
      http.get(`/api/contact/${userId}/friend`, { params: { pageNum, pageSize, key } }),
    )
  },
  sendFriendRequest(userId: string | number, receiveuserId: string | number, msg: string) {
    return unwrap<boolean>(
      http.post(`/api/contact/${userId}/friend/${receiveuserId}`, { msg }),
    )
  },
  getApplyList(userId: string | number, pageNum = 1, pageSize = 50) {
    return unwrap<PageResponse<ApplyFriendDTO>>(
      http.get(`/api/contact/${userId}/apply`, { params: { pageNum, pageSize } }),
    )
  },
  async getUnreadApplyCount(userId: string | number) {
    const data = await unwrap<number | { count?: number | string }>(
      http.get(`/api/contact/${userId}/applyCount`),
    )
    return asCount(data)
  },
  deleteFriend(userId: string | number, receiveuserId: string | number) {
    return unwrap<boolean>(http.delete(`/api/contact/${userId}/friend/${receiveuserId}`))
  },
  blockFriend(userId: string | number, receiveuserId: string | number) {
    return unwrap<boolean>(http.post(`/api/contact/${userId}/block/${receiveuserId}`))
  },
  unblockFriend(userId: string | number, receiveuserId: string | number) {
    return unwrap<boolean>(http.delete(`/api/contact/${userId}/block/${receiveuserId}`))
  },
  modifyApplicationStatus(
    userId: string | number,
    status: '1' | '2' | '3',
    receiveuserIds: string[],
  ) {
    return unwrap<unknown>(
      http.post(`/api/contact/${userId}/application/${status}`, { receiveuserIds }),
    )
  },
  getFriendDetail(userId: string | number, friendId: string | number) {
    return unwrap<FriendDetail>(http.get(`/api/contact/${userId}/friend/${friendId}`))
  },
}
