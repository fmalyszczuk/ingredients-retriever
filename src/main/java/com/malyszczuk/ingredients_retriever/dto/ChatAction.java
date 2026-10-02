package com.malyszczuk.ingredients_retriever.dto;

import java.util.Map;

/**
 * One tool the assistant used while answering. {@code status} is {@code ok}, {@code error},
 * {@code confirmation_required} (the tool was NOT run yet; the user has to confirm first) or {@code not_confirmed}
 * (the user was asked but their answer was not a clear yes, so the tool was NOT run).
 * {@code result} is included for changes, errors and confirmation requests, and omitted for read-only tools.
 */
public record ChatAction(String tool, Map<String, Object> arguments, String status, Object result) {

    public static final String OK = "ok";
    public static final String ERROR = "error";
    public static final String CONFIRMATION_REQUIRED = "confirmation_required";
    public static final String NOT_CONFIRMED = "not_confirmed";
}
