package com.malyszczuk.ingredients_retriever.dto;

/** Deliberately only the address: the answer to a sign-up must be the same whether or not the address had an account. */
public record RegisterResponse(String email) {
}
