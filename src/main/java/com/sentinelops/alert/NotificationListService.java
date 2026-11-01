package com.sentinelops.alert;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Read-only, bounded, stable keyset pagination over outbox metadata. */
@Service
@RequiredArgsConstructor
public class NotificationListService {
    private final AlertNotificationOutboxRepository outbox;

    @Transactional(readOnly = true)
    public NotificationListPage list(AlertNotificationStatus status, Long beforeId, int limit) {
        if (limit < 1 || limit > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 50");
        }
        if (beforeId != null && beforeId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "beforeId must be positive");
        }
        long cursor = beforeId == null ? Long.MAX_VALUE : beforeId;
        var page = PageRequest.of(0, limit + 1);
        List<NotificationListItem> rows = status == null
                ? outbox.findRecentForDashboard(cursor, page)
                : outbox.findRecentByStatusForDashboard(status, cursor, page);
        List<NotificationListItem> items = List.copyOf(
                rows.subList(0, Math.min(limit, rows.size())));
        Long next = rows.size() > limit ? items.get(items.size() - 1).id() : null;
        return new NotificationListPage(items, next);
    }
}
