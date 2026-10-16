package com.malyszczuk.ingredients_retriever.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Slows down password guessing and mass sign-ups.
 * <p>
 * Failed logins are counted three ways, and a login is refused (429, without even looking at the password) as soon as
 * any of the three is used up:
 * <ul>
 *   <li>per address <b>and</b> account: one person guessing at one account (the usual case; it also means a user who
 *       keeps mistyping cannot lock anybody else out);</li>
 *   <li>per address: one machine trying many accounts;</li>
 *   <li>per account, from anywhere: many machines trying one account. The threshold is higher, because an attacker can
 *       use it to lock a victim out for one window; that is the price of stopping distributed guessing.</li>
 * </ul>
 * A successful login clears the address+account and account counters, but never the address one, so alternating
 * with logins to an account of the attacker's own does not reset a guessing run. Registrations are counted per address.
 */
@Component
public class AuthThrottle {

    private final AttemptLimiter perAddressAndAccount;
    private final AttemptLimiter perAddress;
    private final AttemptLimiter perAccount;
    private final AttemptLimiter registrations;
    private final AttemptLimiter mailPerAddress;
    private final AttemptLimiter mailPerAccount;

    @Autowired
    public AuthThrottle(
            @Value("${pantrypal.security.throttle.login-failures-per-address-and-account:5}") int perAddressAndAccountMax,
            @Value("${pantrypal.security.throttle.login-failures-per-address:30}") int perAddressMax,
            @Value("${pantrypal.security.throttle.login-failures-per-account:20}") int perAccountMax,
            @Value("${pantrypal.security.throttle.login-window:15m}") Duration loginWindow,
            @Value("${pantrypal.security.throttle.registrations-per-address:10}") int registrationsMax,
            @Value("${pantrypal.security.throttle.registration-window:1h}") Duration registrationWindow,
            @Value("${pantrypal.security.throttle.mail-requests-per-address:10}") int mailPerAddressMax,
            @Value("${pantrypal.security.throttle.mail-requests-per-account:3}") int mailPerAccountMax,
            @Value("${pantrypal.security.throttle.mail-window:1h}") Duration mailWindow) {
        this(perAddressAndAccountMax, perAddressMax, perAccountMax, loginWindow, registrationsMax, registrationWindow,
                mailPerAddressMax, mailPerAccountMax, mailWindow, Clock.systemUTC());
    }

    AuthThrottle(int perAddressAndAccountMax, int perAddressMax, int perAccountMax, Duration loginWindow,
                 int registrationsMax, Duration registrationWindow,
                 int mailPerAddressMax, int mailPerAccountMax, Duration mailWindow, Clock clock) {
        int maxKeys = 50_000;
        this.perAddressAndAccount = new AttemptLimiter(perAddressAndAccountMax, loginWindow, maxKeys, clock);
        this.perAddress = new AttemptLimiter(perAddressMax, loginWindow, maxKeys, clock);
        this.perAccount = new AttemptLimiter(perAccountMax, loginWindow, maxKeys, clock);
        this.registrations = new AttemptLimiter(registrationsMax, registrationWindow, maxKeys, clock);
        this.mailPerAddress = new AttemptLimiter(mailPerAddressMax, mailWindow, maxKeys, clock);
        this.mailPerAccount = new AttemptLimiter(mailPerAccountMax, mailWindow, maxKeys, clock);
    }

    /** Throws if this address may not try to log in to this account right now. {@code account} must be normalized. */
    public void checkLogin(String address, String account) {
        Optional<Duration> longest = Stream.of(
                        perAddressAndAccount.blockedFor(pair(address, account)),
                        perAddress.blockedFor(address),
                        perAccount.blockedFor(account))
                .flatMap(Optional::stream)
                .max(Duration::compareTo);
        if (longest.isPresent()) {
            throw new TooManyAttemptsException(longest.get());
        }
    }

    public void loginFailed(String address, String account) {
        perAddressAndAccount.record(pair(address, account));
        perAddress.record(address);
        perAccount.record(account);
    }

    public void loginSucceeded(String address, String account) {
        perAddressAndAccount.clear(pair(address, account));
        perAccount.clear(account);
    }

    /** Counts a sign-up attempt (successful or not) and throws if this address has made too many. */
    public void registrationAttempt(String address) {
        registrations.blockedFor(address).ifPresent(wait -> {
            throw new TooManyAttemptsException(wait);
        });
        registrations.record(address);
    }

    /**
     * Counts a request that makes the application send an email ("resend the verification", "forgot my password") and
     * throws if there have been too many from this address, or for this account. Every request counts, whether or not
     * the account exists, so being blocked tells nothing about the account. {@code account} must be normalized.
     */
    public void mailRequest(String address, String account) {
        Optional<Duration> longest = Stream.of(mailPerAddress.blockedFor(address), mailPerAccount.blockedFor(account))
                .flatMap(Optional::stream)
                .max(Duration::compareTo);
        if (longest.isPresent()) {
            throw new TooManyAttemptsException(longest.get());
        }
        mailPerAddress.record(address);
        mailPerAccount.record(account);
    }

    private static String pair(String address, String account) {
        return address + "|" + account;
    }
}
