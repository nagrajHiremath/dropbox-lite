package com.dropbox.upload_service.service;

import com.dropbox.upload_service.domain.UploadSession;
import com.dropbox.upload_service.dto.UploadStatusResponse;
import com.dropbox.upload_service.exception.ResourceNotFoundException;
import com.dropbox.upload_service.repository.UploadPartRepository;
import com.dropbox.upload_service.repository.UploadSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadStatusService {

    private final UploadSessionRepository uploadSessionRepository;
    private final UploadPartRepository uploadPartRepository;

    @Transactional(readOnly = true)
    public UploadStatusResponse getStatus(UUID ownerId, UUID uploadId) {
        UploadSession session = uploadSessionRepository.findByIdAndUserId(uploadId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Upload not found"));

        List<Integer> uploadedParts = uploadPartRepository.findPartNumbersByUploadSessionId(uploadId);

        return new UploadStatusResponse(session.getId(), session.getStatus().name(), session.getTotalParts(), uploadedParts);
    }
}
