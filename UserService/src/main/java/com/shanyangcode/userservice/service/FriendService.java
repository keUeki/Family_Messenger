package com.shanyangcode.userservice.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.shanyangcode.userservice.model.dto.FriendDTO;
import com.shanyangcode.userservice.model.dto.ModifyFriendApplicationResponse;
import com.shanyangcode.userservice.model.entity.Friend;
import com.shanyangcode.userservice.model.entity.User;
import com.shanyangcode.userservice.model.vo.FriendDetailVO;
import com.shanyangcode.common.model.dto.PageRequest;

/**
 * 好友服务接口
 *
 * 功能说明：
 * - 管理好友关系的完整生命周期
 * - 支持添加、删除、拉黑好友等操作
 * - 提供好友列表查询和详情查询功能
 */
public interface FriendService extends IService<Friend> {

    /**
     * 根据关键字搜索用户（自动识别手机号或邮箱）
     *
     * @param userId  当前用户ID
     * @param keyword 搜索关键字（手机号或邮箱）
     * @return FriendDetailVO 对象
     */
    FriendDetailVO searchUserByKeyword(String userId, String keyword);

    /**
     * 获取好友的详细信息
     *
     * @param userId   当前用户Id
     * @param friendId 好友Id
     * @return FriendDetailVO 对象
     */
    FriendDetailVO getFriendDetails(String userId, String friendId);

    /**
     * 获取用户的好友列表
     *
     * 支持分页和关键字搜索
     *
     * @param userId      用户ID
     * @param pageRequest 分页参数
     * @param key         搜索关键字
     * @return 分页的好友DTO列表
     */
    IPage<FriendDTO> getFriends(String userId, PageRequest pageRequest, String key);

    /**
     * 删除好友
     * <p>
     * 删除双向好友关系、相关的好友申请记录、单聊会话，并清除双向好友状态缓存。
     *
     * @param userId   当前用户ID
     * @param friendId 好友ID
     * @return 是否成功
     */
    boolean deleteFriend(Long userId, Long friendId);

    /**
     * 拉黑好友
     * <p>
     * 只修改当前用户方向的关系状态，并清除双向好友状态缓存。
     *
     * @param userId   当前用户ID
     * @param friendId 好友ID
     * @return 是否成功
     */
    boolean blockFriend(Long userId, Long friendId);

    /**
     * 取消拉黑好友
     *
     * @param userId   当前用户ID
     * @param friendId 好友ID
     * @return 是否成功
     */
    boolean unblockFriend(Long userId, Long friendId);

    /**
     * 建立好友关系（好友申请通过时调用）
     * <p>
     * 创建双向好友关系、单聊会话与用户会话关系，并通过Kafka通知申请方。
     *
     * @param recipient 同意申请的一方（接收好友请求的用户）
     * @param friendId  发起申请的一方用户ID
     * @return 新建会话的信息
     */
    ModifyFriendApplicationResponse addFriend(User recipient, Long friendId);
}
