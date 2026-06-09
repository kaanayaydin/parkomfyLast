package com.parkomfy.service;

import com.parkomfy.api.LoginRequest;
import com.parkomfy.api.RegisterRequest;
import com.parkomfy.api.UserDto;
import com.parkomfy.model.User;
import com.parkomfy.repository.IParkingRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class AuthService {

    private final IParkingRepository repository;

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
        if (!user.getPasswordHash().equals(hashPassword(request.getPassword()))) {
            throw new IllegalArgumentException("E-posta veya şifre hatalı");
        }
        return new UserDto(user);
    }

    public static String hashPassword(String password) {
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
}
