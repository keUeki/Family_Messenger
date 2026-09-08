package com.shanyangcode.userservice.service;

import com.shanyangcode.common.model.dto.PageRequest;
import com.shanyangcode.common.model.dto.PageResponse;
import com.shanyangcode.userservice.model.dto.response.UserGroupDTO;

/**
 * 用户群聊列表服务接口
 */
public interface UserGroupService {

    /**
     * 分页查询用户加入的群聊列表
     *
     * @param userId      用户ID
     * @param pageRequest 分页参数
     * @return 用户群聊分页结果
     */
    PageResponse<UserGroupDTO> getUserGroups(Long userId, PageRequest pageRequest);
}
