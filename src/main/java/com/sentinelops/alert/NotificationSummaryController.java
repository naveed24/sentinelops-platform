package com.sentinelops.alert;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationSummaryController {
    private final NotificationSummaryService summary;
    private final NotificationListService listing;

    @GetMapping
    public NotificationListPage list(
            @org.springframework.web.bind.annotation.RequestParam(required = false) AlertNotificationStatus status,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long beforeId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "25") int limit) {
        return listing.list(status, beforeId, limit);
    }

    @GetMapping("/summary")
    public NotificationSummary getSummary() {
        return summary.snapshot();
    }
}
