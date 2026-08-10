package com.roberthevesi.cryptoshred_health.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProofVerificationResponseDto {
    private boolean valid;
    private boolean signatureValid;
    private boolean payloadIntegrityValid;
    private boolean merkleInclusionValid;
    private String verificationMessage;
    private LocalDateTime verifiedAt;
    private String verifiedByAlgorithm;
}
