package com.booking.controller;

import com.booking.dto.AuthDtos.*;
import com.booking.model.User;
import com.booking.repository.UserRepository;
import com.booking.security.JwtService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            log.warn("event=registration_failed reason=email_exists email={}", req.email());
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Email already registered");
        }

        User user = new User(req.email(), passwordEncoder.encode(req.password()), req.fullName(), "USER");
        userRepository.save(user);
        log.info("event=user_registered email={} userId={}", user.getEmail(), user.getId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        return userRepository.findByEmail(req.email())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPassword()))
                .<ResponseEntity<?>>map(u -> {
                    String token = jwtService.generateToken(u.getEmail(), u.getRole());
                    log.info("event=login_success email={} userId={}", u.getEmail(), u.getId());
                    return ResponseEntity.ok(new AuthResponse(token, u.getEmail(), u.getRole()));
                })
                .orElseGet(() -> {
                    log.warn("event=login_failed reason=bad_credentials email={}", req.email());
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password");
                });
    }
}
