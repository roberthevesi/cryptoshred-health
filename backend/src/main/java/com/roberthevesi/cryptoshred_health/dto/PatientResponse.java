package com.roberthevesi.cryptoshred_health.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientResponse {
    private UUID id;
    private String patientId;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String gender;
    private String email;
    private String phoneNumber;
    private String address;
    private String nhsNumber;
    private GpResponse gp;

    @JsonProperty("isActive")
    private boolean isActive;

    private boolean shredded;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
