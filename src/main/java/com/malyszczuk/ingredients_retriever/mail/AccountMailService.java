package com.malyszczuk.ingredients_retriever.mail;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The emails the application sends, and how: after the surrounding transaction has committed (so a link never points at
 * a token that was rolled back), and on a background thread. The latter matters for security as much as for speed: the
 * answer to "forgot my password" must take the same time whether or not the address has an account, which it
 * could not if the request waited for the mail server in one case only. Failures to send are logged, never reported
 * to the caller, for the same reason.
 * <p>
 * Links point at the frontend (pantrypal.public-url), which then calls the API with the token. They are never built
 * from the request's Host header, which an attacker controls.
 */
@Slf4j
@Service
public class AccountMailService {

    private final Mailer mailer;
    private final String publicUrl;
    private final ExecutorService executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(200), runnable -> {
        Thread thread = new Thread(runnable, "mail-sender");
        thread.setDaemon(true);
        return thread;
    });

    public AccountMailService(Mailer mailer, @Value("${pantrypal.public-url}") String publicUrl) {
        this.mailer = mailer;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    public void sendVerification(String to, String token) {
        send(to, "Confirm your PantryPal email address",
                "Welcome to PantryPal!\n\nConfirm your email address by opening this link:\n\n"
                        + publicUrl + "/verify-email?token=" + token + "\n\n"
                        + "The link works once and expires in 24 hours. If you did not create an account, ignore this email.");
    }

    public void sendPasswordReset(String to, String token) {
        send(to, "Reset your PantryPal password",
                "Someone asked to reset the password of this PantryPal account. To choose a new one, open this link:\n\n"
                        + publicUrl + "/reset-password?token=" + token + "\n\n"
                        + "The link works once and expires in 1 hour. If it was not you, ignore this email: "
                        + "your password has not been changed.");
    }

    /** To an address that somebody tried to sign up with although it already has a (confirmed) account. */
    public void sendAlreadyRegistered(String to) {
        send(to, "Someone tried to sign up with your PantryPal email address",
                "This email address already has a PantryPal account, so no new one was created.\n\n"
                        + "If it was you, just log in: " + publicUrl + "/login\n"
                        + "If you forgot your password, you can reset it here: " + publicUrl + "/forgot-password\n\n"
                        + "If it was not you, you can ignore this email: nothing has changed.");
    }

    /** After a password change or reset, so that the owner finds out if it was somebody else. */
    public void sendPasswordChanged(String to) {
        send(to, "Your PantryPal password was changed",
                "The password of your PantryPal account was just changed, and you were logged out of your other devices.\n\n"
                        + "If it was you, there is nothing to do.\n"
                        + "If it was not you, reset your password right away: " + publicUrl + "/forgot-password");
    }

    private void send(String to, String subject, String body) {
        Runnable task = () -> {
            try {
                executor.execute(() -> {
                    try {
                        mailer.send(to, subject, body);
                    } catch (RuntimeException e) {
                        log.error("Could not send the email '{}': {}", subject, e.toString());
                    }
                });
            } catch (RejectedExecutionException e) {
                log.error("The mail queue is full; dropped the email '{}'", subject);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    @PreDestroy
    void stop() {
        executor.shutdown();
    }
}
