package com.dropbox.upload_service.service;

import com.dropbox.upload_service.domain.UploadSession;

/**
 * Deterministic temporary-part object key formula, shared by UploadPartService
 * (writes parts) and UploadCompletionService (composes + cleans them up) so the
 * two can never drift apart and silently miss each other's objects.
 */
final class UploadObjectKeys {

    private UploadObjectKeys() {
    }

    static String partObjectKey(UploadSession session, int partNumber) {
        return "dropbox-files-tmp/%s/%s/part-%05d".formatted(session.getUserId(), session.getId(), partNumber);
    }
}
