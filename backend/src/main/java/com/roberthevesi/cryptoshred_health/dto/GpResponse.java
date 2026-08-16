package com.roberthevesi.cryptoshred_health.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpResponse {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String gmcNumber;
    private String specialisation;
    private String practiceName;

    @JsonProperty("isActive")
    private boolean isActive;

    private LocalDateTime createdAt;
}
