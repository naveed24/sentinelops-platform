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

    @GetMapping("/summary")
    public NotificationSummary getSummary() {
        return summary.snapshot();
    }
}
