package com.dropbox.metadata_service.domain;

public enum OutboxEventStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
