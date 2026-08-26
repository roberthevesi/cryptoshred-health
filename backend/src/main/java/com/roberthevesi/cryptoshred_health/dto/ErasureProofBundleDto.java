package com.roberthevesi.cryptoshred_health.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErasureProofBundleDto {
    private String patientId;
    private VerifiableDeletionProofDto masterPatientProof;
    private List<VerifiableDeletionProofDto> visitProofs;
    private int totalShreddedVisits;
}
