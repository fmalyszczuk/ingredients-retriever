package com.malyszczuk.ingredients_retriever.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 1024) String password
) {

    public RegisterRequest {
        email = email == null ? null : email.strip();
    }

    // A record prints all its fields, and this one must never put a password into a log or an error message.
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=***]";
    }
}
