// FriendStatusEnum.java
package com.shanyangcode.userservice.constants;

/**
 * Friendship status enum
 *
 * Stored values:
 * - 0: an ordinary friendship
 * - 1: blocked
 * - 2: deleted
 *
 */
public enum FriendStatusEnum {


    /**
     * Status: not friends
     */
    NON_FRIEND(-1, "Not a friend"),

    /**
     * Status: an ordinary friendship
     */
    NORMAL(0, "Friend"),

    /**
     * Status: blocked
     */
    BLOCKED(1, "Blocked"),

    /**
     * Status: deleted
     */
    DELETED(2, "Deleted");

    private final int code;
    private final String description;

    FriendStatusEnum(int code, String description) {
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
    public static FriendStatusEnum fromCode(int code) {
        for (FriendStatusEnum status : FriendStatusEnum.values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid friendship status code: " + code);
    }
}