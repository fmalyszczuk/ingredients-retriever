package com.malyszczuk.ingredients_retriever.repository;

import com.malyszczuk.ingredients_retriever.domain.AccountToken;
import com.malyszczuk.ingredients_retriever.domain.TokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {

    Optional<AccountToken> findByTokenHash(String tokenHash);

    /** Spends a token. Returns 1 only for the one caller that got there first, so a token works exactly once. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AccountToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
    int markUsed(@Param("id") Long id, @Param("now") Instant now);

    /** Kills the user's tokens of that type that were never used, e.g. when a new one is issued. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AccountToken t set t.usedAt = :now where t.userId = :userId and t.type = :type and t.usedAt is null")
    int invalidateUnused(@Param("userId") Long userId, @Param("type") TokenType type, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AccountToken t where t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
