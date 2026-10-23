package com.linkedin.notificationservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** type: WELCOME | CONNECTION_REQUEST | CONNECTION_ACCEPTED | POST_LIKED | POST_COMMENTED */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    private String id;
    private String type;
    private String title;
    private String message;
    /** user id (WELCOME, CONNECTION_*) or post id (POST_*) the UI links to */
    private String refId;
    private long createdAtMillis;
    private String createdAt;
    private boolean read;
}
