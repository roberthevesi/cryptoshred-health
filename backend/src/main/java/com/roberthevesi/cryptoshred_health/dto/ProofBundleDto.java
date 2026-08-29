package com.roberthevesi.cryptoshred_health.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ProofBundleDto — Collection of master patient deletion proof and cascaded clinical visit proofs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProofBundleDto {
    private String patientId;
    private ProofVerificationDto masterPatientProof;
    private List<ProofVerificationDto> visitProofs;
    private int totalShreddedVisits;

    public static ProofBundleDto from(ErasureProofBundleDto bundle) {
        if (bundle == null) return null;
        return ProofBundleDto.builder()
                .patientId(bundle.getPatientId())
                .masterPatientProof(ProofVerificationDto.from(bundle.getMasterPatientProof()))
                .visitProofs(bundle.getVisitProofs() != null
                        ? bundle.getVisitProofs().stream().map(ProofVerificationDto::from).collect(Collectors.toList())
                        : List.of())
                .totalShreddedVisits(bundle.getTotalShreddedVisits())
                .build();
    }
}
