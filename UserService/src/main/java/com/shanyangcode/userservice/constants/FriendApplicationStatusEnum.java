// FriendApplicationStatusEnum.java
package com.shanyangcode.userservice.constants;

/**
 * Friend request status enum
 *
 * Stored values:
 * - 0: unread (the receiver has not looked at it)
 * - 1: accepted (the receiver approved the request)
 * - 2: rejected (the receiver declined the request)
 * - 3: read (the receiver has seen it but not acted on it)
 * - 4: expired (the request timed out without being handled)
 */
public enum FriendApplicationStatusEnum {

    UNREAD(0, "Unread"),
    ACCEPTED(1, "Accepted"),
    REJECTED(2, "Rejected"),
    READ(3, "Read"),
    EXPIRED(4, "Expired");

    private final int code;
    private final String description;

    FriendApplicationStatusEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() { return code; }
    public String getDescription() { return description; }

    public static FriendApplicationStatusEnum fromCode(int code) {
        for (FriendApplicationStatusEnum status : FriendApplicationStatusEnum.values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid friend request status code: " + code);
    }
}