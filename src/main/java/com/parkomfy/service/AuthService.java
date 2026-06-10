package com.parkomfy.service;

import com.parkomfy.api.LoginRequest;
import com.parkomfy.api.RegisterRequest;
import com.parkomfy.api.UserDto;
import com.parkomfy.model.User;
import com.parkomfy.repository.IParkingRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuthService {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private final IParkingRepository repository;
    private final Map<String, AuthToken> tokens = new ConcurrentHashMap<>();

    public AuthService(IParkingRepository repository) {
        this.repository = repository;
    }

    public UserDto register(RegisterRequest request) {
        if (isBlank(request.getEmail()) || isBlank(request.getPassword())) {
            throw new IllegalArgumentException("E-posta ve şifre zorunludur");
        }
        if (isBlank(request.getFullName()) || isBlank(request.getLicensePlate())) {
            throw new IllegalArgumentException("Ad soyad ve plaka zorunludur");
        }
        if (request.getPassword().length() < 4) {
            throw new IllegalArgumentException("Şifre en az 4 karakter olmalı");
        }

        String email = normalizeEmail(request.getEmail());
        if (repository.getUserByEmail(email) != null) {
            throw new IllegalStateException("Bu e-posta ile kayıtlı kullanıcı zaten var");
        }

        User user = new User(
            "USR-" + System.currentTimeMillis(),
            email,
            null,
            request.getFullName().trim());
        user.setPasswordHash(hashPassword(request.getPassword()));
        user.setLicensePlate(request.getLicensePlate().trim().toUpperCase());
        user.setRole("USER");
        repository.saveUser(user);
        return new UserDto(user);
    }

    public UserDto login(LoginRequest request) {
        if (isBlank(request.getEmail()) || isBlank(request.getPassword())) {
            throw new IllegalArgumentException("E-posta ve şifre zorunludur");
        }

        String email = normalizeEmail(request.getEmail());
        User user = repository.getUserByEmail(email);
        if (user == null || user.getPasswordHash() == null) {
            throw new IllegalArgumentException("E-posta veya şifre hatalı");
        }
        if (!verifyPassword(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("E-posta veya şifre hatalı");
        }

        UserDto dto = new UserDto(user);
        String token = issueToken(user);
        dto.setToken(token);
        return dto;
    }

    public boolean validateAdminToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        AuthToken auth = tokens.get(token.trim());
        return auth != null && "ADMIN".equalsIgnoreCase(auth.role);
    }

    public static String hashPassword(String password) {
        return ENCODER.encode(password);
    }

    public static boolean verifyPassword(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null) {
            return false;
        }
        if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$")) {
            return ENCODER.matches(rawPassword, storedHash);
        }
        return storedHash.equals(legacySha256(rawPassword));
    }

    public static String defaultAdminPassword() {
        String env = System.getenv("PARKOMFY_ADMIN_PASSWORD");
        return (env != null && !env.isBlank()) ? env : "1234";
    }

    private String issueToken(User user) {
        String token = UUID.randomUUID().toString();
        tokens.put(token, new AuthToken(user.getUserId(), user.getRole()));
        return token;
    }

    private static String legacySha256(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Password hash failed", e);
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    static final class AuthToken {
        final String userId;
        final String role;

        AuthToken(String userId, String role) {
            this.userId = userId;
            this.role = role;
        }
    }
}
