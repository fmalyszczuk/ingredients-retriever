package com.malyszczuk.ingredients_retriever.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeleteAccountRequest(@NotBlank @Size(max = 1024) String password) {

    // Never print the password into a log or an error message.
    @Override
    public String toString() {
        return "DeleteAccountRequest[password=***]";
    }
}
