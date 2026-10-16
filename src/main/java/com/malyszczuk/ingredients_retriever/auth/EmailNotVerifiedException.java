package com.malyszczuk.ingredients_retriever.auth;

/**
 * The password was right, but the email address has not been confirmed yet. Only ever thrown after the password was
 * checked, so it tells nobody anything they could not already do (they know the password). Answers 403.
 */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Email address not verified");
    }
}
