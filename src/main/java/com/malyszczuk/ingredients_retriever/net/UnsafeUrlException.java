package com.malyszczuk.ingredients_retriever.net;

/**
 * The URL itself is not acceptable (wrong scheme, port, credentials, or it points at a private/internal address).
 * An {@link IllegalArgumentException}, so the API answers 400: it is the caller's input that is wrong.
 * Messages deliberately never include the resolved IP address, so error responses can't be used to probe the network.
 */
public class UnsafeUrlException extends IllegalArgumentException {

    public UnsafeUrlException(String message) {
        super(message);
    }
}
