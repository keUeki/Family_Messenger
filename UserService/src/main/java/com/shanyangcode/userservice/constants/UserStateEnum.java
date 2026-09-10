package com.shanyangcode.userservice.constants;

/**
 * User status enum
 *
 * Stored values:
 * - 0: active
 * - 1: banned
 * - 2: deactivated
 */
public enum UserStateEnum {

    /**
     * Status: active
     */
    NORMAL(0, "Active"),

    /**
     * Status: banned
     */
    BANNED(1, "Banned"),

    /**
     * Status: deactivated
     */
    CANCELLED(2, "Deactivated");

    private final int code;
    private final String description;

    UserStateEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Returns the enum constant for the given status code
     *
     * @param code the status code
     * @return the matching enum constant
     * @throws IllegalArgumentException if the status code is not valid
     */
    public static UserStateEnum fromCode(int code) {
        for (UserStateEnum state : UserStateEnum.values()) {
            if (state.code == code) {
                return state;
            }
        }
        throw new IllegalArgumentException("Invalid user status code: " + code);
    }
}