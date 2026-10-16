package com.malyszczuk.ingredients_retriever.auth;

/**
 * Login failed. Deliberately says nothing about why (unknown email, wrong password, disabled account): the answer
 * must be the same in every case, or the login form can be used to find out which addresses have an account.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
