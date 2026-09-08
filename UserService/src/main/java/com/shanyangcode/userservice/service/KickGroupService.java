package com.shanyangcode.userservice.service;

import com.shanyangcode.userservice.model.dto.request.KickGroupMembersRequest;
import com.shanyangcode.userservice.model.dto.response.KickGroupMembersResponse;

/**
 * 踢出群成员服务接口
 */
public interface KickGroupService {

    /**
     * 踢出群成员
     * <p>
     * 处理流程：
     * 1. 参数校验
     * 2. 校验会话存在且为群聊
     * 3. 校验操作者为群主或管理员
     * 4. 逐个校验并踢出成员（群主不可被踢，管理员只能踢普通成员）
     * 5. 推送踢出通知给群内所有成员及被踢出者
     *
     * @param request 踢出请求
     * @return 踢出结果（成功踢出的成员ID列表）
     */
    KickGroupMembersResponse kickGroupMembers(KickGroupMembersRequest request);
}
