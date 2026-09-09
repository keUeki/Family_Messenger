package com.shanyangcode.userservice.model.vo;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 用户资料响应
 */
@Data
public class UserInfoResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * 登录账号（邮箱）
     */
    private String account;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 用户头像
     */
    private String avatar;

    /**
     * 性别：0 女，1 男，2 未知
     */
    private Integer gender;

    /**
     * 个性签名
     */
    private String description;
}