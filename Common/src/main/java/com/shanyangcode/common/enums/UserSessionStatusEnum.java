package com.shanyangcode.common.enums;

/**
 * User-session status enum
 * <p>
 * Defines the values of the status column in the user_session table
 * so that every service checks user-session status the same way.
 * <p>
 * Stored values:
 * - 0: active
 * - 1: deleted
 */
public enum UserSessionStatusEnum {

    /**
     * Status: active
     */
    NORMAL(0, "Active"),

    /**
     * Status: deleted
     */
    DELETED(1, "Deleted");

    private final int code;
    private final String description;

    UserSessionStatusEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

}