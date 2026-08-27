package com.roberthevesi.cryptoshred_health.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class PatientRequest {
    private String patientId;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    private String dateOfBirth;
    private String gender;
    private String email;
    private String phoneNumber;
    private String address;
    private String nhsNumber;
    private String bloodType;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String emergencyContactRelationship;
    private String insuranceProvider;
    private String insurancePolicyNumber;
    private String insuranceGroupNumber;
    private UUID gpId;
}
