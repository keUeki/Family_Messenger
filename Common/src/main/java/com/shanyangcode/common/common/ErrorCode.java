package com.shanyangcode.common.common;

public enum ErrorCode {
    // ============ Generic error codes (40xxx) ============
    PARAMS_ERROR(40000, "Invalid request parameter"),
    NOT_LOGIN_ERROR(40100, "Not logged in"),
    NO_AUTH_ERROR(40101, "Permission denied"),
    TOKEN_MISSING(40102, "Missing token"),
    TOKEN_EXPIRED(40103, "Token has expired"),
    TOKEN_INVALID(40104, "Invalid token"),
    TOKEN_MISMATCH(40105, "Token does not match the user"),
    FORBIDDEN_ERROR(40300, "Access forbidden"),
    NOT_FOUND_ERROR(40400, "Requested data does not exist"),
    SENSITIVE_WORD_ERROR(40003, "Contains a sensitive word; request rejected"),

    // ============ System error codes (50xxx) ============
    SYSTEM_ERROR(50000, "Internal system error"),
    OPERATION_ERROR(50001, "Operation failed"),
    INVALID_PARAMETER_ERROR(50003, "Parameter validation failed"),
    PLEASE_LOGIN(50004, "Please log in first"),
    SAME_LOGIN_CONFLICT(50005, "This account is already logged in elsewhere"),
    SYSTEM_BUSY(50008, "System is busy, please try again later"),

    // ============ User error codes (70xxx) ============
    PHONE_EMAIL_ERROR(70000, "Invalid phone number or email format"),
    USER_ALREADY_EXISTS(70001, "User already exists"),
    USER_NOT_EXISTS(70002, "User does not exist"),
    REGISTER_ERROR(70003, "Registration failed"),
    LOGIN_ERROR_CODE(70004, "Incorrect verification code"),
    LOGIN_ERROR(70005, "Login failed: incorrect username or password"),
    LoginPasswordError(70006, "The two passwords do not match"),

    // ============ WebSocket parameter error codes (90xxx) ============
    SIGNAL_TYPE_ERROR(90000, "A one-to-one message must specify a receiver"),
    GROUP_TYPE_ERROR(90001, "A group message must not specify a receiver"),
    INVALID_TOKEN(90003, "Invalid token, please log in again"),
    USER_EMAIL_LIST_EMPTY(90004, "User email list is empty; check that UserService is healthy or that any user has registered");

    /**
     * Status code
     */
    private final int code;

    /**
     * Message
     */
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

}