package com.malyszczuk.ingredients_retriever;

import com.malyszczuk.ingredients_retriever.auth.AccountService;
import com.malyszczuk.ingredients_retriever.auth.EmailAlreadyRegisteredException;
import com.malyszczuk.ingredients_retriever.domain.AppUser;
import com.malyszczuk.ingredients_retriever.repository.UserRepository;
import com.malyszczuk.ingredients_retriever.support.PostgresIntegrationTest;
import com.malyszczuk.ingredients_retriever.support.RequiresDocker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Accounts on a real PostgreSQL: what is actually stored, and what the database itself refuses. */
@RequiresDocker
class AccountIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "a very decent passphrase";

    @Autowired
    private AccountService accountService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void emptyUsers() {
        jdbc.update("DELETE FROM users");
    }

    @Test
    void register_storesAnAccount_withTheEmailNormalised_andOnlyAHashOfThePassword() {
        AppUser created = accountService.register("  Alice@Example.COM ", PASSWORD);

        String storedEmail = jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, created.getId());
        String storedHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, created.getId());

        assertEquals("alice@example.com", storedEmail);
        assertTrue(storedHash.startsWith("{bcrypt}$2"), "stored as a prefixed BCrypt hash: " + storedHash);
        assertFalse(storedHash.contains(PASSWORD));
        assertTrue(passwordEncoder.matches(PASSWORD, storedHash));
        assertFalse(passwordEncoder.matches("a different passphrase", storedHash));
        assertTrue(jdbc.queryForObject("SELECT enabled FROM users WHERE id = ?", Boolean.class, created.getId()));
    }

    @Test
    void register_neverStoresThePasswordAnywhereInTheRow() {
        AppUser created = accountService.register("bob@example.com", PASSWORD);

        String wholeRow = jdbc.queryForObject("SELECT users::text FROM users WHERE id = ?", String.class, created.getId());

        assertFalse(wholeRow.contains(PASSWORD));
    }

    @Test
    void twoRegistrationsOfTheSameHash_getDifferentHashes_becauseEachIsSalted() {
        accountService.register("one@example.com", PASSWORD);
        accountService.register("two@example.com", PASSWORD);

        List<String> hashes = jdbc.queryForList("SELECT password_hash FROM users ORDER BY id", String.class);

        assertEquals(2, hashes.size());
        assertTrue(!hashes.get(0).equals(hashes.get(1)));
    }

    @Test
    void register_refusesAnAddressThatIsTaken_whateverTheLetterCase() {
        accountService.register("carol@example.com", PASSWORD);

        assertThrows(EmailAlreadyRegisteredException.class, () -> accountService.register("carol@example.com", PASSWORD));
        assertThrows(EmailAlreadyRegisteredException.class, () -> accountService.register("CAROL@EXAMPLE.COM", PASSWORD));
        assertThrows(EmailAlreadyRegisteredException.class, () -> accountService.register(" Carol@Example.com ", PASSWORD));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM users", Integer.class));
    }

    @Test
    void theDatabaseItself_refusesTwoAccountsForOneAddress_evenIfTheApplicationWereBypassed() {
        accountService.register("dave@example.com", PASSWORD);

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO users (email, password_hash, created_at) VALUES ('DAVE@example.com', 'x', now())"));
    }

    @Test
    void findByEmail_findsTheAccountInAnyLetterCase() {
        accountService.register("erin@example.com", PASSWORD);

        assertTrue(userRepository.findByEmail("ERIN@Example.com").isPresent());
        assertTrue(userRepository.findByEmail("nobody@example.com").isEmpty());
    }

    @Test
    void simultaneousRegistrationsOfOneAddress_produceExactlyOneAccount() throws Exception {
        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<AppUser>> futures = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return accountService.register("frank@example.com", PASSWORD);
                }));
            }
            go.countDown();

            int created = 0;
            for (Future<AppUser> future : futures) {
                try {
                    future.get();
                    created++;
                } catch (ExecutionException e) {
                    assertInstanceOf(EmailAlreadyRegisteredException.class, e.getCause(),
                            "the only acceptable failure is 'already registered', got " + e.getCause());
                }
            }

            assertEquals(1, created);
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM users WHERE email = 'frank@example.com'", Integer.class));
        } finally {
            pool.shutdownNow();
        }
    }
}
