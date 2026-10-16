package com.malyszczuk.ingredients_retriever.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TokenRequest(@NotBlank @Size(max = 128) String token) {

    // The token is a credential: never print it into a log or an error message.
    @Override
    public String toString() {
        return "TokenRequest[token=***]";
    }
}
