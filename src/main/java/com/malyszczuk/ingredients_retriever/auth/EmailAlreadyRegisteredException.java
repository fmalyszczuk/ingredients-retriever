package com.malyszczuk.ingredients_retriever.auth;

/** An account with this email address already exists (answered as 409). */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("An account with this email address already exists");
    }
}
