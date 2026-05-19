package com.dfpp.upload.auth;

import com.dfpp.common.security.JwtTokenProvider;
import com.dfpp.upload.user.UserEntity;
import com.dfpp.upload.user.UserRepository;
import com.dfpp.upload.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtTokenProvider tokenProvider;
    private final long validityMs;

    public AuthService(UserRepository users, PasswordEncoder encoder,
                       JwtTokenProvider tokenProvider,
                       @Value("${security.jwt.validity-ms}") long validityMs) {
        this.users = users;
        this.encoder = encoder;
        this.tokenProvider = tokenProvider;
        this.validityMs = validityMs;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest req) {
        if (users.existsByUsername(req.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "Username already taken");
        }
        UserEntity user = new UserEntity(
                req.username(),
                encoder.encode(req.password()),
                Set.of("ROLE_USER"));
        users.save(user);
        return issue(user);
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest req) {
        UserEntity user = users.findByUsername(req.username())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return issue(user);
    }

    private AuthDtos.AuthResponse issue(UserEntity user) {
        List<String> roles = List.copyOf(user.getRoles());
        String token = tokenProvider.generateToken(user.getId(), user.getUsername(), roles);
        return new AuthDtos.AuthResponse(token, user.getUsername(), roles, validityMs);
    }
}
