package com.careerbridge.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Badge count for the notification bell. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnreadCountResponse {

    private Long userId;

    /** long, matching MongoRepository's countBy... return type -- no narrowing to int. */
    private long unreadCount;
}
