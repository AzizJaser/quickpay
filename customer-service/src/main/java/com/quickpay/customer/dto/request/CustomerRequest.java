package com.quickpay.customer.dto.request;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @NotBlank
        @Size(max = 10,min = 10)
        @Pattern(regexp = "^[12][0-9]{9}$")
        String nationalId,
        @NotBlank
        @Size(max = 100)
        String customerName,
        @NotBlank
        @Size(max = 20)
        String phoneNumber,
        @Email
        @NotBlank
        @Size(max = 254)
        String email,
        @NotBlank
        @Size(max = 200)
        String password
) {
}
