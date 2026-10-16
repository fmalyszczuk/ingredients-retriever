package com.malyszczuk.ingredients_retriever.auth;

/**
 * An emailed link that is wrong, expired or already used. One answer for all three, so a token can't be probed. An
 * {@link IllegalArgumentException}, so the API answers 400.
 */
public class InvalidTokenException extends IllegalArgumentException {

    public InvalidTokenException() {
        super("The link is invalid or has expired");
    }
}
