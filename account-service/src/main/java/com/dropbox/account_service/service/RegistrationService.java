package com.dropbox.account_service.service;

import com.dropbox.account_service.domain.StorageQuota;
import com.dropbox.account_service.domain.User;
import com.dropbox.account_service.domain.UserStatus;
import com.dropbox.account_service.dto.RegisterRequest;
import com.dropbox.account_service.dto.RegisterResponse;
import com.dropbox.account_service.exception.EmailAlreadyInUseException;
import com.dropbox.account_service.repository.StorageQuotaRepository;
import com.dropbox.account_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserRepository userRepository;
    private final StorageQuotaRepository storageQuotaRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${storage.default-quota-bytes}")
    private long defaultQuotaBytes;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyInUseException(email);
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                .status(UserStatus.ACTIVE)
                .build();

        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyInUseException(email);
        }

        StorageQuota quota = StorageQuota.builder()
                .userId(user.getId())
                .maxBytes(defaultQuotaBytes)
                .usedBytes(0L)
                .build();
        storageQuotaRepository.save(quota);

        return new RegisterResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt());
    }
}
