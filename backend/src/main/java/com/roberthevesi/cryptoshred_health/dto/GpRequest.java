package com.roberthevesi.cryptoshred_health.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GpRequest {
    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    private String email;
    private String phoneNumber;

    @NotBlank(message = "GMC number is required")
    private String gmcNumber;

    private String specialisation;
    private String practiceName;
}
