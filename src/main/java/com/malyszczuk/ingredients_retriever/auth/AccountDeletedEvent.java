package com.malyszczuk.ingredients_retriever.auth;

/** Published once an account and all of its data are gone, so that whatever else remembers the user can forget them. */
public record AccountDeletedEvent(Long userId, String email) {
}
