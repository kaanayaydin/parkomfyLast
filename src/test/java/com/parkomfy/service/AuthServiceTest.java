package com.parkomfy.service;

import org.junit.Test;

import static org.junit.Assert.*;

public class AuthServiceTest {

    @Test
    public void hashPassword_producesBcryptHash() {
        String hash = AuthService.hashPassword("1234");
        assertNotNull(hash);
        assertTrue(hash.startsWith("$2"));
        assertNotEquals(hash, AuthService.hashPassword("1234"));
    }

    @Test
    public void verifyPassword_acceptsMatchingBcrypt() {
        String hash = AuthService.hashPassword("secret");
        assertTrue(AuthService.verifyPassword("secret", hash));
        assertFalse(AuthService.verifyPassword("wrong", hash));
    }

    @Test
    public void verifyPassword_supportsLegacySha256() {
        String legacy = "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4";
        assertTrue(AuthService.verifyPassword("1234", legacy));
        assertFalse(AuthService.verifyPassword("0000", legacy));
    }
}
