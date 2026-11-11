package com.example.personal_blog.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class HashingTests {
    @Test
    void sha256MatchesKnownVectorsAndKeepsAccountTokenHashesCompatible() {
        assertThat(Hashing.sha256Hex(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        String expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        assertThat(Hashing.sha256Hex("abc")).isEqualTo(expected);
        assertThat(AccountSecurityService.hash("abc")).isEqualTo(expected);
    }
}
