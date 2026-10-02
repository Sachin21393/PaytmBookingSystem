package com.paytm.project.service;

import com.paytm.project.dto.AuthRequest;
import com.paytm.project.dto.AuthResponse;
import com.paytm.project.dto.RegisterRequest;
import com.paytm.project.entity.User;
import com.paytm.project.repository.UserRepository;
import com.paytm.project.security.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.getUsername().trim();
        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username already exists");
        }

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .fullName(request.getFullName())
                .role("ROLE_USER")
                .build();

        userRepository.save(user);
        log.info("User registered successfully: {}", username);

        String token = jwtTokenService.generateToken(user.getUsername(), Map.of("role", user.getRole()));

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenService.getExpirationSeconds())
                .username(user.getUsername())
                .message("User registered successfully")
                .build();
    }

    @Transactional
    public AuthResponse login(AuthRequest request) {
        String username = request.getUsername().trim();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));

        boolean matches = passwordEncoder.matches(request.getPassword(), user.getPassword());
        
        // Backward compatibility: If the stored password was stored in plaintext, authenticate and upgrade to BCrypt
        if (!matches && request.getPassword().equals(user.getPassword())) {
            log.info("Upgrading legacy plaintext password for user: {}", username);
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            userRepository.save(user);
            matches = true;
        }

        if (!matches) {
            log.warn("Invalid password attempt for username: {}", username);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        String token = jwtTokenService.generateToken(user.getUsername(), Map.of("role", user.getRole()));
        log.info("User authenticated successfully: {}", username);

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenService.getExpirationSeconds())
                .username(user.getUsername())
                .message("Authentication successful")
                .build();
    }

    public AuthResponse generateTokenForSubject(String subject, Map<String, Object> claims) {
        String token = jwtTokenService.generateToken(subject, claims);
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenService.getExpirationSeconds())
                .username(subject)
                .message("Token generated successfully")
                .build();
    }
}
