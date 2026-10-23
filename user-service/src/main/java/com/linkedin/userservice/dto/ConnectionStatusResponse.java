package com.linkedin.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Relationship between the caller and another user.
 * status: SELF | NONE | PENDING_SENT | PENDING_RECEIVED | CONNECTED | REJECTED
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConnectionStatusResponse {

    private String status;
    private String connectionId;
}
