package com.shanyangcode.common.utils;

import com.shanyangcode.common.constant.SnowflakeConstant;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;

/**
 * Snowflake ID helper
 *
 * @author shanyang
 */
public class SnowflakeUtil {

    private static final Snowflake SNOWFLAKE = IdUtil.getSnowflake(
            SnowflakeConstant.WORKER_ID,
            SnowflakeConstant.DATA_CENTER_ID
    );

    /**
     * Generates a snowflake ID
     *
     * @return the snowflake ID
     */
    public static long nextId() {
        return SNOWFLAKE.nextId();
    }
}