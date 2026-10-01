package com.paytm.project.service;

import com.paytm.project.dto.AuthRequest;
import com.paytm.project.dto.AuthResponse;
import com.paytm.project.dto.RegisterRequest;
import com.paytm.project.entity.User;
import com.paytm.project.repository.UserRepository;
import com.paytm.project.security.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username already exists");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .fullName(request.getFullName())
                .role("ROLE_USER")
                .build();

        userRepository.save(user);

        String token = jwtTokenService.generateToken(user.getUsername(), Map.of("role", user.getRole()));

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenService.getExpirationSeconds())
                .username(user.getUsername())
                .message("User registered successfully")
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        String token = jwtTokenService.generateToken(user.getUsername(), Map.of("role", user.getRole()));

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
