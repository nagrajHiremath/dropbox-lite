package com.dropbox.account_service.service;

import com.dropbox.account_service.domain.User;
import com.dropbox.account_service.domain.UserStatus;
import com.dropbox.account_service.dto.LoginRequest;
import com.dropbox.account_service.dto.LoginResponse;
import com.dropbox.account_service.dto.RefreshTokenRequest;
import com.dropbox.account_service.exception.InvalidCredentialsException;
import com.dropbox.account_service.exception.InvalidRefreshTokenException;
import com.dropbox.account_service.repository.UserRepository;
import com.dropbox.account_service.security.JwtService;
import com.dropbox.account_service.security.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }

        return issueTokens(user);
    }

    /**
     * Rotates the given refresh token (single-use - the old one is revoked as
     * a side effect of validateAndRotate) and issues a brand new access +
     * refresh token pair, the same shape login() returns. Re-checks the
     * user's status the same way login() does - a deactivated account
     * shouldn't be able to mint a new access token via a still-valid refresh
     * token either.
     */
    @Transactional
    public LoginResponse refresh(RefreshTokenRequest request) {
        UUID userId = refreshTokenService.validateAndRotate(request.refreshToken());

        User user = userRepository.findById(userId)
                .orElseThrow(InvalidRefreshTokenException::new);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidRefreshTokenException();
        }

        return issueTokens(user);
    }

    private LoginResponse issueTokens(User user) {
        String accessToken = jwtService.generateToken(user.getId(), user.getEmail());
        String refreshToken = refreshTokenService.issue(user.getId());
        return new LoginResponse(accessToken, "Bearer", jwtService.getExpirationMs(),
                user.getId(), user.getEmail(), user.getDisplayName(), refreshToken);
    }
}
