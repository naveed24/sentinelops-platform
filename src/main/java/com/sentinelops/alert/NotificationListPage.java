package com.sentinelops.alert;

import java.util.List;

public record NotificationListPage(List<NotificationListItem> items, Long nextBeforeId) {
}
