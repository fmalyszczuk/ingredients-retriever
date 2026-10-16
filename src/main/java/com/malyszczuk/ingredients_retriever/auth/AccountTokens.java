package com.malyszczuk.ingredients_retriever.auth;

import com.malyszczuk.ingredients_retriever.domain.AccountToken;
import com.malyszczuk.ingredients_retriever.domain.TokenType;
import com.malyszczuk.ingredients_retriever.repository.AccountTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * One-time tokens that are sent by email. A token is 256 random bits, so it cannot be guessed; the database only keeps
 * its SHA-256 hash, so it cannot be read back from a copy of the database either. Each token works once, until it
 * expires, and issuing a new one kills the user's earlier unused ones of the same kind.
 */
@Service
@RequiredArgsConstructor
public class AccountTokens {

    /** Longest token the API will even look at. A real one is 43 characters. */
    public static final int MAX_LENGTH = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountTokenRepository repository;

    /** Creates a token for the user and returns it. This is the only time it exists in readable form. */
    @Transactional
    public String issue(Long userId, TokenType type, Duration lifetime) {
        Instant now = Instant.now();
        repository.deleteExpired(now);
        repository.invalidateUnused(userId, type, now);

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(AccountToken.builder()
                .userId(userId)
                .type(type)
                .tokenHash(hash(token))
                .createdAt(now)
                .expiresAt(now.plus(lifetime))
                .build());
        return token;
    }

    /** The live token of that kind this string stands for, without using it up. */
    @Transactional(readOnly = true)
    public Optional<AccountToken> find(String token, TokenType type) {
        if (token == null || token.isEmpty() || token.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        return repository.findByTokenHash(hash(token))
                .filter(found -> found.getType() == type)
                .filter(found -> found.getUsedAt() == null)
                .filter(found -> found.getExpiresAt().isAfter(Instant.now()));
    }

    /** Uses the token up. Returns the user it belongs to, or empty if it is not (or no longer) valid. */
    @Transactional
    public Optional<Long> consume(String token, TokenType type) {
        return find(token, type)
                .filter(found -> repository.markUsed(found.getId(), Instant.now()) == 1)
                .map(AccountToken::getUserId);
    }

    @Transactional
    public void invalidateAll(Long userId, TokenType type) {
        repository.invalidateUnused(userId, type, Instant.now());
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
