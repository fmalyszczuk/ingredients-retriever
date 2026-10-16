package com.malyszczuk.ingredients_retriever.net;

/** A safe URL could not be fetched: unreachable, an HTTP error, not a web page, too large, too slow, too many redirects. */
public class UrlFetchException extends RuntimeException {

    public UrlFetchException(String message) {
        super(message);
    }

    public UrlFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
