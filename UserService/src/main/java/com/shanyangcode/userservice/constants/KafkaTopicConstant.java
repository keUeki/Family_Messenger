package com.shanyangcode.userservice.constants;

/**
 * Kafka topic constants
 *
 * Every Kafka topic name used by the UserService module is declared here.
 */
public class KafkaTopicConstant {

    /**
     * Topic for system notification messages
     * Purpose: publishes system notifications (new session, friend request, group invite, ...)
     * Producer: UserService
     * Consumer: RealTimeService
     */
    public static final String TOPIC_SYSTEM_NOTIFICATION = "system-notification-topic";

    /**
     * Topic for friend-request creation events
     * Purpose: emitted when a friend request is created, to register the delayed expiry task
     * Producer: UserService (ApplyFriendService)
     * Consumer: UserService (FriendRequestExpirationEnqueuer)
     */
    public static final String TOPIC_FRIEND_REQUEST_CREATION = "friend-request-creation-topic";

    /**
     * Topic for friend-request expiry events
     * Purpose: emitted when the scheduled scan finds an expired friend request
     * Producer: UserService (FriendRequestExpirationDispatcher)
     * Consumer: UserService (FriendRequestExpirationExecutor)
     */
    public static final String TOPIC_FRIEND_REQUEST_EXPIRATION = "friend-request-expiration-topic";

    /**
     * Consumer group id for system notifications
     */
    public static final String GROUP_SYSTEM_NOTIFICATION_CONSUMER = "system-notification-consumer-group";

    /**
     * Consumer group id for friend-request creation events
     */
    public static final String GROUP_FRIEND_REQUEST_ENQUEUER = "friend-request-enqueuer-group";

    /**
     * Consumer group id for friend-request expiry events
     */
    public static final String GROUP_FRIEND_REQUEST_EXECUTOR = "friend-request-executor-group";
}