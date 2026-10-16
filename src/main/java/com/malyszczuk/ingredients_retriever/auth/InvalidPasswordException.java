package com.malyszczuk.ingredients_retriever.auth;

/** The chosen password is not acceptable. An {@link IllegalArgumentException}, so the API answers 400. */
public class InvalidPasswordException extends IllegalArgumentException {

    public InvalidPasswordException(String message) {
        super(message);
    }
}
