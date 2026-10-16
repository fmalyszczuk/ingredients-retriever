package com.malyszczuk.ingredients_retriever.mail;

/** Sends a plain-text email. May throw if the mail server cannot be reached; callers decide what that means. */
public interface Mailer {

    void send(String to, String subject, String body);
}
