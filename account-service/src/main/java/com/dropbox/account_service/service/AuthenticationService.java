package com.dropbox.account_service.service;

import com.dropbox.account_service.domain.User;
import com.dropbox.account_service.domain.UserStatus;
import com.dropbox.account_service.dto.LoginRequest;
import com.dropbox.account_service.dto.LoginResponse;
import com.dropbox.account_service.exception.InvalidCredentialsException;
import com.dropbox.account_service.repository.UserRepository;
import com.dropbox.account_service.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
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

        String token = jwtService.generateToken(user.getId(), user.getEmail());

        return new LoginResponse(token, "Bearer", jwtService.getExpirationMs(), user.getId(), user.getEmail());
    }
}
