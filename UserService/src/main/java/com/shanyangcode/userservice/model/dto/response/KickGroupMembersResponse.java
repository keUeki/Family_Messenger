package com.shanyangcode.userservice.model.dto.response;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * 踢出群成员响应DTO
 */
@Data
public class KickGroupMembersResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 成功踢出的成员ID列表
     */
    private List<String> successIds;
}
