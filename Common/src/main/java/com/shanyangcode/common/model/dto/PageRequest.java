package com.shanyangcode.common.model.dto;

import java.io.Serial;
import java.io.Serializable;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.Data;

/**
 * Shared pagination request parameters
 * <p>
 * Usage: query params ?pageNum=1&pageSize=20
 */
@Data
public class PageRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Current page number (1-based)
     */
    private Integer pageNum = 1;

    /**
     * Page size
     */
    private Integer pageSize = 20;

    public Integer getPageNum() {
        return pageNum == null ? 1 : pageNum;
    }

    public Integer getPageSize() {
        return pageSize == null ? 20 : Math.min(pageSize, 100);
    }

    /**
     * Converts to a MyBatis-Plus Page object
     */
    public <T> Page<T> toPage() {
        int num = getPageNum();
        int size = getPageSize();
        return new Page<>(num, size);
    }
}