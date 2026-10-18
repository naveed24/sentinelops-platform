package com.sentinelops.incident;

record IncidentLifecycleEvent(IncidentSeverity severity, IncidentStatus from, IncidentStatus to) {}
