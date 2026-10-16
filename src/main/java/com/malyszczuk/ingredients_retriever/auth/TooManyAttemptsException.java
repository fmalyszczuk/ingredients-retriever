package com.malyszczuk.ingredients_retriever.auth;

import java.time.Duration;

/** Too many login or registration attempts; the caller may try again after {@link #retryAfterSeconds()}. */
public class TooManyAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyAttemptsException(Duration retryAfter) {
        super("Too many attempts. Try again later.");
        this.retryAfterSeconds = Math.max(1, retryAfter.plusMillis(999).toSeconds());
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
