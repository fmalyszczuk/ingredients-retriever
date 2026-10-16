package com.malyszczuk.ingredients_retriever.auth;

/**
 * The password given to confirm a sensitive action (changing the password, deleting the account) is wrong. Answers
 * 400, not 401: the user IS logged in, and the frontend treats every 401 as "session over, show the login page".
 */
public class WrongPasswordException extends RuntimeException {

    public WrongPasswordException() {
        super("The password is incorrect");
    }
}
