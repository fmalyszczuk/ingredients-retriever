package com.malyszczuk.ingredients_retriever.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthThrottleTest {

    /** A clock the test moves by hand. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2030-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private TestClock clock;
    private AuthThrottle throttle;

    @BeforeEach
    void setUp() {
        clock = new TestClock();
        // 3 per address+account, 6 per address, 5 per account, within 15 minutes; 2 sign-ups per hour
        throttle = new AuthThrottle(3, 6, 5, Duration.ofMinutes(15), 2, Duration.ofHours(1), 4, 2, Duration.ofHours(1), clock);
    }

    private void fail(String address, String account, int times) {
        for (int i = 0; i < times; i++) {
            throttle.checkLogin(address, account);
            throttle.loginFailed(address, account);
        }
    }

    @Test
    void failuresBelowTheLimit_blockNothing() {
        fail("1.1.1.1", "a@x.com", 2);

        assertDoesNotThrow(() -> throttle.checkLogin("1.1.1.1", "a@x.com"));
    }

    @Test
    void theLimitingFailure_blocksThatAddressFromThatAccount_withTheTimeToWait() {
        fail("1.1.1.1", "a@x.com", 3);

        TooManyAttemptsException blocked = assertThrows(TooManyAttemptsException.class,
                () -> throttle.checkLogin("1.1.1.1", "a@x.com"));
        assertEquals(15 * 60, blocked.retryAfterSeconds());

        clock.advance(Duration.ofMinutes(5));
        assertEquals(10 * 60, assertThrows(TooManyAttemptsException.class,
                () -> throttle.checkLogin("1.1.1.1", "a@x.com")).retryAfterSeconds());
    }

    @Test
    void theBlockEnds_andTheCountStartsFromZeroAgain() {
        fail("1.1.1.1", "a@x.com", 3);
        clock.advance(Duration.ofMinutes(15));

        assertDoesNotThrow(() -> throttle.checkLogin("1.1.1.1", "a@x.com"));
        fail("1.1.1.1", "a@x.com", 2);
        assertDoesNotThrow(() -> throttle.checkLogin("1.1.1.1", "a@x.com"));
    }

    @Test
    void failuresOlderThanTheWindow_areForgotten() {
        fail("1.1.1.1", "a@x.com", 2);
        clock.advance(Duration.ofMinutes(16));
        fail("1.1.1.1", "a@x.com", 2);

        assertDoesNotThrow(() -> throttle.checkLogin("1.1.1.1", "a@x.com"));
    }

    @Test
    void blockingOneAddressAndAccount_doesNotBlockAnotherAddress_orAnotherAccount() {
        fail("1.1.1.1", "a@x.com", 3);

        assertDoesNotThrow(() -> throttle.checkLogin("2.2.2.2", "a@x.com"), "the real owner, elsewhere");
        assertDoesNotThrow(() -> throttle.checkLogin("1.1.1.1", "b@x.com"), "same address, different account");
    }

    @Test
    void oneAddressTryingManyAccounts_isBlockedEntirely() {
        for (int i = 0; i < 6; i++) {
            fail("1.1.1.1", "user" + i + "@x.com", 1);
        }

        assertThrows(TooManyAttemptsException.class, () -> throttle.checkLogin("1.1.1.1", "someone-new@x.com"));
        assertDoesNotThrow(() -> throttle.checkLogin("2.2.2.2", "someone-new@x.com"));
    }

    @Test
    void manyAddressesTryingOneAccount_areBlockedOnceTheAccountLimitIsUsedUp() {
        for (int i = 0; i < 5; i++) {
            fail("10.0.0." + i, "victim@x.com", 1);
        }

        assertThrows(TooManyAttemptsException.class, () -> throttle.checkLogin("10.0.0.99", "victim@x.com"));
        assertDoesNotThrow(() -> throttle.checkLogin("10.0.0.99", "other@x.com"));
    }

    @Test
    void aSuccessfulLogin_clearsTheAccountCounters_butNotTheAddressCounter() {
        fail("1.1.1.1", "a@x.com", 2);
        throttle.loginSucceeded("1.1.1.1", "a@x.com");
        fail("1.1.1.1", "a@x.com", 2);
        assertDoesNotThrow(() -> throttle.checkLogin("1.1.1.1", "a@x.com"), "the account counter started again");

        // the address counter still remembers all 4 failures so far; 2 more reach its limit of 6, whatever succeeded in between
        throttle.loginSucceeded("1.1.1.1", "a@x.com");
        fail("1.1.1.1", "b@x.com", 2);
        assertThrows(TooManyAttemptsException.class, () -> throttle.checkLogin("1.1.1.1", "c@x.com"));
    }

    @Test
    void theLongestWaitIsReported_whenSeveralLimitsApply() {
        fail("1.1.1.1", "a@x.com", 3);
        clock.advance(Duration.ofMinutes(10));
        for (int i = 0; i < 3; i++) {
            throttle.loginFailed("1.1.1.1", "other" + i + "@x.com");
        }

        // the address limit (6) was reached just now and lasts a full window; the earlier pair block has 5 minutes left
        assertEquals(15 * 60, assertThrows(TooManyAttemptsException.class,
                () -> throttle.checkLogin("1.1.1.1", "a@x.com")).retryAfterSeconds());
    }

    @Test
    void registrations_areLimitedPerAddress_whetherOrNotTheyWork() {
        throttle.registrationAttempt("1.1.1.1");
        throttle.registrationAttempt("1.1.1.1");

        assertThrows(TooManyAttemptsException.class, () -> throttle.registrationAttempt("1.1.1.1"));
        assertDoesNotThrow(() -> throttle.registrationAttempt("2.2.2.2"));

        clock.advance(Duration.ofHours(1));
        assertDoesNotThrow(() -> throttle.registrationAttempt("1.1.1.1"));
    }

    @Test
    void mailRequests_areLimitedPerAccount_andPerAddress_whetherOrNotTheAccountExists() {
        throttle.mailRequest("1.1.1.1", "a@x.com");
        throttle.mailRequest("2.2.2.2", "a@x.com");

        assertThrows(TooManyAttemptsException.class, () -> throttle.mailRequest("3.3.3.3", "a@x.com"), "2 per account");
        assertDoesNotThrow(() -> throttle.mailRequest("3.3.3.3", "b@x.com"));

        for (int i = 0; i < 2; i++) {
            throttle.mailRequest("9.9.9.9", "c" + i + "@x.com");
        }
        throttle.mailRequest("9.9.9.9", "c2@x.com");
        throttle.mailRequest("9.9.9.9", "c3@x.com");
        assertThrows(TooManyAttemptsException.class, () -> throttle.mailRequest("9.9.9.9", "c4@x.com"), "4 per address");

        clock.advance(Duration.ofHours(1));
        assertDoesNotThrow(() -> throttle.mailRequest("9.9.9.9", "a@x.com"));
    }

    @Test
    void memoryStaysBounded_whenFloodedWithMadeUpAccounts() {
        AttemptLimiter limiter = new AttemptLimiter(1, Duration.ofMinutes(1), 100, clock);
        for (int i = 0; i < 10_000; i++) {
            limiter.record("key" + i);
        }

        // the oldest are forgotten, the newest remembered
        assertTrue(limiter.blockedFor("key9999").isPresent());
        assertTrue(limiter.blockedFor("key0").isEmpty());
    }
}
