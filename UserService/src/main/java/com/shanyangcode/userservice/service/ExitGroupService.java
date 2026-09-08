package com.shanyangcode.userservice.service;

import com.shanyangcode.userservice.model.dto.request.GroupExitRequestDTO;

/**
 * 退出群聊服务接口
 */
public interface ExitGroupService {

    /**
     * 退出群聊
     * <p>
     * 处理流程：
     * 1. 参数校验
     * 2. 校验用户存在且状态正常
     * 3. 校验会话存在且为群聊
     * 4. 校验用户在群内（群主不可直接退出）
     * 5. 删除用户会话关系并通知剩余成员
     *
     * @param request 退出群聊请求
     * @return 是否成功
     */
    boolean exitGroup(GroupExitRequestDTO request);
}
