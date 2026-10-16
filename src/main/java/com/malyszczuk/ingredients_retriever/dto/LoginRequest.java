package com.malyszczuk.ingredients_retriever.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(max = 1024) String password
) {

    public LoginRequest {
        email = email == null ? null : email.strip();
    }

    // Never print the password into a log or an error message.
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}
