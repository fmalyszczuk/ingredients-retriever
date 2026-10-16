package com.malyszczuk.ingredients_retriever.mail;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountMailServiceTest {

    private record Mail(String to, String subject, String body) {
    }

    private final BlockingQueue<Mail> sent = new LinkedBlockingQueue<>();

    private AccountMailService service(String publicUrl) {
        return new AccountMailService((to, subject, body) -> sent.add(new Mail(to, subject, body)), publicUrl);
    }

    @Test
    void theVerificationEmail_linksToTheFrontend_withTheToken() throws Exception {
        service("https://pantrypal.example.com/").sendVerification("me@example.com", "tok-123");

        Mail mail = sent.poll(5, TimeUnit.SECONDS);
        assertNotNull(mail);
        assertEquals("me@example.com", mail.to());
        assertTrue(mail.body().contains("https://pantrypal.example.com/verify-email?token=tok-123"), mail.body());
    }

    @Test
    void theResetEmail_linksToTheFrontend_andNeverContainsAPassword() throws Exception {
        service("http://localhost:5173").sendPasswordReset("me@example.com", "tok-456");

        Mail mail = sent.poll(5, TimeUnit.SECONDS);
        assertNotNull(mail);
        assertTrue(mail.body().contains("http://localhost:5173/reset-password?token=tok-456"), mail.body());
        assertTrue(mail.body().contains("1 hour"));
    }

    @Test
    void aMailServerThatFails_isNotTheCallersProblem() throws Exception {
        AccountMailService failing = new AccountMailService((to, subject, body) -> {
            throw new IllegalStateException("smtp is down");
        }, "http://localhost:5173");

        failing.sendVerification("me@example.com", "tok"); // must not throw

        assertNull(sent.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    void insideATransaction_theEmailWaitsForTheCommit() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service("http://localhost:5173").sendVerification("me@example.com", "tok");

            assertNull(sent.poll(300, TimeUnit.MILLISECONDS), "nothing may be sent before the commit");
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            assertNotNull(sent.poll(5, TimeUnit.SECONDS));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void aRolledBackTransaction_sendsNothing() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service("http://localhost:5173").sendVerification("me@example.com", "tok");
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertNull(sent.poll(300, TimeUnit.MILLISECONDS));
    }
}
