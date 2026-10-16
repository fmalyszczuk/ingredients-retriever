package com.malyszczuk.ingredients_retriever.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Deliberately no @Email check: whether the address is well-formed must not decide what the caller learns. */
public record EmailRequest(@NotBlank @Size(max = 254) String email) {

    public EmailRequest {
        email = email == null ? null : email.strip();
    }
}
