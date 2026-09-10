package com.shanyangcode.common.exception;


import com.shanyangcode.common.common.ErrorCode;

/**
 * Exception-throwing helper
 */
public class ThrowUtils {

    /**
     * Throws when the condition holds
     *
     * @param condition the condition; an exception is thrown when it is {@code true}
     * @param runtimeException the runtime exception instance to throw
     */
    public static void throwIf(boolean condition, RuntimeException runtimeException) {
        if (condition) {
            throw runtimeException;
        }
    }

    /**
     * Throws when the condition holds
     *
     * @param condition the condition; an exception is thrown when it is {@code true}
     * @param errorCode business error code used to build the {@link BusinessException}
     */
    public static void throwIf(boolean condition, ErrorCode errorCode) {
        throwIf(condition, new BusinessException(errorCode));
    }

    /**
     * Throws when the condition holds
     *
     * @param condition the condition; an exception is thrown when it is {@code true}
     * @param errorCode business error code
     * @param message custom exception message
     */
    public static void throwIf(boolean condition, ErrorCode errorCode, String message) {
        throwIf(condition, new BusinessException(errorCode, message));
    }
}

